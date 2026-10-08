package ru.inversion.db.session;


import ru.inversion.utils.ConnectionStringFormatEnum;
import ru.inversion.utils.S;
import ru.inversion.utils.Tags;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;


/**
 *
 * @author ssu
 */
public class ConnectionProvider implements DataSource {
// crack
//    final private DataSourceCfg dataSourceCfg;
//    final private DataSource    dataSource;

    final private Object connectionSource;


    /** */
    public ConnectionProvider( DataSourceCfg dataSourceCfg ) {
        this.connectionSource = Objects.requireNonNull( dataSourceCfg, Tags.PRODUCT_LABEL + "'dataSourceCfg' is null");
    }


    /** */
    public ConnectionProvider( Supplier<DataSource> sup ) {
        this.connectionSource = Objects.requireNonNull( sup, Tags.PRODUCT_LABEL + "'sup' is null");
    }


    /** */
    public ConnectionProvider( DataSource dataSource ) {
        this( ()->dataSource );
    }


    /** */
    public boolean isConfigMode()
    {
        return connectionSource instanceof DataSourceCfg;
    }


    /** */
    public boolean isSupplierMode()
    {
        return connectionSource instanceof Supplier;
    }


    /** */
    public DataSourceCfg getDataSourceCfg( ) {
        if( isConfigMode() )
            return (DataSourceCfg)connectionSource;
        throw new IllegalStateException( Tags.PRODUCT_LABEL + "ConnectionProvider initialized in " + connectionSource.getClass().getSimpleName() + " mode" );
    }

    /** */
    @Override
    public Connection getConnection() throws SQLException {

        if( isConfigMode() )
        {
            final DataSourceCfg dataSourceCfg = (DataSourceCfg)connectionSource;
            Properties p = dataSourceCfg.getConnectionProperties();
            Connection connection = DriverManager.getConnection( dataSourceCfg.getConnectUrl(), p );
            connection.setAutoCommit(false);

            return connection;
        }
        else
        {
            Supplier<DataSource> sup = (Supplier<DataSource>)connectionSource;
            DataSource dataSource = sup.get();
            return dataSource.getConnection();
        }
    }

    /** */
    @Override
    public Connection getConnection( String username, String password ) throws SQLException {

        final Connection connection;

        if( isConfigMode() ) {

            final DataSourceCfg dataSourceCfg = (DataSourceCfg)connectionSource;

            Properties p = dataSourceCfg.getConnectionProperties(username, password, null);
            connection   = DriverManager.getConnection(dataSourceCfg.getConnectUrl(), p);
            connection.setAutoCommit(false);
       }
        else
        {
            Supplier<DataSource> sup = (Supplier<DataSource>)connectionSource;
            DataSource dataSource = sup.get();
            connection = dataSource.getConnection(username, password);
        }
        return connection;
    }

    /** */
    public Connection getConnection(String username, String pswd, String url ) throws SQLException {

        if( url == null )
            return getConnection(username, pswd );

        if( !isConfigMode() )
            throw new SQLException( Tags.PRODUCT_LABEL + "Создание соединений в режиме 'dataSource' поддерживается только для логина и пароля. URL заменить не возможно!" );

        final DataSourceCfg dataSourceCfg = (DataSourceCfg)connectionSource;

        Properties p = dataSourceCfg.getConnectionProperties( username, pswd, url );

        if( p.containsKey("url") )
            url = p.getProperty("url");

        Connection connection = DriverManager.getConnection ( url, p );
        connection.setAutoCommit(false);

        return connection;
    }

    /** */
    public String getConnectionString( ConnectionStringFormatEnum format ) throws SQLException {

        if( !isConfigMode() )
            return S.EMPTY_STRING;

        final DataSourceCfg dataSourceCfg = (DataSourceCfg)connectionSource;

        switch( format ) {
            case SQL_PLUS:
                return String.format("%s/%s@%s", dataSourceCfg.getLogin(), dataSourceCfg.getPassword(), dataSourceCfg.getDbUrl() );
            case SQL_INFO:
            case SQL_SIMPLE:
                return String.format("%s@%s", dataSourceCfg.getLogin(), dataSourceCfg.getDbUrl() );
            case SQL_DB_ALIS:
                return dataSourceCfg.getDbUrl();
        }
        throw new SQLFeatureNotSupportedException( Tags.PRODUCT_LABEL + "Not implemented for the format + '" + format + "'" );
    }

    /** */
    public void applyDataSourceCfg( Map<String,Object> cfg )
    {
        if( isConfigMode() )
            ((DataSourceCfg)connectionSource).applyCfg(cfg);
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }
    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void setLoginTimeout( int seconds ) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }
    @Override
    public int getLoginTimeout() throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }
    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException();
    }
    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }
    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }
}
