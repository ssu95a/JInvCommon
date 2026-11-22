package ru.inversion.db.session.cfg_impl;

import ru.inversion.db.session.DataSourceCfg;
import ru.inversion.db.session.AbstractDataSourceCfgBuilder;
import ru.inversion.db.session.xxi.XXIConnectorException;
import ru.inversion.db.session.xxi.XxiConnector;
import ru.inversion.utils.S;
import ru.inversion.utils.Tags;
import ru.inversion.utils.U;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.PasswordAuthentication;
import java.nio.file.AccessDeniedException;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Function;
import java.util.prefs.Preferences;

import static ru.inversion.db.session.xxi.XXIConnectorException.Reason.TNS_CONFIGURATION_ERROR;

public class Ora_DataSourceCfgBuilder extends AbstractDataSourceCfgBuilder {

    public static final String ORACLE_TNS_ADMIN = "oracle.net.tns_admin";

    public static final String TNS_ADMIN = "TNS_ADMIN";

    public static final String TNSNAMES_ORA = "tnsnames.ora";

    private static final String THIN_PREFIX = "jdbc:oracle:thin:@";

    /**
     * @param appId
     */
    public Ora_DataSourceCfgBuilder( String appId ) {
        super(appId);
    }

    /** */
    private static void checkTnsPath() {

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
            throw tnsConfigurationError(new IllegalStateException("Property 'oracle.net.tns_admin' is not configured"));

        File dir = new File(s);

        if( !dir.exists() )
            throw tnsConfigurationError(new FileNotFoundException("TNS_ADMIN directory does not exist: " + s));

        if( !dir.isDirectory() )
            throw tnsConfigurationError(new NotDirectoryException(s));

        final File tnsnames = new File( dir, TNSNAMES_ORA );

        if (!tnsnames.isFile())
            throw tnsConfigurationError(new NoSuchFileException(tnsnames.getPath()));

        if (!tnsnames.canRead())
            throw tnsConfigurationError(new AccessDeniedException(tnsnames.getPath()));
    }

    private static XXIConnectorException tnsConfigurationError(Exception cause) {
        return XXIConnectorException.of(TNS_CONFIGURATION_ERROR, TNS_CONFIGURATION_ERROR.getDefaultMessage(), cause);
    }

    private static String prepareJdbcUrl(String url) {
        String jdbcUrl = url.indexOf(':') == -1 ? THIN_PREFIX + url : url;

        if (jdbcUrl.startsWith(THIN_PREFIX)) {
            int options = jdbcUrl.indexOf('?', THIN_PREFIX.length());
            String target = jdbcUrl.substring(THIN_PREFIX.length(),
                    options < 0 ? jdbcUrl.length() : options).trim();
            // Алиас ссылается на tnsnames.ora, адрес и дескриптор заданы прямо в URL.
            if (target.startsWith("(") || target.indexOf('/') >= 0 || target.indexOf(':') >= 0)
                return jdbcUrl;
        }

        checkTnsPath();
        return jdbcUrl;
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

        String  connectUrl     = prepareJdbcUrl(dbUrl);
        String  pseudoPassword = null;

        System.setProperty("ru.inversion.driver.class_name", ORACLE_DRIVER );

        try {
            Class.forName( ORACLE_DRIVER );
        }
        catch( Throwable th ) {
            throw new RuntimeException( Tags.PRODUCT_LABEL + "Error init JDBC driver", th );
        }

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

                    String urlI = properties.getProperty("url");
                    String urlR = S.isNullOrEmpty(urlI) ? null : prepareJdbcUrl(urlI);

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
                catch (XXIConnectorException ex) {
                    throw ex;
                }
                catch( Throwable th ) {
                    throw new RuntimeException( Tags.PRODUCT_LABEL + "Error on make URL to database", th );
                }
            }
        });
    }

}
