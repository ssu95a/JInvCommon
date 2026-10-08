package ru.inversion.db.session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.inversion.db.session.cfg_impl.Mdl_DataSourceCfgBuilder;
import ru.inversion.db.session.cfg_impl.Ora_DataSourceCfgBuilder;
import ru.inversion.db.session.cfg_impl.Pg_DataSourceCfgBuilder;
import ru.inversion.db.session.cfg_impl.Srv_DataSourceCfgBuilder;
import ru.inversion.db.session.cfg_impl.srv.ClientParameter;
import ru.inversion.utils.S;
import ru.inversion.utils.Tags;

import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;


/**
 * @author ssu
 */
public abstract class AbstractDataSourceCfgBuilder {
    
    final protected static Logger g_logger = LoggerFactory.getLogger(AbstractDataSourceCfgBuilder.class);

    final static public String V$SESSION_PROGRAM  = "XxiConnector";
    final static public String ORACLE_DRIVER      = "oracle.jdbc.OracleDriver";
    final static public String POSTGRESQL_DRIVER  = "org.postgresql.Driver";
    final static public String MIDDLE_DRIVER      = "ru.inversion.jdbc.InversionDriver";

    protected String login, password, appId, dbUrl;

    protected boolean usePseudoConnection = true;

    protected BiFunction<Connection, Object, Object> resultHandler;

    protected final Map<String,String> properties = new HashMap<>();

    static {

        ClientParameter.initGlobalValueSupplier( new Function<ClientParameter, String>() {
            @Override
            public String apply( ClientParameter p ) {
                switch (p) {
                    case APP_NAME:
                        return "XxiConnect";
                    case APP_KIND:
                        return "java-standalone";
                    case APP_VERSION:
                        return "1.0";
                }
                return null;
            }
        });
    }

    /** */
    public AbstractDataSourceCfgBuilder( String appId ) {
        this.appId = appId;
    }

    /** */
    public AbstractDataSourceCfgBuilder resultHandler( BiFunction<Connection, Object, Object> resultHandler ) {
        this.resultHandler = resultHandler;
        return this;
    }

    /** */
    public AbstractDataSourceCfgBuilder login   ( String login ) {
        this.login = login;
        return this;
    }
    
    /** */
    public AbstractDataSourceCfgBuilder password( String password ) {
        this.password = password;
        return this;
    }
    
    /** */
    public AbstractDataSourceCfgBuilder usePseudoConnection( boolean v ) {
        this.usePseudoConnection = v;
        return this;
    }

    /** */
    public AbstractDataSourceCfgBuilder property( String property, Object value ) {
        if( value == null )
            this.properties.remove( property );
        else
            properties.put( property, value.toString() );
        return this;
    }

    /** */
    public AbstractDataSourceCfgBuilder dbUrl( String dbUrl ) {
        this.dbUrl = dbUrl;
        return this;
    }

    /** */
    protected void preBuild( ) {

        if( S.isNullOrEmpty(login) )
            throw new IllegalStateException( Tags.PRODUCT_LABEL + "login is empty" );

        if( S.isNullOrEmpty(password) )
            throw new IllegalStateException( Tags.PRODUCT_LABEL + "password is empty" );

        if( S.isNullOrEmpty(dbUrl) )
            throw new IllegalStateException( Tags.PRODUCT_LABEL + "dbUrl is empty" );
    }

    /** */
    public abstract DataSourceCfg build( ) throws Exception;

    /** */
    public static AbstractDataSourceCfgBuilder create( Function<String,String> p )
    {
        String middleDrvServer = p.apply("middle_drv_server");
        String appId           = p.apply("app_id");
        String authServer      = p.apply("auth_server");

        if( !S.isNullOrEmpty(middleDrvServer) )
        {
            return new Mdl_DataSourceCfgBuilder( appId, middleDrvServer );
        }
        else if( !S.isNullOrEmpty(authServer) )
        {
            return new Srv_DataSourceCfgBuilder( appId, authServer );
        }
        else
        {
            String s = p.apply("vendor_db");

            if( S.isNullOrEmpty(s) )
                s = p.apply("url");

            if( !S.isNullOrEmpty(s) && s.contains("postgres") )
                 return new Pg_DataSourceCfgBuilder ( appId );
            else
                return new Ora_DataSourceCfgBuilder( appId );
        }
    }
}
