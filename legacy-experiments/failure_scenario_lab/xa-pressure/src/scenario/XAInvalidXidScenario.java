package scenario;

import core.*;
import infra.*;
import runner.*;
import com.tmax.tibero.jdbc.ext.TbXADataSource;
import com.tmax.tibero.jdbc.ext.TbXid;

import javax.sql.XAConnection;
import javax.transaction.xa.XAResource;
import java.sql.Connection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class XaCrashTest {

    private static final String USER = "test";
    private static final String PASSWORD = "test";
    private static final int SESSION_COUNT = 100;

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: XaCrashTest <url> <scenario>");
            return;
        }

        String url = args[0];
        String scenario = args[1];

        if ("306591".equals(scenario)) {
            runScenario306591(url);
        } else if ("309107".equals(scenario)) {
            runScenario309107(url);
        } else {
            System.out.println("Unknown scenario: " + scenario);
        }
    }

    // 306591 시나리오 - XID mismatch rollback
    private static void runScenario306591(String url) throws Exception {
        TbXADataSource xaDs = new TbXADataSource();
        xaDs.setURL(url);
        xaDs.setUser(USER);
        xaDs.setPassword(PASSWORD);

        XAConnection xaConn = xaDs.getXAConnection();
        Connection conn = xaConn.getConnection();
        XAResource xaRes = xaConn.getXAResource();

        TbXid xid = new TbXid(1, new byte[]{0x01}, new byte[]{0x02});
        TbXid wrongXid = new TbXid(1, new byte[]{0x01}, new byte[]{0x03});  // 일부러 다른 XID 준비

        xaRes.start(xid, XAResource.TMNOFLAGS);
        conn.createStatement().executeUpdate("INSERT INTO T VALUES (1)");
        xaRes.end(xid, XAResource.TMSUCCESS);

        System.out.println("[XA TEST] Wrong rollback 시도");
        xaRes.rollback(wrongXid);  // 다른 XID로 rollback 시도
        System.exit(0);  // 즉시 종료로 cleanup 유도
    }

    // 309107 시나리오 - 동시 end 타이밍 충돌
    private static void runScenario309107(String url) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(SESSION_COUNT);

        TbXADataSource xaDs = new TbXADataSource();
        xaDs.setURL(url);
        xaDs.setUser(USER);
        xaDs.setPassword(PASSWORD);

        for (int i = 0; i < SESSION_COUNT; i++) {
            int finalI = i;
            executor.submit(() -> {
                try {
                    XAConnection xaConn = xaDs.getXAConnection();
                    Connection conn = xaConn.getConnection();
                    XAResource xaRes = xaConn.getXAResource();

                    TbXid xid = new TbXid(1, ("GTRID-" + finalI).getBytes(), ("BQUAL-" + finalI).getBytes());

                    xaRes.start(xid, XAResource.TMNOFLAGS);
                    conn.createStatement().executeUpdate("INSERT INTO T VALUES (" + finalI + ")");
                    Thread.sleep(50);  // 타이밍 충돌 유도
                    xaRes.end(xid, XAResource.TMSUCCESS);

                    xaConn.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }

        executor.shutdownNow();
        executor.awaitTermination(1, TimeUnit.MINUTES);
        System.exit(0);  // 전체 세션 종료로 cleanup 유도
    }
}
