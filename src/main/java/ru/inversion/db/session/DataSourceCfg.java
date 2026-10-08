package ru.inversion.db.session;

import ru.inversion.utils.S;

import java.util.Map;
import java.util.Properties;
import java.util.function.Function;

/**
 *
 * @author ssu
 */
public class DataSourceCfg  {
    //
    private final boolean usePseudoConnection;
    //
    private final String    login, 
                            dbUrl,
                            middleDrvServer,
                            connectUrl;

    private String pseudoPassword, password;

    private final Properties properties;

    private final Function<Properties,String> urlMaker;

    /** */
    public DataSourceCfg( String login, String password, String connectUrl, String dbUrl, String middleDrvServer, boolean usePseudoConnection, Properties properties, String pseudoPassword, Function< Properties, String > urlMaker )
    {
        this.login           = login;
        this.password        = password;
        this.dbUrl           = dbUrl;
        this.properties      = properties;
        this.middleDrvServer = middleDrvServer;
        this.usePseudoConnection
                             = usePseudoConnection;
        this.connectUrl      = connectUrl;
        this.pseudoPassword  = pseudoPassword;
        this.urlMaker        = urlMaker;
    }
    
    /** */
    public Properties getConnectionProperties( ) {
        
        Properties p = new Properties(properties);
        p.setProperty( "user",     login );
        p.setProperty( "password", password );
        p.setProperty( "ru.inversion.pseudo_connection", Boolean.toString( isUsePseudoConnection()) );
        
        return p;
    }

    /** */
    public Properties getConnectionProperties( String username, String pswd, String url ) {

        Properties p = new Properties(properties);
        p.setProperty( "user",     username );
        p.setProperty( "password", pswd     );
        p.setProperty( "ru.inversion.pseudo_connection", Boolean.toString( isUsePseudoConnection()) );

        if( S.isNullOrEmpty(url) )
            return p;

        p.setProperty( "url", url );

        if( urlMaker != null )
        {
            String newUrl = urlMaker.apply( p );
            p.setProperty( "url", newUrl );
        }

        return p;
    }

    /** */
    public String getProperty(String property) {
        return properties.getProperty(property);
    }

    /** */
    public boolean isMiddleMode( ) {
        return middleDrvServer != null;
    }
    
    /** */
    public boolean isUsePseudoConnection( ) {
        return usePseudoConnection;
    }
    
    /** */
    public String getLogin( ) {
        return login;
    }
    
    /** */
    public String getDbUrl( ) {
        return dbUrl;
    }
    
    /** */
    public String getConnectUrl( ) {
        return connectUrl;
    }
    
    /** */
    public String getMiddleServer( ) {
        return middleDrvServer; 
    }
    
    /** */
    public String getPassword( ) {
        return password;
    }

    /** */
    public String getPseudoPassword( ) {
        return pseudoPassword;
    }

    /** */
    public void applyCfg( Map<String,Object> cfg ) {

        if( cfg == null || cfg.isEmpty() )
            return;

        String v = (String)cfg.get("password");

        if( v != null )
        {
            if( !usePseudoConnection )
                password = v;
            else
            {
                Boolean b = (Boolean) cfg.get("pseudo_user");
                if( b == null || b )
                    pseudoPassword = v;
                else
                    password = v;
            }
        }
    }
}
