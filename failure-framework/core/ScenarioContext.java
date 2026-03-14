package core;

import javax.transaction.xa.XAResource;
import java.sql.Connection;
import java.sql.Statement;

public class ScenarioContext {

    private final Connection conn;

    private final XAResource xa;

    public ScenarioContext(
            Connection conn,
            XAResource xa){

        this.conn = conn;
        this.xa = xa;

    }

    public void sql(String sql)
            throws Exception{

        try(Statement st =
                    conn.createStatement()){

            st.execute(sql);

        }

    }

    public void commit()
            throws Exception{

        conn.commit();

    }

    public void rollback()
            throws Exception{

        conn.rollback();

    }

}