#!/bin/bash
#################################################################
# run_allocator_repro.sh
#
# Purpose:
# Reproduce allocator / metadata pressure patterns in a database
# using synthetic workloads only.
#
# Modes:
#   session_storm  - connection/login storm only
#   tx_pressure    - autonomous transaction pressure only
#   full_pressure  - both session storm and transaction pressure
#   stop           - stop all spawned workloads
#################################################################

set -u

# ===== Default configuration (override with environment variables) =====
DB_USER="${DB_USER:-dbuser}"
DB_PW="${DB_PW:-dbpassword}"
DB_ALIAS="${DB_ALIAS:-db}"
TBSQL="${TBSQL:-tbsql}"

MODE="${MODE:-tx_pressure}"           # session_storm | tx_pressure | full_pressure | stop
DURATION_SEC="${DURATION_SEC:-900}"   # login storm duration
PROCS="${PROCS:-20}"                  # concurrent login storm processes

TB_HOME="${TB_HOME:-/path/to/tibero}"
TB_SID="${TB_SID:-${DB_ALIAS}}"
SYSLOG="${TB_HOME}/instance/${TB_SID}/log/slog/sys.log"

LOGROOT="${LOGROOT:-./allocator_logs}"
TS="$(date +%Y%m%d_%H%M%S)"
OUTDIR="${LOGROOT}/${TS}"

die() {
  echo "ERR: $*" >&2
  exit 1
}

ensure_dir() {
  [ -d "$1" ] || mkdir -p "$1" || die "mkdir failed: $1"
}

clean_sql() {
  local f="$1"
  tr -d '\r' < "$f" > "${f}.nocrlf" && mv "${f}.nocrlf" "$f"
}

# ===== Stop mode =====
if [ "$MODE" = "stop" ]; then
  echo "== Stopping allocator reproduction workloads =="
  pkill -f "[[:space:]]@savepoint_churn\.sql" 2>/dev/null || true
  pkill -f "[[:space:]]@tx_pressure_setup\.sql" 2>/dev/null || true
  pkill -f "[[:space:]]@tx_pressure_run\.sql" 2>/dev/null || true
  pkill -f "sgastat_poller\.sql" 2>/dev/null || true
  pkill -f "connection_storm\.sh" 2>/dev/null || true
  echo "Done."
  exit 0
fi

# ===== Prepare output directory =====
ensure_dir "$OUTDIR"
cd "$OUTDIR" || die "cd failed: $OUTDIR"

echo "== OUTDIR: $(pwd) =="

# ============================================================
# 1) Savepoint churn
# ============================================================
cat > savepoint_churn.sql <<'SQL'
DECLARE
  TYPE rc IS REF CURSOR;
  c   rc;
  v   NUMBER;
  i   PLS_INTEGER := 0;
BEGIN
  LOOP
    SAVEPOINT SP_STRESS_POINT;
    OPEN c FOR 'SELECT /*allocator_stress*/ 1 FROM dual';
    FETCH c INTO v;

    IF MOD(i, 7) = 0 THEN
      ROLLBACK TO SP_STRESS_POINT;
    ELSE
      COMMIT;
    END IF;

    CLOSE c;

    IF MOD(i, 911) = 0 THEN
      BEGIN
        RAISE_APPLICATION_ERROR(-20000, 'synthetic_error');
      EXCEPTION
        WHEN OTHERS THEN NULL;
      END;
    END IF;

    i := i + 1;
  END LOOP;
END;
/
exit;
SQL
clean_sql savepoint_churn.sql

# ============================================================
# 2) Transaction pressure setup
# ============================================================
cat > tx_pressure_setup.sql <<'SQL'
BEGIN
  EXECUTE IMMEDIATE 'DROP TABLE LOCK_PAD PURGE';
EXCEPTION
  WHEN OTHERS THEN NULL;
END;
/

CREATE TABLE LOCK_PAD(
  ID  NUMBER PRIMARY KEY,
  PAD CHAR(1)
);
/

INSERT INTO LOCK_PAD VALUES (1, 'X');
COMMIT;
/

CREATE OR REPLACE PROCEDURE TX_PRESSURE_STEP IS
  PRAGMA AUTONOMOUS_TRANSACTION;
BEGIN
  UPDATE LOCK_PAD
     SET PAD = PAD
   WHERE ID = 1;

  ROLLBACK;
END;
/

CREATE OR REPLACE PROCEDURE TX_PRESSURE_RUN IS
BEGIN
  LOOP
    BEGIN
      TX_PRESSURE_STEP;
    EXCEPTION
      WHEN OTHERS THEN NULL;
    END;
  END LOOP;
