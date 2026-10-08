package ru.inversion.db.session.cfg_impl;

import ru.inversion.db.session.DataSourceCfg;
import ru.inversion.db.session.AbstractDataSourceCfgBuilder;
import ru.inversion.db.session.xxi.PGPseudoConnector;
import ru.inversion.utils.S;
import ru.inversion.utils.Tags;

import java.net.PasswordAuthentication;
import java.util.Properties;
import java.util.function.Function;

/** */
public class Pg_DataSourceCfgBuilder extends AbstractDataSourceCfgBuilder {

    /** */
    public Pg_DataSourceCfgBuilder( String appId ) {
        super(appId);
    }

    /** */
    private Properties initProperties() {

        Properties info = new Properties();

        //info.put( "v$session.module", appId );

        info.setProperty( "useUnicode",           "true" );
        info.setProperty( "characterEncoding",    "utf8" );
        info.setProperty( "ApplicationName",      V$SESSION_PROGRAM );
        info.setProperty( "escapeSyntaxCallMode", "callIfNoReturn"  ); //callIfNoReturn
        info.setProperty( "preferQueryMode",      "extendedForPrepared" );
        info.setProperty( "defaultRowFetchSize",  "60"   );
        info.setProperty( "prepareThreshold",     "0"    );

        info.putAll( this.properties );

        return info;
    }

    /** */
    public DataSourceCfg build( ) throws Exception {

        preBuild( );

        Properties p = initProperties( );

        //System.setProperty( "ru.inversion.driver.class_name", POSTGRESQL_DRIVER );

        //login = login.toLowerCase();

        try {
            Class.forName(POSTGRESQL_DRIVER);
        }
        catch( Throwable th ) {
            throw new RuntimeException( Tags.PRODUCT_LABEL + "Error init JDBC driver", th );
        }

        final String connectUrl;

        if( dbUrl.indexOf(':') == -1 )
            connectUrl = String.format("jdbc:postgresql://%s", dbUrl );
        else
            connectUrl = dbUrl;

        String  pseudoPassword = null;

        if(usePseudoConnection) {

            pseudoPassword = password;

            final PasswordAuthentication auth = PGPseudoConnector.login( connectUrl, this.login, pseudoPassword, p, resultHandler) ;

            this.login    = auth.getUserName();
            this.password = String.valueOf( auth.getPassword() );
        }

        return new DataSourceCfg(login, password, connectUrl, dbUrl, null, usePseudoConnection, p, pseudoPassword, new Function< Properties, String >() {
            @Override
            public String apply( Properties properties ) {

                try {

                    String urlR = null;
                    String urlI = properties.getProperty("url");
                    if( !S.isNullOrEmpty(urlI) )
                    {
                        if( urlI.indexOf(':') == -1 )
                            urlR = String.format("jdbc:postgresql://%s", urlI );
                        else
                            urlR = urlI;

                    }

                    if( "true".equals( properties.getProperty( "ru.inversion.pseudo_connection" ) ) )
                    {
                        String pseudoPassword = properties.getProperty("password");
                        String user           = properties.getProperty("user");

                        final PasswordAuthentication auth = PGPseudoConnector.login( urlR, user, pseudoPassword, p, null ) ;

                        properties.setProperty("user",     auth.getUserName() );
                        properties.setProperty("password", String.valueOf( auth.getPassword() ) );
                    }

                    return urlR;
                }
                catch( Throwable th ) {
                    throw new RuntimeException( Tags.PRODUCT_LABEL + "Error on make URL to database", th );
                }

            }
        });
    }
}
