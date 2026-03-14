/*
Distributed transaction failure reproduction test.

Purpose:
Reproduce instability scenarios under:
- DB link activity
- Session termination
- Shared pool flush

Engineering study only.
Synthetic workload.
*/

import java.sql.*;
import java.util.Random;
import java.util.concurrent.*;

public class DistributedTransactionFailureTest {
    private static final String[] TIBERO6_URLS = {
        System.getenv().getOrDefault(
            "TIBERO6_NODE1",
            "jdbc:tibero:thin:@localhost:47100:test6"
        ),

        System.getenv().getOrDefault(
            "TIBERO6_NODE2",
            "jdbc:tibero:thin:@localhost:47101:test6"
        )
    };
    private static final String[] TIBERO7_URLS = {
        System.getenv().getOrDefault(
            "TIBERO7_NODE1",
            "jdbc:tibero:thin:@localhost:57100:test7"
        ),

        System.getenv().getOrDefault(
            "TIBERO7_NODE2",
            "jdbc:tibero:thin:@localhost:57101:test7"
        )
    };
    private static final String USERNAME =
    System.getenv().getOrDefault("DB_USER","test");
    private static final String PASSWORD =
    System.getenv().getOrDefault("DB_PASSWORD","test");
    private static final String SYS_USERNAME =
    System.getenv().getOrDefault("SYS_USER","sys");
    private static final String SYS_PASSWORD =
    System.getenv().getOrDefault("SYS_PASSWORD","sys");
    private static final int THREAD_COUNT =
    Integer.parseInt(
        System.getenv().getOrDefault("THREAD_COUNT","20")
    );
    private static final String TARGET6_DBLINK =
    System.getenv().getOrDefault("TARGET6_DBLINK", "TEST_LINK_6");

    private static final String TARGET7_DBLINK =
    System.getenv().getOrDefault("TARGET7_DBLINK", "TEST_LINK_7");
    private static final int ACTION_INTERVAL_MS = 10000; // 10초마다 Kill 또는 Flush 실행
    private static final int TEST_DURATION_MS = 600000;
    private static final Random RANDOM = new Random();
	
    public static void main(String[] args) {
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT + 3); // +3: SELECT & KILL SESSION 스레드 추가

