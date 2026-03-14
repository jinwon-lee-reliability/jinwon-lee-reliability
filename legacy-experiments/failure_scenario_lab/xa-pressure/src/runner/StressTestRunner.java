package runner;

import core.*;
import infra.*;
import scenario.*;
import javax.sql.XAConnection;
import javax.sql.XADataSource;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StressTestRunner {

    private static final int THREAD_COUNT = 50;
    private static final int TOTAL_ROWS = 100000;  // 10만건
    private static final Random RANDOM = new Random();

    public static void main(String[] args) throws Exception {
        XADataSource targetXA = XADataSourceFactory.createXADataSource();

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);

        for (int i = 0; i < THREAD_COUNT; i++) {
            int scenario = (i % 5) + 1; // 1~5번 시나리오 순환
            executor.submit(() -> runScenario(targetXA, scenario));
        }

        executor.shutdownNow();
    }

    private static void runScenario(XADataSource targetXA, int scenarioNumber) {
        try {
            XAConnection targetConn = targetXA.getXAConnection("test", "test");
            XAResource targetXARes = targetConn.getXAResource();
            targetXARes.setTransactionTimeout(300);

            Connection targetSQL = targetConn.getConnection();
            Xid xid = createXid();

            targetXARes.start(xid, XAResource.TMNOFLAGS);

            switch (scenarioNumber) {
                case 1 -> runInsert(targetSQL, targetXARes, xid);
                case 2 -> runUpdate(targetSQL, targetXARes, xid);
                case 3 -> runSelect(targetSQL, targetXARes, xid);
                case 4 -> runMixed(targetSQL, targetXARes, xid);
                case 5 -> runLongTransaction(targetSQL, targetXARes, xid);
            }

        } catch (Exception e) {
            System.err.println("[XA LOG] Exception occurred in Scenario " + scenarioNumber + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void runInsert(Connection conn, XAResource xaRes, Xid xid) throws Exception {
        PreparedStatement stmt = conn.prepareStatement("INSERT INTO XA_TEST_TABLE(id, column1) VALUES (XA_TEST_TABLE_SEQ.NEXTVAL, ?)");
        for (int i = 1; i <= TOTAL_ROWS; i++) {
            stmt.setString(1, "STRESS_INSERT_" + i);
            stmt.executeUpdate();
            maybeKillSession(conn, i);
        }
        commitOrRollback(xaRes, xid);
    }

    private static void runUpdate(Connection conn, XAResource xaRes, Xid xid) throws Exception {
        PreparedStatement stmt = conn.prepareStatement("UPDATE XA_TEST_TABLE SET column1 = 'UPDATED'");
        stmt.executeUpdate();
        maybeKillSession(conn, 5000);
        commitOrRollback(xaRes, xid);
    }

    private static void runSelect(Connection conn, XAResource xaRes, Xid xid) throws Exception {
        PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM XA_TEST_TABLE");
        ResultSet rs = stmt.executeQuery();
        if (rs.next()) {
            System.out.println("[XA LOG] Total Rows: " + rs.getInt(1));
        }
        maybeKillSession(conn, 3000);
        commitOrRollback(xaRes, xid);
    }

    private static void runMixed(Connection conn, XAResource xaRes, Xid xid) throws Exception {
        runInsert(conn, xaRes, xid);
        runUpdate(conn, xaRes, xid);
        runSelect(conn, xaRes, xid);
    }

    private static void runLongTransaction(Connection conn, XAResource xaRes, Xid xid) throws Exception {
        PreparedStatement stmt = conn.prepareStatement("INSERT INTO XA_TEST_TABLE(id, column1) VALUES (XA_TEST_TABLE_SEQ.NEXTVAL, ?)");
        for (int i = 1; i <= TOTAL_ROWS; i++) {
            stmt.setString(1, "LONG_TX_" + i);
            stmt.executeUpdate();
            if (i % 20000 == 0) {
                maybeKillSession(conn, i);
                Thread.sleep(2000);  // 일부러 트랜잭션 길게 끌기
            }
        }
        commitOrRollback(xaRes, xid);
    }

    private static void commitOrRollback(XAResource xaRes, Xid xid) throws Exception {
        xaRes.end(xid, XAResource.TMSUCCESS);
        int prepareResult = xaRes.prepare(xid);
        if (prepareResult == XAResource.XA_OK) {
            xaRes.commit(xid, false);
            System.out.println("[XA LOG] Commit Completed");
        } else {
            xaRes.rollback(xid);
            System.out.println("[XA LOG] Rollback Completed");
        }
    }

    private static void maybeKillSession(Connection conn, int counter) {
	if (counter % 10000 == 0) {
        System.out.println("[XA LOG] Killing a random session after " + counter + " rows");
        try {
            TiberoSessionKiller.killRandomSession(conn, 1);  // 이 부분 try-catch 추가
        } catch (Exception e) {
            System.err.println("[XA LOG] Failed to kill session: " + e.getMessage());
            e.printStackTrace();
        }
    	}
    }

    private static Xid createXid() {
        int formatId = (int) System.currentTimeMillis();
        byte[] gtrid = ("TIBERO-" + UUID.randomUUID()).getBytes();
        byte[] bqual = ("BRANCH-" + RANDOM.nextInt(10000)).getBytes();
        return new SimpleXid(formatId, gtrid, bqual);
    }
}
