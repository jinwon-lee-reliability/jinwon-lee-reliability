package datasource;

import com.tmax.tibero.jdbc.ext.TbXADataSource;

import javax.sql.XADataSource;

public class XADataSourceFactory {

    public static XADataSource create(){

        TbXADataSource ds =
                new TbXADataSource();

        try{

            ds.setURL(
            System.getenv()
                    .getOrDefault(
                    "DB_URL",
                    "jdbc:tibero:thin:@localhost:57100:db"
                    )
            );

        }
        catch(Exception e){

            throw new RuntimeException(e);

        }

        return ds;

    }

}