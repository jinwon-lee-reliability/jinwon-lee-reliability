import java.sql.*;
import javax.transaction.xa.*;
import javax.sql.XAConnection;
import com.tmax.tibero.jdbc.ext.TbXADataSource;
import java.util.Random;
import java.util.concurrent.*;
import java.time.LocalDateTime;

public class DatabaseOOMFailureTest {
    private static final String[] CLUSTER_URLS = {
		System.getenv().getOrDefault(
			"DB_NODE1",
			"jdbc:tibero:thin:@localhost:57100:test"
		),

		System.getenv().getOrDefault(
			"DB_NODE2",
			"jdbc:tibero:thin:@localhost:57101:test"
		)
	};
    private static final String USERNAME =
	System.getenv().getOrDefault("APP_USER","test");

	private static final String PASSWORD =
	System.getenv().getOrDefault("APP_PASSWORD","test");

	private static final String SYS_USERNAME =
	System.getenv().getOrDefault("SYS_USER","sys");

	private static final String SYS_PASSWORD =
	System.getenv().getOrDefault("SYS_PASSWORD","sys");

	private static final String OOM_USERNAME =
	System.getenv().getOrDefault("OOM_USER","oom");

	private static final String OOM_PASSWORD =
	System.getenv().getOrDefault("OOM_PASSWORD","oom");
    private static final int THREAD_COUNT = 60;
    private static final int XA_TRANSACTION_THREADS = 30;
    private static final int TEST_DURATION_MS = 600000; // 10 minutes
    private static final int ACTION_INTERVAL_MS = 20000; // 20 seconds
    private static final Random RANDOM = new Random();

    public static void main(String[] args) {
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT + 1);

