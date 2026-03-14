package scenario;

import core.*;
import infra.*;
import runner.*;
import java.sql.*;
import javax.sql.XAConnection;
import javax.transaction.xa.*;
import com.tmax.tibero.jdbc.ext.*;

public class XaScenario {
    private static final String URL = "jdbc:tibero:thin:@localhost:57100:db";
    private static final String USER = "test";
    private static final String PASSWORD = "test";
    private static final int BCH_NUM = 256;

    public static void main(String[] args) throws Exception {
        XaUtil.cleanupPendingXATransactions();
        System.out.println("[INIT] Existing XA Transactions Cleaned Up");

        if (args.length == 0 || "306591".equals(args[0])) {
            runScenario306591();
        }
        if (args.length == 0 || "309107".equals(args[0])) {
            runScenario309107();
        }
    }

    private static void runScenario306591() throws Exception {
        System.out.println("[SCENARIO 306591] Start");
        TbXADataSource ds = XaUtil.createXADataSource(URL, USER, PASSWORD);
        XAConnection[] xaConns = new XAConnection[BCH_NUM];
        XAResource[] xaRes = new XAResource[BCH_NUM];
        Connection[] conns = new Connection[BCH_NUM];
        TbXid[] xids = new TbXid[BCH_NUM];

        for (int i = 0; i < BCH_NUM; i++) {
            xaConns[i] = ds.getXAConnection();
            xaRes[i] = xaConns[i].getXAResource();
            conns[i] = xaConns[i].getConnection();

            xids[i] = XaUtil.createXid();

            xaRes[i].start(xids[i], XAResource.TMNOFLAGS);
            conns[i].createStatement().execute("INSERT INTO XA_TEST_TABLE (id, column1) VALUES (XA_TEST_TABLE_SEQ.NEXTVAL, '306591_TEST')");
            if (i % 8 == 0) xaRes[i].end(xids[i], XAResource.TMSUCCESS);
        }

        System.exit(0);  // 강제 종료로 cleanup 타이밍 유발
    }

private static void runScenario309107() throws Exception {
    System.out.println("[SCENARIO 309107] Start");

    TbXADataSource ds = XaUtil.createXADataSource(URL, USER, PASSWORD);
    XAConnection xaConn = null;
    Connection conn = null;

    try {
        xaConn = ds.getXAConnection();
        conn = xaConn.getConnection();

        XAResource xaRes = xaConn.getXAResource();
        TbXid xid = XaUtil.createXid();
        TbXid wrongXid = XaUtil.createXid();

        xaRes.start(xid, XAResource.TMNOFLAGS);
        conn.createStatement().execute("INSERT INTO XA_TEST_TABLE (id, column1) VALUES (XA_TEST_TABLE_SEQ.NEXTVAL, '309107_TEST')");
        
        // 일부러 잘못된 XID로 rollback 시도
        xaRes.rollback(wrongXid);

        System.exit(0);  // 강제 종료로 cleanup 유도
    } finally {
        if (conn != null) try { conn.close(); } catch (Exception ignored) {}
        if (xaConn != null) try { xaConn.close(); } catch (Exception ignored) {}
    }
}
}