END;
/
exit;
SQL
clean_sql tx_pressure_setup.sql

# ============================================================
# 3) Transaction pressure runner
# ============================================================
cat > tx_pressure_run.sql <<'SQL'
EXEC TX_PRESSURE_RUN;
exit;
SQL
clean_sql tx_pressure_run.sql

# ============================================================
# 4) SGA stat poller
# ============================================================
cat > sgastat_poller.sql <<'SQL'
DECLARE
  i PLS_INTEGER := 0;
BEGIN
  LOOP
    FOR r IN (SELECT * FROM v$sgastat WHERE ROWNUM = 1) LOOP
      NULL;
    END LOOP;

    i := i + 1;

    IF MOD(i, 100000) = 0 THEN
      COMMIT;
    END IF;
  END LOOP;
END;
/
exit;
SQL
clean_sql sgastat_poller.sql

# ============================================================
# 5) Connection storm helper
# ============================================================
cat > connection_storm.sh <<'SH'
#!/bin/bash

DB_ALIAS="$1"
DURATION_SEC="${2:-600}"
PROCS="${3:-20}"

DB_USER="${DB_USER:-dbuser}"
DB_PW="${DB_PW:-dbpassword}"
TBSQL="${TBSQL:-tbsql}"

END_TS=$(( $(date +%s) + DURATION_SEC ))

one() {
  while [ "$(date +%s)" -lt "$END_TS" ]; do
    "${TBSQL}" "${DB_USER}/${DB_PW}"@"${DB_ALIAS}" <<'SQL' >/dev/null 2>&1
DECLARE
BEGIN
  NULL;
END;
/
exit;
SQL
  done
}

k=1
while [ "$k" -le "$PROCS" ]; do
  one &
  k=$((k+1))
done

wait
SH
chmod +x connection_storm.sh

# ============================================================
# 6) Execute scenarios
# ============================================================
echo "== Starting savepoint churn on ${DB_ALIAS} =="
nohup "${TBSQL}" "${DB_USER}/${DB_PW}"@"${DB_ALIAS}" @savepoint_churn.sql > savepoint_churn.log 2>&1 &

case "$MODE" in
  session_storm)
    echo "== MODE=session_storm : start connection storm (${DURATION_SEC}s, procs=${PROCS}) =="
    nohup ./connection_storm.sh "${DB_ALIAS}" "${DURATION_SEC}" "${PROCS}" > connection_storm.log 2>&1 &
    ;;
  tx_pressure)
    echo "== MODE=tx_pressure : create transaction pressure procedures and start runner =="
    nohup "${TBSQL}" "${DB_USER}/${DB_PW}"@"${DB_ALIAS}" @tx_pressure_setup.sql > tx_pressure_setup.log 2>&1 &
    wait $! 2>/dev/null || true

    nohup "${TBSQL}" "${DB_USER}/${DB_PW}"@"${DB_ALIAS}" @tx_pressure_run.sql > tx_pressure_run.log 2>&1 &
    ;;
  full_pressure)
    echo "== MODE=full_pressure : start connection storm + transaction pressure =="
    nohup ./connection_storm.sh "${DB_ALIAS}" "${DURATION_SEC}" "${PROCS}" > connection_storm.log 2>&1 &

    nohup "${TBSQL}" "${DB_USER}/${DB_PW}"@"${DB_ALIAS}" @tx_pressure_setup.sql > tx_pressure_setup.log 2>&1 &
    wait $! 2>/dev/null || true

    nohup "${TBSQL}" "${DB_USER}/${DB_PW}"@"${DB_ALIAS}" @tx_pressure_run.sql > tx_pressure_run.log 2>&1 &
    ;;
  *)
    echo "Unknown MODE=${MODE}. Use session_storm | tx_pressure | full_pressure | stop"
    exit 1
    ;;
esac

# ============================================================
# 7) Short SGA poll burst
# ============================================================
echo "== SGASTAT poller burst (60s x2) =="
nohup "${TBSQL}" "${DB_USER}/${DB_PW}"@"${DB_ALIAS}" @sgastat_poller.sql > poll_1.log 2>&1 &
nohup "${TBSQL}" "${DB_USER}/${DB_PW}"@"${DB_ALIAS}" @sgastat_poller.sql > poll_2.log 2>&1 &
sleep 60
pkill -f "sgastat_poller\.sql" 2>/dev/null || true

echo "== Example diagnostic tail command =="
echo "grep -E \"REDZONE|Chunk Dump|session|allocator\" \"${SYSLOG}\" | tail"
echo "== Running. To stop: ./run_allocator_repro.sh MODE=stop =="