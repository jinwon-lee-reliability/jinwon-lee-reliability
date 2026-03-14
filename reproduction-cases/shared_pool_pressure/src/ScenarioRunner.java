import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.*;

import static LogUtil.*;

public class TiberoScenarioRunner {

    private static String ipNode1, portNode1, ipNode2, portNode2, serviceName;
    private static final String DB_USER = "", DB_PASS = "";
    private static int maxParallelSessions, totalSessions;
    private static long commitDelayMillis;
    private static String resultLogFile, scenarioLogFile;

    public static void main(String[] args) {
        if (args.length < 9) {
            System.out.println("Usage: java TiberoScenarioRunner <ipNode1> <portNode1> <ipNode2> <portNode2> <serviceName> <maxParallelSessions> <totalSessions> <scenarioName> <commitDelayMillis>");
            return;
        }

        ipNode1 = args[0]; portNode1 = args[1];
        ipNode2 = args[2]; portNode2 = args[3];
        serviceName = args[4]; maxParallelSessions = Integer.parseInt(args[5]);
        totalSessions = Integer.parseInt(args[6]);
        String scenarioName = args[7];
        commitDelayMillis = Long.parseLong(args[8]);

        resultLogFile = "log/Result_summary_" + scenarioName + "_" + timestamp() + ".log";
        scenarioLogFile = "log/LogScenarioRunner_" + scenarioName + "_" + timestamp() + ".log";

        setLogLevel(LogLevel.DEBUG);  // 기본 로그 레벨 설정

        logScenario(scenarioLogFile, LogLevel.INFO, "Starting Scenario: " + scenarioName);

        try {
            setupTables();

            runRollbackScenario(1, true, true);
            runRollbackScenario(2, true, false);
            runInsertWithDelayedCommitAndSelect(3);
            runUpdateWithDelayedCommitAndSelect(4);
            runLargeJoinWithConcurrentUpdate(5);

        } catch (Exception e) {
            logException(scenarioLogFile, LogLevel.ERROR, e);
        }
    }

    private static void setupTables() throws SQLException {
        try (Connection conn = getConnection(ipNode1, portNode1);
             Statement stmt = conn.createStatement()) {

            createTableWithData(stmt, "PERF_TABLE_A", 50);
            createTableWithData(stmt, "PERF_TABLE_B", 70);
            createTableWithData(stmt, "PERF_TABLE_C", 90);
        }
    }

    private static void createTableWithData(Statement stmt, String tableName, int columnCount) throws SQLException {
        stmt.execute("DROP TABLE " + tableName + " PURGE");

        StringBuilder query = new StringBuilder("CREATE TABLE " + tableName + " (id NUMBER PRIMARY KEY");
        for (int i = 1; i <= columnCount; i++) {
            query.append(", column").append(i).append(i % 2 == 0 ? " NUMBER" : " VARCHAR2(100)");
        }
        query.append(")");
        stmt.execute(query.toString());

        stmt.execute("INSERT INTO " + tableName + " SELECT ROWNUM id, " +
                "RPAD('X', 100, 'X'), RPAD('X', 100, 'X') FROM DUAL CONNECT BY ROWNUM <= 100000");

        logScenario(scenarioLogFile, LogLevel.INFO, tableName + " created and initial data inserted.");
    }

    private static void runRollbackScenario(int scenarioNumber, boolean rollback, boolean triggerFirst) {
        String description = "Scenario " + scenarioNumber + " (" + (triggerFirst ? "Trigger First" : "Main First") + ")";
        logScenario(scenarioLogFile, LogLevel.INFO, "Running " + description);

        runParallelInsert(description, rollback, triggerFirst);
    }

    private static void runInsertWithDelayedCommitAndSelect(int scenarioNumber) {
        runParallelInsert("Scenario " + scenarioNumber, false, false);
        runParallelSelect("Scenario " + scenarioNumber);
    }