        try {
            // SEQUENCE 삭제 후 재생성
            recreateSequence(getRandomTibero6URL());
            recreateSequence(getRandomTibero7URL());

            // 테이블 초기화 및 데이터 삽입
            initializeData(getRandomTibero6URL(), "XA_SOURCE_TABLE_6");
            initializeData(getRandomTibero7URL(), "XA_SOURCE_TABLE_7");
            initializeData(getRandomTibero6URL(), "XA_TARGET_TABLE_6");
            initializeData(getRandomTibero7URL(), "XA_TARGET_TABLE_7");

            Connection[] connections = new Connection[THREAD_COUNT];

            for (int i = 0; i < THREAD_COUNT; i++) {
                int finalI = i;
                executor.submit(() -> {
                    try (Connection conn = DriverManager.getConnection(
                            finalI % 2 == 0 ? getRandomTibero6URL() : getRandomTibero7URL(), USERNAME, PASSWORD);
                         Statement stmt = conn.createStatement()) {

                        connections[finalI] = conn;
                        String sourceDB = (finalI % 2 == 0) ? "SourceCluster" : "TargetCluster";
                        System.out.println("Thread " + finalI + " started on " + sourceDB);

                        for (int j = 0; j < 10; j++) {
                            if (finalI % 2 == 0) {
                                // Tibero6 → Tibero7 INSERT & UPDATE
                                stmt.executeUpdate("INSERT INTO XA_TARGET_TABLE_7@" + TARGET7_DBLINK + " (col1, col2, col3) " +
                                        "SELECT XA_TEST_SEQ.NEXTVAL, col2, col3 FROM XA_SOURCE_TABLE_6 WHERE ROWNUM <= 1000");
                                stmt.executeUpdate("UPDATE XA_TARGET_TABLE_7@" + TARGET7_DBLINK + 
                                        " SET col2 = 'Updated' WHERE col1 IN " +
                                        "(SELECT col1 FROM XA_SOURCE_TABLE_6 WHERE ROWNUM <= 500)");
                            } else {
                                // Tibero7 → Tibero6 INSERT & UPDATE
                                stmt.executeUpdate("INSERT INTO XA_TARGET_TABLE_6@" + TARGET6_DBLINK + " (col1, col2, col3) " +
                                        "SELECT XA_TEST_SEQ.NEXTVAL, col2, col3 FROM XA_SOURCE_TABLE_7 WHERE ROWNUM <= 1000");
                                stmt.executeUpdate("UPDATE XA_TARGET_TABLE_6@" + TARGET6_DBLINK + 
                                        " SET col2 = 'Updated' WHERE col1 IN " +
                                        "(SELECT col1 FROM XA_SOURCE_TABLE_7 WHERE ROWNUM <= 500)");
                            }

                            conn.commit();
                            System.out.println("Thread " + finalI + " executed INSERT/UPDATE on " + sourceDB);
                            Thread.sleep(1000);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }

            // 독립적인 SELECT 실행 (SYS 사용자로)
            executor.submit(() -> monitorMemoryUsage(getRandomTibero6URL(), "XA_SOURCE_TABLE_6"));
            executor.submit(() -> monitorMemoryUsage(getRandomTibero7URL(), "XA_SOURCE_TABLE_7"));

            // KILL 또는 FLUSH 실행
            executor.submit(() -> {
                while (true) {
                    try {
                        if (RANDOM.nextBoolean()) {
                            System.out.println("🔴 Executing: KILL SESSION");
                            forceDisconnect(USERNAME);
                        } else {
                            System.out.println("🔵 Executing: FLUSH SHARED POOL");
                            flushSharedPool();
                        }
                        Thread.sleep(ACTION_INTERVAL_MS);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            });

            // Test duration controller
            Thread.sleep(TEST_DURATION_MS);
            System.out.println("Test completed. Shutting down.");

            executor.shutdown();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String getRandomTibero6URL() {
        return TIBERO6_URLS[RANDOM.nextInt(TIBERO6_URLS.length)];
    }

    private static String getRandomTibero7URL() {
        return TIBERO7_URLS[RANDOM.nextInt(TIBERO7_URLS.length)];
    }

    private static void recreateSequence(String dbUrl) {
        try (Connection conn = DriverManager.getConnection(dbUrl, USERNAME, PASSWORD);
             Statement stmt = conn.createStatement()) {

            stmt.executeUpdate("DROP SEQUENCE XA_TEST_SEQ");
            System.out.println("Dropped SEQUENCE: XA_TEST_SEQ on " + dbUrl);

            stmt.executeUpdate("CREATE SEQUENCE XA_TEST_SEQ START WITH 1 INCREMENT BY 1");
            System.out.println("Created SEQUENCE: XA_TEST_SEQ on " + dbUrl);

        } catch (SQLException e) {
            if (!e.getMessage().contains("does not exist")) {
                e.printStackTrace();
            }
        }
    }

    private static void initializeData(String dbUrl, String tableName) {
        try (Connection conn = DriverManager.getConnection(dbUrl, USERNAME, PASSWORD);
             Statement stmt = conn.createStatement()) {

            stmt.executeUpdate("DROP TABLE " + tableName + " PURGE");
            stmt.executeUpdate("CREATE TABLE " + tableName + " (col1 NUMBER PRIMARY KEY, col2 VARCHAR2(50), col3 DATE)");

            if (tableName.contains("SOURCE")) {
                stmt.executeUpdate("INSERT INTO " + tableName + " (col1, col2, col3) " +
                        "SELECT XA_TEST_SEQ.NEXTVAL, 'Data_' || LEVEL, SYSDATE - LEVEL FROM DUAL CONNECT BY LEVEL <= 10000");
                conn.commit();
                System.out.println("Initialized data in " + tableName);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static void monitorMemoryUsage(String dbUrl, String tableName) {
        try (Connection conn = DriverManager.getConnection(dbUrl, SYS_USERNAME, SYS_PASSWORD);
             Statement stmt = conn.createStatement()) {

            while (true) {
                try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
                    if (rs.next()) {
                        int count = rs.getInt(1);
                        System.out.println("Memory Check: " + tableName + " has " + count + " rows.");
                    }
                }
                Thread.sleep(5000); // 5초마다 체크
            }
        } catch (SQLException | InterruptedException e) {
            e.printStackTrace();
        }
    }
	
	private static void flushSharedPool() {
		try (Connection conn = DriverManager.getConnection(getRandomTibero7URL(), SYS_USERNAME, SYS_PASSWORD);
			 Statement stmt = conn.createStatement()) {

			stmt.executeUpdate("ALTER SYSTEM FLUSH SHARED_POOL");
			System.out.println("Shared Pool Flushed");

		} catch (SQLException e) {
			System.err.println("Error while flushing Shared Pool:");
			e.printStackTrace();
		}
    }

    private static void forceDisconnect(String username) {
        System.out.println("Killing sessions for user: " + username);
        killSessions(getRandomTibero6URL(), username);
        killSessions(getRandomTibero7URL(), username);
    }

	private static void killSessions(String dbUrl, String username) {
    System.out.println("Attempting to kill sessions on: " + dbUrl);

    try (Connection conn = DriverManager.getConnection(dbUrl, USERNAME, PASSWORD);
         Statement stmt = conn.createStatement();
         ResultSet rs = stmt.executeQuery(
                 "SELECT SID, SERIAL# FROM V$SESSION WHERE USERNAME = '" + username + "'")) {

        boolean sessionKilled = false;

        while (rs.next()) {
            String sid = rs.getString("SID");
            String serial = rs.getString("SERIAL#");

            try (Statement killStmt = conn.createStatement()) {
                // IMMEDIATE 옵션 추가
                String killQuery = "ALTER SYSTEM KILL SESSION '" + sid + "," + serial + "'";
                System.out.println("Executing: " + killQuery);
                killStmt.executeUpdate(killQuery);
                System.out.println("Disconnected session on " + dbUrl + ": SID=" + sid + ", SERIAL#=" + serial);
                sessionKilled = true;
            } catch (SQLException e) {
                System.err.println("Failed to kill session: SID=" + sid + ", SERIAL#=" + serial);
                e.printStackTrace();
            }
        }

        if (!sessionKilled) {
            System.out.println("No active sessions found for user: " + username + " on " + dbUrl);
        }
    } catch (SQLException e) {
        System.err.println("Error while retrieving session list from " + dbUrl);
        e.printStackTrace();
    }
}

}