        try {
	    initializeDatabase();
            // Run XA and OOM threads
            for (int i = 0; i < THREAD_COUNT; i++) {
                int finalI = i;
                executor.submit(() -> {
                    if (finalI < XA_TRANSACTION_THREADS) {
                        runXATransaction(finalI);
                    } else {
                        runOOMScenario(finalI);
                    }
                });
            }

            // Kill only XA transaction sessions periodically
            executor.submit(() -> {
                while (true) {
                    try {
                        Thread.sleep(ACTION_INTERVAL_MS);
                        forceKillXASessions();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            });

            Thread.sleep(TEST_DURATION_MS);
            executor.shutdownNow();
            System.out.println("Test completed.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

	private static void initializeDatabase() {
		try (Connection conn = DriverManager.getConnection(getRandomClusterURL(), USERNAME, PASSWORD);
			 Statement stmt = conn.createStatement()) {

			System.out.println("[OOM] initializeDatabase connected to: " + conn.getMetaData().getURL());

			// ?? 1. Drop leftover OOM_TBL_% tables from previous runs
			try (Statement selectStmt = conn.createStatement();
				 ResultSet rs = selectStmt.executeQuery(
					"SELECT table_name FROM dba_tables " +
					"WHERE table_name LIKE 'OOM_TBL_%' " +
					"AND owner = UPPER('" + OOM_USERNAME + "')")) 
			{
				while (rs.next()) {
					String tableName = rs.getString("table_name");
					try (Statement dropStmt = conn.createStatement()) {
						dropStmt.executeUpdate("DROP TABLE " + OOM_USERNAME + "." + tableName);
						System.out.println("Dropped leftover: " + tableName);
					} catch (SQLException dropEx) {
						System.err.println("Failed to drop " + tableName + ": " + dropEx.getMessage());
					}
				}
			}

			// ?? 2. Drop and recreate XA_TARGET_TABLE
			try (Statement dropStmt = conn.createStatement()) {
				dropStmt.executeUpdate("DROP TABLE XA_TARGET_TABLE");
				System.out.println("Dropped existing XA_TARGET_TABLE");
			} catch (SQLException ignored) {
				System.out.println("XA_TARGET_TABLE did not exist. Creating new.");
			}
			stmt.executeUpdate("CREATE TABLE XA_TARGET_TABLE (col1 NUMBER PRIMARY KEY, col2 VARCHAR2(100), col3 DATE)");

			// ?? 3. Drop and recreate XA_SOURCE_TABLE
			try (Statement dropStmt = conn.createStatement()) {
				dropStmt.executeUpdate("DROP TABLE XA_SOURCE_TABLE");
				System.out.println("Dropped existing XA_SOURCE_TABLE");
			} catch (SQLException ignored) {
				System.out.println("XA_SOURCE_TABLE did not exist. Creating new.");
			}
			stmt.executeUpdate("CREATE TABLE XA_SOURCE_TABLE (col1 NUMBER PRIMARY KEY, col2 VARCHAR2(100), col3 DATE)");
			stmt.executeUpdate("INSERT INTO XA_SOURCE_TABLE SELECT LEVEL, 'Data_' || LEVEL, SYSDATE - LEVEL FROM dual CONNECT BY LEVEL <= 1000");

			System.out.println("Created and populated XA_SOURCE_TABLE");
			System.out.println("Created XA_TARGET_TABLE");
			System.out.println("Database initialized: Cleared XA_TARGET_TABLE");

			conn.commit();

		} catch (SQLException e) {
			System.err.println("Error during database initialization: " + e.getMessage());
			e.printStackTrace();
		}
	}

    private static void runOOMScenario(int threadId) {
		String connUrl = getRandomClusterURL();
		try (Connection conn = DriverManager.getConnection(connUrl, OOM_USERNAME, OOM_PASSWORD);
			 Statement stmt = conn.createStatement()) {

			System.out.println("[THREAD " + threadId + "] Connected to " + connUrl);

			while (true) {
				String uniq = threadId + "_" + System.nanoTime();

				try {
					switch (threadId % 5) {
						case 0: // DDL 폭탄 - Dictionary Cache + Library Cache
							String tableName = "OOM_TBL_" + uniq;
							stmt.executeUpdate("CREATE TABLE " + tableName + " (id NUMBER)");
							stmt.executeUpdate("COMMENT ON TABLE " + tableName + " IS 'OOM test'");
							stmt.executeQuery("SELECT COUNT(*) FROM " + tableName);
							stmt.executeUpdate("DROP TABLE " + tableName);
							break;

						case 1: // SQL 파싱 폭탄 - Library Cache
							String sql = "SELECT '" + uniq + "' FROM dual";
							stmt.executeQuery(sql);
							break;

						case 2: // PL/SQL 익명 블록 - PL/SQL Library Cache
							String plsql = "BEGIN DBMS_OUTPUT.PUT_LINE('" + uniq + "'); END;";
							stmt.execute(plsql);
							break;

						case 3: // 시퀀스 생성/삭제 - Dictionary Cache 집중 자극
							String seq = "OOM_SEQ_" + uniq;
							stmt.executeUpdate("CREATE SEQUENCE " + seq);
							stmt.executeUpdate("DROP SEQUENCE " + seq);
							break;

						case 4: // 커서 반복 - 커서 캐시 소모
							ResultSet rs = stmt.executeQuery("SELECT * FROM dual");
							while (rs.next()) {
								rs.getString(1);
							}
							rs.close();
							break;
					}

				} catch (SQLException e) {
					System.err.println("[THREAD " + threadId + "] SQL error:");
					e.printStackTrace();
				}
			}

		} catch (SQLException e) {
			System.err.println("[THREAD " + threadId + "] Connection failed:");
			e.printStackTrace();
		}
	}

    private static void runXATransaction(int threadId) {
        TbXADataSource xaDs = new TbXADataSource();
	try {
		xaDs.setURL(getRandomClusterURL());
	} catch (SQLException e) {
		System.err.println("Error setting XADataSource URL: " + e.getMessage());
		e.printStackTrace();
	}
        xaDs.setUser(USERNAME);
        xaDs.setPassword(PASSWORD);

	while (true) {
		XAConnection xaConn = null;
		Connection conn = null;
		long baseId = threadId * 1_000_000_000L + System.nanoTime() % 1_000_000_000L;
                String insertSql = "INSERT INTO XA_TARGET_TABLE (col1, col2, col3) " +
                        "SELECT " + baseId + " + rownum, col2, col3 FROM (SELECT col2, col3 FROM XA_SOURCE_TABLE SAMPLE(5)) WHERE rownum <= 500";
		String updateSql = "UPDATE XA_TARGET_TABLE SET col2 = 'Updated_' || col1 WHERE col1 BETWEEN " + baseId + " AND " + (baseId + 499);

		try {
			System.out.println("[XA] Thread " + threadId + " acquiring XA connection...");
			xaConn = xaDs.getXAConnection();
			conn = xaConn.getConnection();
			Statement stmt = conn.createStatement();

			XAResource xaRes = xaConn.getXAResource();
			Xid xid = new MyXid(100, ("gtrid" + threadId + "_" + System.nanoTime()).getBytes(),
               ("bqual" + threadId).getBytes());
			String xidStr = new String(xid.getGlobalTransactionId());
                    	String timestamp = LocalDateTime.now().toString();

			//System.out.println("[" + timestamp + "] [XA] Thread " + threadId + " START XID=" + xidStr);
			xaRes.start(xid, XAResource.TMNOFLAGS);

			//System.out.println("[" + timestamp + "] [XA] Thread " + threadId + " executing insert SQL for XID=" + xidStr);
			int inserted = stmt.executeUpdate(insertSql);
			int updated = stmt.executeUpdate(updateSql);

			//System.out.println("[" + timestamp + "] [XA] Thread " + threadId + " END XID=" + xidStr);
			xaRes.end(xid, XAResource.TMSUCCESS);

			if (threadId % 2 == 0) {
				xaRes.rollback(xid);
				System.out.println("[XA] Thread " + threadId + " rolled back XID: " + new String(xid.getGlobalTransactionId()));
			} else {
				xaRes.commit(xid, true);
				System.out.println("[XA] Thread " + threadId + " committed XID: " + new String(xid.getGlobalTransactionId()) + ", Rows: " + inserted);
			}
			System.out.flush();

			stmt.close();
			Thread.sleep(2000);
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (conn != null) try { conn.close(); } catch (Exception ignored) {}
			if (xaConn != null) try { xaConn.close(); } catch (Exception ignored) {}
		}
	}

    }

	private static String getRandomClusterURL() {
		return CLUSTER_URLS[RANDOM.nextInt(CLUSTER_URLS.length)];
	}

	private static void forceKillXASessions() {
		System.out.println("Killing XA sessions targeting XA_TARGET_TABLE");

		String[] usernames = { USERNAME, OOM_USERNAME};

		for (int i = 0; i < usernames.length; i++) {
			String usernameCondition = usernames[i];
			System.out.println("\n========== [" + usernameCondition + "] SESSION KILLING START ==========\n");

			for (String dbUrl : CLUSTER_URLS) {
				System.out.println("[SYS] connecting to: " + dbUrl);
				try (Connection conn = DriverManager.getConnection(dbUrl, SYS_USERNAME, SYS_PASSWORD);
					 Statement stmt = conn.createStatement();
					 ResultSet rs = stmt.executeQuery(
							 "SELECT SID, SERIAL# FROM V$SESSION " +
							 "WHERE USERNAME = '" + usernameCondition + "' " +
							 "AND STATUS = 'RUNNING' AND SQL_ID IS NOT NULL")
				) {
					System.out.println("[SYS] connected to: " + conn.getMetaData().getURL());

					while (rs.next()) {
						String sid = rs.getString("SID");
						String serial = rs.getString("SERIAL#");
						try (Statement killStmt = conn.createStatement()) {
							killStmt.executeUpdate("ALTER SYSTEM KILL SESSION '" + sid + "," + serial + "' IMMEDIATE");
							System.out.println("Killed XA session SID=" + sid + ", SERIAL#=" + serial);
						} catch (SQLException e) {
							System.err.println("Failed to kill XA session SID=" + sid);
							e.printStackTrace();
						}
					}

					try (Statement flushStmt = conn.createStatement()) {
						flushStmt.execute("ALTER SYSTEM FLUSH SHARED_POOL");
						System.out.println("Flushed Shared Pool");
					}

				} catch (SQLException e) {
					System.err.println("Error killing XA sessions");
					e.printStackTrace();
				}
			}

			// 두 번의 반복 사이에 텀 주기
			if (i < usernames.length - 1) {
				try {
					System.out.println("\nWaiting for 10 seconds before next USERNAME pass...\n");
					Thread.sleep(10_000); // 10초 대기
				} catch (InterruptedException e) {
					System.err.println("Sleep interrupted");
				}
			}
		}
	}

}

// Simple Xid implementation
class MyXid implements Xid {
    int formatId;
    byte[] gtrid;
    byte[] bqual;

    public MyXid(int formatId, byte[] gtrid, byte[] bqual) {
        this.formatId = formatId;
        this.gtrid = gtrid;
        this.bqual = bqual;
    }

    public int getFormatId() {
        return formatId;
    }

    public byte[] getBranchQualifier() {
        return bqual;
    }

    public byte[] getGlobalTransactionId() {
        return gtrid;
    }
}