    private static void runUpdateWithDelayedCommitAndSelect(int scenarioNumber) {
        runParallelUpdate("Scenario " + scenarioNumber);
        runParallelSelect("Scenario " + scenarioNumber);
    }

    private static void runLargeJoinWithConcurrentUpdate(int scenarioNumber) {
        ExecutorService executor = Executors.newFixedThreadPool(maxParallelSessions);
        executor.submit(() -> runLargeJoinQuery("Scenario " + scenarioNumber));
        executor.submit(() -> runUpdate(ipNode1, portNode1, "Scenario " + scenarioNumber + " (Node1)"));
        executor.submit(() -> runUpdate(ipNode2, portNode2, "Scenario " + scenarioNumber + " (Node2)"));
        awaitTermination(executor);
    }

    private static void runParallelInsert(String scenario, boolean rollback, boolean triggerFirst) {
        ExecutorService executor = Executors.newFixedThreadPool(maxParallelSessions);
        for (int session = 1; session <= totalSessions; session++) {
            int start = (session - 1) * (100000 / totalSessions) + 1;
            int end = session * (100000 / totalSessions);

            executor.submit(() -> runInsert(ipNode1, portNode1, start, end, scenario + " (Node1)", rollback, triggerFirst));
            executor.submit(() -> runInsert(ipNode2, portNode2, start, end, scenario + " (Node2)", rollback, triggerFirst));
        }
        awaitTermination(executor);
    }

    private static void runParallelUpdate(String scenario) {
        ExecutorService executor = Executors.newFixedThreadPool(maxParallelSessions);
        for (int session = 1; session <= totalSessions; session++) {
            executor.submit(() -> runUpdate(ipNode1, portNode1, scenario + " (Node1)"));
            executor.submit(() -> runUpdate(ipNode2, portNode2, scenario + " (Node2)"));
        }
        awaitTermination(executor);
    }

    private static void runInsert(String ip, String port, int start, int end, String tag, boolean rollback, boolean triggerFirst) {
        try (Connection conn = getConnection(ip, port);
             PreparedStatement stmt = conn.prepareStatement("INSERT INTO PERF_TABLE_A VALUES (?, ?)")) {
            conn.setAutoCommit(false);

            for (int id = start; id <= end; id++) {
                stmt.setInt(1, id);
                String value = "Test";

                if (rollback && id == start + 100) {
                    if (triggerFirst) value = "ROLLBACK_TRIGGER";
                    else throw new SQLException("Manual rollback at " + id);
                }
                stmt.setString(2, value);
                stmt.executeUpdate();
            }

            Thread.sleep(commitDelayMillis);
            conn.commit();
        } catch (Exception e) {
            logException(scenarioLogFile, LogLevel.ERROR, e);
        }
    }

    private static void runUpdate(String ip, String port, String tag) {
        try (Connection conn = getConnection(ip, port);
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("UPDATE PERF_TABLE_A SET column1 = 'Updated' WHERE ROWNUM <= 100");
            Thread.sleep(commitDelayMillis);
            conn.commit();
        } catch (Exception e) {
            logException(scenarioLogFile, LogLevel.ERROR, e);
        }
    }

    private static void runLargeJoinQuery(String tag) {
        logScenario(scenarioLogFile, LogLevel.INFO, tag + ": Large Join Completed.");
    }

    private static void runParallelSelect(String scenario) {
        logScenario(scenarioLogFile, LogLevel.INFO, scenario + ": SELECT Completed.");
    }

    private static Connection getConnection(String ip, String port) throws SQLException {
        return DriverManager.getConnection("jdbc:tibero:thin:@" + ip + ":" + port + ":" + serviceName, DB_USER, DB_PASS);
    }

    private static void awaitTermination(ExecutorService executor) {
        executor.shutdown();
        try {
            executor.awaitTermination(1, TimeUnit.HOURS);
        } catch (InterruptedException e) {
            logException(scenarioLogFile, LogLevel.ERROR, e);
        }
    }

    private static String timestamp() {
        return new SimpleDateFormat("MMdd_HHmm").format(new Date());
    }
}
