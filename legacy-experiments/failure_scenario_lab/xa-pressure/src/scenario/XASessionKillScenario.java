package scenario;

import core.*;
import infra.*;
import runner.*;
import java.sql.*;
import javax.sql.XAConnection;
import javax.transaction.xa.*;

import com.tmax.tibero.jdbc.ext.TbXADataSource;
import com.tmax.tibero.jdbc.ext.TbXid;

import java.util.concurrent.*;

public class XaSelfKillWithReconnectTest {

    private static final String DB_URL = "jdbc:tibero:thin:@localhost:57100:db";
    private static final String USER = "test";
    private static final String PASSWORD = "test";

    private static final int THREAD_COUNT = 50;

    public static void main(String[] args) {
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        for (int i = 0; i < THREAD_COUNT; i++) {
            final int threadIndex = i + 1;
            executor.submit(() -> {
                try {
                    runTest(threadIndex);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }
        executor.shutdownNow();
    }

    private static void runTest(int threadIndex) throws Exception {
        TbXADataSource xaDs = new TbXADataSource();
        xaDs.setURL(DB_URL);
        xaDs.setUser(USER);
        xaDs.setPassword(PASSWORD);

        XAConnection xaConn = xaDs.getXAConnection();
        XAResource xaRes = xaConn.getXAResource();
        Connection conn = xaConn.getConnection();
        conn.setAutoCommit(false);

        TbXid xid = createXid(threadIndex);
        log(threadIndex, "XID Created: " + xidToString(xid));

        xaRes.start(xid, XAResource.TMNOFLAGS);

        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("INSERT INTO PERF_TABLE_A (id, column1) VALUES (PERF_TABLE_A_SEQ.NEXTVAL, 'SELF_KILL_TEST_" + threadIndex + "')");
            log(threadIndex, "Data Inserted");
        }

        SessionInfo sessionInfo = getMySessionInfo(conn);
        log(threadIndex, "My SID = " + sessionInfo.sid + ", SERIAL# = " + sessionInfo.serial);

        // 자기 자신 Kill 스레드 기동 (End 직전 타이밍)
        startSelfKillThread(sessionInfo, threadIndex);
	
	log(threadIndex, "XA End Try (Expected to Fail if Kill Succeeded)");

        // 약간 대기 후 XA End 시도 (Kill 되면 실패)
        //Thread.sleep(20);
        try {
            xaRes.end(xid, XAResource.TMSUCCESS);
            log(threadIndex, "Unexpected: XA End Succeeded");
        } catch (Exception e) {
            log(threadIndex, "XA End Failed as expected: " + e.getMessage());
        }

        // Kill로 세션이 끊겼으므로, 새 커넥션으로 재연결 시도
        log(threadIndex, "Reconnecting...");
        xaConn = xaDs.getXAConnection();
        xaRes = xaConn.getXAResource();

        // 기존 XID로 Attach 시도
        try {
            xaRes.start(xid, XAResource.TMJOIN);
            log(threadIndex, "Re-Attached to Existing XID");
        } catch (Exception e) {
            log(threadIndex, "Re-Attach Failed: " + e.getMessage());
        }

        try {
            int prepareResult = xaRes.prepare(xid);
            log(threadIndex, "Prepare Result: " + prepareResult);
            if (prepareResult == XAResource.XA_OK) {
                xaRes.commit(xid, false);
                log(threadIndex, "Committed");
            }
        } catch (Exception e) {
            log(threadIndex, "Prepare/Commit Failed: " + e.getMessage());
        }

        xaConn.close();
    }

    private static void startSelfKillThread(SessionInfo sessionInfo, int threadIndex) {
        new Thread(() -> {
            try {
                Thread.sleep(7);  // End 직전 Kill 타이밍
                try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASSWORD);
                     Statement stmt = conn.createStatement()) {
                    String killCmd = "ALTER SYSTEM KILL SESSION '" + sessionInfo.sid + "," + sessionInfo.serial + "'";
                    stmt.execute(killCmd);
                    log(threadIndex, "[SELF KILL] Session Killed: " + killCmd);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private static SessionInfo getMySessionInfo(Connection conn) throws SQLException {
        String sql = """
            SELECT SID, SERIAL#
            FROM V$SESSION
            WHERE AUDSID = SYS_CONTEXT('USERENV', 'SESSIONID')
        """;

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return new SessionInfo(rs.getInt("SID"), rs.getInt("SERIAL#"));
            } else {
                throw new SQLException("Failed to get current session info");
            }
        }
    }

    private static TbXid createXid(int threadIndex) {
        try {
            int formatId = (int) System.currentTimeMillis();
            String gtrid = "TIBERO-SELF-KILL-" + threadIndex + "-" + System.nanoTime();
            return new TbXid(formatId, gtrid.getBytes(), new byte[0]);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create XID", e);
        }
    }

    private static String xidToString(Xid xid) {
        return String.format("FormatId=%d, Gtrid=%s, Bqual=%s",
                xid.getFormatId(),
                new String(xid.getGlobalTransactionId()),
                new String(xid.getBranchQualifier()));
    }

    private static void log(int threadIndex, String message) {
        System.out.printf("[Thread-%d][XA LOG] %s%n", threadIndex, message);
    }

    static class SessionInfo {
        int sid;
        int serial;

        SessionInfo(int sid, int serial) {
            this.sid = sid;
            this.serial = serial;
        }
    }
}
