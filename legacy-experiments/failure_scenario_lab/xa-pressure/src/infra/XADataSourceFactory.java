package infra;

import core.*;
import runner.*;
import scenario.*;
import infra.*;
import java.sql.SQLException;
import javax.sql.XADataSource;
import com.tmax.tibero.jdbc.ext.TbXADataSource;

public class XADataSourceFactory {
    public static XADataSource createXADataSource() {
        TbXADataSource xaDataSource = new TbXADataSource();
	try {
		xaDataSource.setURL("jdbc:tibero:thin:@localhost:57100:db");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to set URL for Source XA DataSource", e);
        }
        return xaDataSource;
    }
}
