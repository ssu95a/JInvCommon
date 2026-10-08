package ru.inversion.db.session.cfg_impl;

import ru.inversion.db.session.DataSourceCfg;
import ru.inversion.db.session.AbstractDataSourceCfgBuilder;
import ru.inversion.db.session.xxi.XxiConnector;
import ru.inversion.utils.S;
import ru.inversion.utils.Tags;

import java.net.PasswordAuthentication;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Properties;

public class Mdl_DataSourceCfgBuilder extends AbstractDataSourceCfgBuilder {
    /**
     */
    final private  String middleDrvServer;
    public Mdl_DataSourceCfgBuilder( String appId, String middleDrvServer ) {
        super(appId);
        this.middleDrvServer = middleDrvServer;
    }

    /** */
    private Properties initProperties( ) {

        Properties info = new Properties();

        String s = System.getenv("COMPUTERNAME"); // == null ? java.net.InetAddress.getLocalHost().getHostName() : System.getenv("COMPUTERNAME");
        if( s != null )
            info.put("v$session.terminal", s.toUpperCase() );

        s = System.getenv("USERDOMAIN") + "\\" + System.getenv("COMPUTERNAME");

        if( s != null )
            info.put("v$session.machine", s.toUpperCase() );

        info.put( "v$session.program", V$SESSION_PROGRAM );
        info.put( "v$session.module", appId );

        info.putAll( this.properties );

        return info;
    }
    /** */
    public DataSourceCfg build( ) throws Exception {

        preBuild();

        Properties p = initProperties( );

        String  connectUrl     = null;

        boolean doPseudoLogon  = usePseudoConnection;

        String  pseudoPassword = null;

        System.setProperty("ru.inversion.driver.class_name", MIDDLE_DRIVER );

        try {
            Class.forName( MIDDLE_DRIVER );
        }
        catch( Throwable th ) {
            g_logger.error( "search for JDBC driver", th);
            throw new RuntimeException( Tags.PRODUCT_LABEL + "Error init 3-level JDBC driver", th );
        }

        connectUrl = "jdbc:invo:" + middleDrvServer + "@" + dbUrl;

        Properties info = new Properties(p);
        info.put( "user",     login    );
        info.put( "password", password );
        info.put( "ru.inversion.pseudo_connection", usePseudoConnection ? "true" : false );

        try( Connection c = DriverManager.getConnection( connectUrl, info ) ){ }

        if(usePseudoConnection) {

            String rp = info.getProperty("rp");

            if( S.isNotNullOrEmpty(rp) )
            {
                pseudoPassword = password;
                password       = rp;

                doPseudoLogon  = false;
            }
        }

        if( doPseudoLogon ) {

            pseudoPassword = password;

            //this.password = XxiConnector.login( connectUrl, login, pseudoPassword, p, resultHandler );
            final PasswordAuthentication auth = XxiConnector.login( connectUrl, this.login, pseudoPassword, p, resultHandler) ;

            this.login    = auth.getUserName();
            this.password = String.valueOf( auth.getPassword() );
        }

        return new DataSourceCfg( login, password, connectUrl, dbUrl, middleDrvServer, usePseudoConnection, p, pseudoPassword, null );
    }

}
