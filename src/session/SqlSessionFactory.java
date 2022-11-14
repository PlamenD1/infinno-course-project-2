package session;

import config.Configuration;
import config.environment.DataSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Objects;

public class SqlSessionFactory {
    Configuration configuration;
    boolean databaseConnectionPoolConnected = false;

    public SqlSessionFactory(Configuration configuration) {
        this.configuration = configuration;
    }

    public SqlSession openSession() throws Exception {
        return new SqlSession(getConnection(), configuration.mappers);
    }

    Connection getConnection() throws Exception {
        Connection connection = null;
        DataSource ds = configuration.environment.dataSource;
        if (ds.type.equals("POOLED")) {
            if (!databaseConnectionPoolConnected)
                DatabaseConnectionPool.setDataSource(ds);

            connection = DatabaseConnectionPool.getConnectionPool().getConnection();
        } else if (ds.type.equals("UNPOOLED")) {
            Class.forName(ds.activeProperties.get("driver"));
            String url = ds.activeProperties.get("url");
            String username = ds.activeProperties.get("username");
            String password = ds.activeProperties.get("password");

            connection = DriverManager.getConnection(url, username, password);
        }

        return connection;
    }
}