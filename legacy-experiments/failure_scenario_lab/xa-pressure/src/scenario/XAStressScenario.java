package scenario;

import core.*;
import infra.*;
import runner.*;
import java.util.UUID;
import javax.sql.XAConnection;
import javax.sql.XADataSource;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SourceToTargetXAStressTest {

    private static final int THREAD_COUNT = 50;

    public static void main(String[] args) throws Exception {
        XADataSource sourceXA = SourceXADataSourceFactory.createXADataSource();
        XADataSource targetXA = TargetXADataSourceFactory.createXADataSource();

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);

        for (int i = 0; i < THREAD_COUNT; i++) {
            executor.submit(() -> runXATransaction(sourceXA, targetXA));
        }

        executor.shutdownNow();
    }

    private static void runXATransaction(XADataSource sourceXA, XADataSource targetXA) {
        try {
            XAConnection sourceConn = sourceXA.getXAConnection("test", "test");
            XAConnection targetConn = targetXA.getXAConnection("test", "test");

            XAResource sourceXARes = sourceConn.getXAResource();
	    sourceXARes.setTransactionTimeout(300);
            XAResource targetXARes = targetConn.getXAResource();
	    targetXARes.setTransactionTimeout(300);

            Connection sourceSQL = sourceConn.getConnection();
            Connection targetSQL = targetConn.getConnection();

            Xid xid = createXid();
            sourceXARes.start(xid, XAResource.TMNOFLAGS);
            targetXARes.start(xid, XAResource.TMNOFLAGS);

            PreparedStatement select = sourceSQL.prepareStatement("SELECT column1 FROM SOURCE_TABLE_A WHERE ROWNUM <= 5");
            ResultSet rs = select.executeQuery();

            PreparedStatement insert = targetSQL.prepareStatement("INSERT INTO PERF_TABLE_A(id,column1) VALUES (PERF_TABLE_A_SEQ.NEXTVAL, ?)");
            while (rs.next()) {
                insert.setString(1, rs.getString(1));
                insert.executeUpdate();
            }

            targetSQL.createStatement().executeUpdate("UPDATE PERF_TABLE_A SET column1 = column1 || '_STRESS' WHERE ROWNUM <= 5");

            sourceXARes.end(xid, XAResource.TMSUCCESS);

            if (new Random().nextBoolean()) {
                TiberoSessionKiller.killRandomSession(targetSQL, 1);
            }

            targetXARes.end(xid, XAResource.TMSUCCESS);

            sourceXARes.prepare(xid);
            targetXARes.prepare(xid);

            sourceXARes.commit(xid, false);
            targetXARes.commit(xid, false);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Xid createXid() {
	int formatId = (int) System.currentTimeMillis();
	byte[] gtrid = ("TIBERO-" + UUID.randomUUID()).getBytes();
    	byte[] bqual = ("BRANCH-" + new Random().nextInt(10000)).getBytes();
    	return new SimpleXid(formatId, gtrid, bqual);
    }
}
