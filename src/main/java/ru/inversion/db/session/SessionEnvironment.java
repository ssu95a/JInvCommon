package ru.inversion.db.session;


import ru.inversion.utils.Tags;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Supplier;


/**
 *
 * @author ssu
 */
public class SessionEnvironment {
    
    private static SessionEnvironment instance;

    private final ConnectionProvider connectionProvider;

    private SessionEnvironment( ConnectionProvider connectionProvider ) throws SQLException {
        this.connectionProvider = connectionProvider;
    }

    /** */
    public static SessionEnvironment getInstance( ) {
        if( instance == null )
            throw new IllegalStateException( Tags.PRODUCT_LABEL + "SessionEnvironment.instance is null. Database not connected. Login is not done." );
        return instance;
    }
    
    /** */
    public ConnectionProvider getConnectionProvider() {
        return this.connectionProvider;
    }

    /** */
    public static void initialize( DataSourceCfg dataSourceCfg ) throws Exception {

        if( instance != null )
        {
            System.out.println("WARN: SessionEnvironment already has init!");
            return;
        }

        final ConnectionProvider cp = new ConnectionProvider(dataSourceCfg);

        if( "true".equalsIgnoreCase( dataSourceCfg.getProperty("check_connection") ) )
            //а точно ли логин успешный?
            try ( Connection c = cp.getConnection() ) {
            }

        instance = new SessionEnvironment( cp );
    }

    /** */
    public static void initialize( DataSource dataSource ) throws Exception {

        if( instance != null )
        {
            System.out.println("WARN: SessionEnvironment already init!");
            return;
        }

        instance = new SessionEnvironment( new ConnectionProvider( dataSource ) );
    }

    /** */
    public static void initialize( Supplier<DataSource> sup ) throws Exception {

        if( instance != null )
        {
            System.out.println("WARN: SessionEnvironment already init!");
            return;
        }

        instance = new SessionEnvironment( new ConnectionProvider( sup ) );
    }

}
