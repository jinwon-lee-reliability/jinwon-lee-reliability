package core;

import javax.sql.XAConnection;
import javax.sql.XADataSource;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
import java.sql.Connection;
import java.util.UUID;

public class FailureFramework {

    public static void run(
            XADataSource ds,
            Scenario scenario){

        try{

            XAConnection xaConn =
                    ds.getXAConnection(
                            "test",
                            "test"
                    );

            XAResource xa =
                    xaConn.getXAResource();

            Connection conn =
                    xaConn.getConnection();

            Xid xid =
                    XaIdGenerator.create();

            xa.start(
                    xid,
                    XAResource.TMNOFLAGS
            );

            ScenarioContext ctx =
                    new ScenarioContext(
                            conn,
                            xa
                    );

            scenario.run(ctx);

            xa.end(
                    xid,
                    XAResource.TMSUCCESS
            );

            xa.prepare(xid);

            xa.commit(
                    xid,
                    false
            );

        }
        catch(Exception e){

            e.printStackTrace();

        }

    }

}