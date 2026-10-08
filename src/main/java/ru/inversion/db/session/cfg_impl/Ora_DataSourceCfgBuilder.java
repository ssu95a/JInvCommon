package ru.inversion.db.session.cfg_impl;

import ru.inversion.db.session.DataSourceCfg;
import ru.inversion.db.session.AbstractDataSourceCfgBuilder;
import ru.inversion.db.session.xxi.XxiConnector;
import ru.inversion.utils.S;
import ru.inversion.utils.Tags;
import ru.inversion.utils.U;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.PasswordAuthentication;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Function;
import java.util.prefs.Preferences;

public class Ora_DataSourceCfgBuilder extends AbstractDataSourceCfgBuilder {

    public static final String ORACLE_TNS_ADMIN = "oracle.net.tns_admin";

    public static final String TNS_ADMIN = "TNS_ADMIN";

    public static final String TNSNAMES_ORA = "tnsnames.ora";

    /**
     * @param appId
     */
    public Ora_DataSourceCfgBuilder( String appId ) {
        super(appId);
    }

    /** */
    private static void checkTnsPath( ) throws Exception {

        if( System.getProperty( ORACLE_TNS_ADMIN ) == null )
        {
            Optional.ofNullable (
                U.firstNotNull (
                    System.getenv( TNS_ADMIN ),
                    Preferences.userRoot().get( TNS_ADMIN, null )
                )
            )
                .ifPresent( (String tns) -> System.setProperty( ORACLE_TNS_ADMIN, tns ) );
        }

        String s = System.getProperty( ORACLE_TNS_ADMIN );

        if( S.isNullOrEmpty(s) )
            throw new IllegalStateException( Tags.PRODUCT_LABEL + "Property 'oracle.net.tns_admin' is not configured" );

        File dir = new File(s);

        if( !dir.exists() )
            throw new FileNotFoundException( Tags.PRODUCT_LABEL + "Directory with the path '" + s + "' does not exist");

        if( !dir.isDirectory() )
            throw new NotDirectoryException( Tags.PRODUCT_LABEL + "'" + s + "' is not a directory");

        final File tnsnames = new File( dir, TNSNAMES_ORA );

        if( !tnsnames.exists() || !tnsnames.isFile() )
            throw new NoSuchFileException( Tags.PRODUCT_LABEL + "In the directory '" + s + "' does not exist file 'tnsnames.ora'");
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
        info.put( "v$session.module",  appId );

        info.putAll( this.properties );

        return info;
    }

    /** */
    public DataSourceCfg build( ) throws Exception {

        preBuild( );

        Properties p = initProperties( );

        String  connectUrl     = null;
        String  pseudoPassword = null;

        checkTnsPath( );

        System.setProperty("ru.inversion.driver.class_name", ORACLE_DRIVER );

        try {
            Class.forName( ORACLE_DRIVER );
        }
        catch( Throwable th ) {
            throw new RuntimeException( Tags.PRODUCT_LABEL + "Error init JDBC driver", th );
        }

        if( dbUrl.indexOf(':') == -1 )
            connectUrl = String.format("jdbc:oracle:thin:@%s", dbUrl );
        else
            connectUrl = dbUrl;

        if( usePseudoConnection ) {

            pseudoPassword = password;

            final PasswordAuthentication auth = XxiConnector.login( connectUrl, this.login, pseudoPassword, p, resultHandler) ;

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
                            urlR = String.format("jdbc:oracle:thin:@%s", urlI );
                        else
                            urlR = urlI;
                    }

                    if( "true".equals( properties.getProperty( "ru.inversion.pseudo_connection" ) ) )
                    {
                        String pseudoPassword = properties.getProperty("password");
                        String user           = properties.getProperty("user");

                        final PasswordAuthentication auth = XxiConnector.login( urlR, user, pseudoPassword, p, null ) ;

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
