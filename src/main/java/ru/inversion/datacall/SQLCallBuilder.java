package ru.inversion.datacall;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import ru.inversion.datacall.impl.SQLExprXMLLoader;
import ru.inversion.dataset.IParameters;
import ru.inversion.db.dialect.SqlDialect;
import ru.inversion.meta.impl.work.IDBWork;
import ru.inversion.parser.statement.LangEnum;
import ru.inversion.tc.TaskContext;
import ru.inversion.utils.*;
import ru.inversion.utils.lstn.IListenerManEvent;
import ru.inversion.utils.lstn.ListenerManFactory;

import javax.persistence.NamedStoredProcedureQueries;
import javax.persistence.NamedStoredProcedureQuery;
import javax.persistence.StoredProcedureParameter;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static javax.persistence.ParameterMode.OUT;
import static ru.inversion.parser.statement.LangEnum.*;


/**
 * Построитель объектов для выполнения хранимого блока кода на языке SQL, PL/SQL, JDBC, PLpg/SQL
 * <p>
 * @author Sulimoff
 * @version 3.0-04.02.2026/1.2-02.04.2019
 *
 * @see ru.inversion.datacall.IDataCall
 * @see CallException
 * */
public class SQLCallBuilder {

    static final private Cache<String, Map<String, CallStatement >> callCache;

    static {
        callCache = Caffeine.newBuilder().maximumSize(16).build();
    }

    private static Consumer< Pair<Object,String> > callRegister;

    public static void setCallRegister( Consumer< Pair<Object,String> > cr ) {
        callRegister = cr;
    }
    public static Consumer< Pair<Object,String> > getCallRegister( ) {
        return callRegister;
    }

    /** */
    private final TaskContext tc;

    /** */
    private Class<?> runClass;

    /** */
    private URL url;

    /** */
    private Function<String, CallStatement> fcs;

    /** */
    private String name;

    /** */
    private IParameters callBackParameters;

    /** */
    private SQLCallBuilder( TaskContext tc ) {
        this.tc = tc;
    }

    /** */
    private boolean runByDbmsSql = false;

    /** */
    public static SQLCallBuilder NEW( TaskContext tc ) {
        return new SQLCallBuilder( tc );
    }

    /** */
    public SQLCallBuilder name( String name ) {
        this.name = name;
        return this;
    }

    /** */
    public SQLCallBuilder runByDbmsSql( boolean v ) {
        this.runByDbmsSql = v;
        return this;
    }

    /** */
    public SQLCallBuilder runClass( Class runClass ) {
        this.runClass = runClass;
        return this;
    }

    /** */
    public SQLCallBuilder url( URL url ) {
        this.url = url;
        return this;
    }

    /** */
    public SQLCallBuilder callStatement( Function<String, CallStatement> fcs )
    {
        this.fcs = fcs;

        if( fcs != null )
        {
            url = null; runClass = null;
        }

        return this;
    }

    /** */
    public SQLCallBuilder closeAfterExecute( boolean v ) {
        return this;
    }

    /** */
    public SQLCallBuilder callBackParameters( IParameters v ) {
        this.callBackParameters = v;
        return this;
    }

    /** Регистрация вызова в системе вызовов*/
    private void registerCall( )
    {
        if( callRegister == null )
            return;

        final int mode = fcs != null ? 1 : ( url == null ? 2 : 3 );

        final Object v;

        switch( mode ) {
            case 1: v = "#callStatement"; break;
            case 2: v = runClass; break;
            case 3: v = url; break;
            default:
                v = "<unknown>";
        }

        callRegister.accept( Pair.makePair( v, name ) );
    }

    /** */
    private String cacheKey( String base ) {
        SqlDialect d = Objects.requireNonNull( tc.dialect(), "'tc.dialect()' is null");
        return base + "::" + d.type().name();
    }


    /** */
    public IDataCall build( ) throws CallException {

        registerCall( );

        if( fcs != null )
            return buildByFcs();

        return url == null ? buildByRunClass( ) : (IDataCall)buildByUrl(true);
    }

    /** */
    private IDataCall buildByFcs( ) throws CallException {

        try {

            // Если имя не задано, то в кэш не кладем
            if( S.isNullOrEmpty(name) )
                return build( fcs.apply(null) );

            return build( callCache.get( cacheKey("#callStatement"), (s)-> new ConcurrentHashMap<>() ).computeIfAbsent( name, fcs ) );
        }
        catch( Throwable th ) {
            throw new CallException( Tags.PRODUCT_LABEL + "Error on create CallStatement by #callStatement mode.", th );
        }
    }

    /** */
    private IDataCall buildByRunClass( ) throws CallException {

        try {

            if( runClass == null )
                throw new IllegalStateException("'runClass' is null");

            if( S.isNullOrEmpty(name) )
                throw new IllegalStateException("'name' is null");

            Map<String, CallStatement> m = callCache.get( cacheKey( runClass.getName() ), (s) -> parseRunClass() );
            CallStatement st = m.get(name);

            if( st == null )
                throw new IllegalStateException(Tags.PRODUCT_LABEL + "SQL выражение '" + name + "' не найдено в " + runClass.toString() );

            return new SQLDataCall (
                st.createCallWork( tc, this.callBackParameters, true )
            );

        } catch( Throwable th ) {
            throw new CallException( Tags.PRODUCT_LABEL + "Error on create CallStatement by class: " + S.nvl( U.callIfNotNull( runClass, Class::getName ) ), th );
        }
    }

    /** */
    private Map<String,CallStatement> parseRunClass( ) throws CallException {

        Map<String,CallStatement> stMap = new HashMap<>();

        NamedStoredProcedureQuery nspq = runClass.getAnnotation( NamedStoredProcedureQuery.class );

        if( nspq != null )
            stMap.put( nspq.name(), parse(nspq) );

        NamedStoredProcedureQueries nspqs = runClass.getAnnotation( NamedStoredProcedureQueries.class );

        if( nspqs != null ) {

            for( NamedStoredProcedureQuery n : nspqs.value() )
                stMap.put( n.name(), parse(n) );
        }//end if

        return stMap;
    }

    /** */
    private CallStatement parse( NamedStoredProcedureQuery nspq ) throws CallException {

        CallBuilder cb = CallBuilder.NEW();

        StringBuilder sb = new StringBuilder();

        int index = 1;
        Class retClass = null;

        if( nspq.resultClasses().length == 0 )
            sb.append("{call ");

        else
            retClass = nspq.resultClasses()[0];

        sb.append( nspq.procedureName() ).append("(");

        StoredProcedureParameter[] ps = nspq.parameters();

        boolean first = true;
        boolean hasNamed = false;

        if(ps != null) {

            for( StoredProcedureParameter p : ps ) {

                if( !first )
                    sb.append(", ");

                if( S.isNullOrEmpty( p.name() ) )
                {
                    cb.parameter( p.mode(), index++, Jt2St.get( p.type() ), null, p.type(), null, true );
                    sb.append("?");
                }
                else
                {
                    cb.parameter(p.mode(), p.name(), Jt2St.get(p.type()), null, p.type(), null, true);
                    sb.append(" :").append( p.name() );
                    hasNamed = true;
                }

                first = false;

            }//end for
        }

        sb.append(")}");

        if( retClass != null )
        {
            ParameterMetadataBuilder pmb = new ParameterMetadataBuilder();

            if( index > 1 || !hasNamed )
            {
                pmb.position(0);
                sb.insert  ( 0, "{?=call " );
            }
            else
            {
                pmb.name  ("_return");
                sb.insert( 0, "{:_return=call " );
            }

            cb.putParameter( pmb.mode(OUT).javaType(retClass).sqlType( Jt2St.get(retClass) ).isReturnValue(true).build() );

        }

        return cb.sql( sb.toString() ).lang( LangEnum.JDBC ).removeAdditionalInfo(true).build();
    }

    /** */
    private Object buildByUrl( boolean call  ) throws CallException {

        try {

            if( url == null )
                throw new IllegalStateException( "'url' is null");

            if( S.isNullOrEmpty(name) )
                throw new IllegalStateException( "'name' is null");

            final CallStatement callStatement =
                    ( !S.isNullOrEmpty( url.getPath() ) && url.getPath().contains(":no_cache:") )
                            ?
                            parseUrl().get( name )
                            :
                            callCache.get ( cacheKey( url.toString() ), (s) -> parseUrl() ).get( name );

            if( callStatement == null )
                throw new IllegalStateException(Tags.PRODUCT_LABEL + "SQL выражение '" + name + "' не найдено в " + url.toString() );

            return call ? build( callStatement ) : callStatement;

        } catch( Throwable th ) {
            throw new CallException( Tags.PRODUCT_LABEL + "Error on create CallStatement by Url: " + ( url == null ? "<null>" : url.toString() ), th );
        }
    }

    /** */
    CallStatement buildStatement() {
        return (CallStatement)buildByUrl(false);
    }

    /** */
    private IDataCall build( CallStatement callStatement )
    {
        /** */
        if( !runByDbmsSql ) {

            if (
                //LangEnum.getGLangPL() == PL_pgSQL &&
                    tc.dialect().type() == SqlDialect.DialectId.POSTGRES &&
                            U.in( callStatement.getLang(), PL_pgSQL, PL ) &&
                            callStatement.isAnonymousBlock()
            )
                return new JsonSqlCall( tc, callStatement, callBackParameters, name );

            return new SQLDataCall( callStatement.createCallWork( tc, callBackParameters ) );
        }

        /** */
        return new IDataCall() {
            final IDBWork dsw = new DbmsSqlWork( tc, callStatement, callBackParameters );
            /** Коллекция слушателей событий самого DataCall */
            final private IListenerManEvent<IDataCallListener, DataCallEvent> dataCallListeners = ListenerManFactory.createListenerManEvent( IDataCallListener::dataCallChanged );
            @Override
            public String getName() throws CallException { return name; }
            @Override
            public void setName( String name ) throws CallException { }
            @Override
            public void setProperty( String propertyName, Object value ) throws CallException { }
            @Override
            public Object getProperty( String propertyName ) throws CallException { return null; }
            @Override
            public IDataCall execute() throws CallException {
                dataCallListeners.fire( new DataCallEvent( this, DataCallEvent.Type.EXECUTE, true  ) );
                try {
                    this.dsw.execute( null );
                } catch(Exception e) {
                    throw new CallException( Tags.PRODUCT_LABEL + "Error on call " + dsw.getClass().getName(), e );
                }
                dataCallListeners.fire( new DataCallEvent( this, DataCallEvent.Type.EXECUTE, false ) );
                return this;
            }
            @Override
            public < V > void setValueCallback( Object id, Supplier< V > supplier ) throws CallException { }
            @Override
            public < V > void setValueListener( Object id, BiConsumer< Object, V > consumer ) throws CallException { }
            @Override
            public void setValueListener( BiConsumer< Object, Object > consumer ) throws CallException { }
            @Override
            public void addDataCallListener( IDataCallListener l ) throws CallException { }
            @Override
            public void removeDataCallListener( IDataCallListener l ) throws CallException { }
            @Override
            public ParameterMetadata getParameterMetadata( Object id ) throws CallException { return null; }

            @Override
            public Iterable<ParameterMetadata<?>> getParameterIter() {
                //throw new UnsupportedOperationException("getParameterIter");
                return callStatement.getParameterIter();
            }

            @Override
            public void close() { }
            @Override
            public Object getParameter( String parameterName ) { return null; }
            @Override
            public Object getParameter( int parameterIndex ) { return null; }
        };
    }

    /** */
    private Map<String,CallStatement> parseUrl( ) throws CallException {
        SQLExprXMLLoader loader = new SQLExprXMLLoader( url );
        return loader.parse( tc.dialect() );
    }

    /** */
    private static class SQLDataCall implements IDataCall {

        /** Исполнение */
        private CallWork work;

        /** Коллекция слушателей событий самого DataCall */
        final private IListenerManEvent<IDataCallListener, DataCallEvent> dataCallListeners = ListenerManFactory.createListenerManEvent( IDataCallListener::dataCallChanged );

        /** */
        private SQLDataCall( CallWork work ) {
            this.work = Objects.requireNonNull( work, "'work' is null" );
        }

        /** Наименование */
        private String name;

        /** Дополнительные пользовательские свойства DataCall */
        private Map<String,Object> properties;

        @Override
        public String getName() {
            return name;
        }

        @Override
        public void setName( String name ) {
            this.name = name;
        }

        /** {@inheritDoc} */
        @Override
        public Object getProperty(String propertyName) {
            return properties == null ? null : properties.get(propertyName);
        }

        /** {@inheritDoc} */
        @Override
        public void setProperty(String propertyName, Object value) {
            if( properties == null )
                properties = new HashMap<>();
            properties.put( propertyName, value );
        }

        @Override
        public IDataCall execute( ) throws CallException {

            dataCallListeners.fire( new DataCallEvent( this, DataCallEvent.Type.EXECUTE, true ) );

            this.work.execute( );

            dataCallListeners.fire( new DataCallEvent( this, DataCallEvent.Type.EXECUTE, false ) );

            return this;
        }

        @Override
        public <V> void setValueCallback( Object id, Supplier<V> supplier ) throws CallException {

            try {

                if( id == null )
                    throw new IllegalArgumentException("'id' param is null");

                if( id instanceof Number )
                    this.work.set( ((Number)id).intValue(), supplier );
                else
                    this.work.set( id.toString(), supplier );
            }
            catch( Throwable th ) {
                throw new CallException( Tags.PRODUCT_LABEL + "Error on 'setValueCallback' to dataCall", th );
            }
        }

        @Override
        public <V> void setValueListener( Object id, BiConsumer< Object, V > listener ) throws CallException {

            try {

                if( id == null )
                    throw new IllegalArgumentException("'id' param is null");

                this.work.outCallback( (Comparable)id, listener);
            }
            catch( Throwable th ) {
                throw new CallException( Tags.PRODUCT_LABEL + "Error on 'setValueListener' to dataCall", th );
            }
        }

        @Override
        public void setValueListener( BiConsumer< Object, Object > listener ) throws CallException {
            try {
                this.work.outCallback( listener );
            }
            catch( Throwable th ) {
                throw new CallException( Tags.PRODUCT_LABEL + "Error on 'setValueListener' to dataCall", th );
            }
        }

        @Override
        public void addDataCallListener( IDataCallListener l ) {
            dataCallListeners.addListener(l);
        }

        @Override
        public void removeDataCallListener( IDataCallListener l ) {
            dataCallListeners.removeListener(l);
        }

        @Override
        public ParameterMetadata getParameterMetadata( Object id ) throws CallException {

            if( id == null )
                return null;

            if( id instanceof Number )
                return this.work.getParameterMetadata( ((Number)id).intValue() );
            else
                return this.work.getParameterMetadata( id.toString() );
        }

        @Override
        public Iterable<ParameterMetadata<?>> getParameterIter() {
            return work.getParameterIter();
        }

        @Override
        public <T> T getReturnValue() {
            return work.getReturnValue();
        }

        @Override
        public Object getParameter( String parameterName ) {
            try {
                return work.get(parameterName);
            }
            catch( Throwable th ) {
                throw new RuntimeException( Tags.PRODUCT_LABEL + "Error on get '" + parameterName + "' parameter value", th );
            }
        }
        /** */
        @Override
        public Object getParameter( int parameterIndex ) {
            try {
                return work.get(parameterIndex);
            }
            catch( Throwable th ) {
                throw new RuntimeException( Tags.PRODUCT_LABEL + "Error on get '" + parameterIndex + "' parameter value", th );
            }
        }
        @Override
        public void setParameter( String parameterName, Object value ) {
            try {
                work.set( parameterName, value );
            }
            catch( Throwable th ) {
                throw new RuntimeException( Tags.PRODUCT_LABEL + "Error on set '" + parameterName + "' parameter value", th );
            }
        }
        /** */
        @Override
        public void setParameter( int parameterIndex, Object value ) {
            try {
                work.set(parameterIndex, value);
            }
            catch( Throwable th ) {
                throw new RuntimeException( Tags.PRODUCT_LABEL + "Error on set '" + parameterIndex + "' parameter value", th );
            }
        }

        @Override
        public boolean hasParameter( int parameterIndex ) {
            return work.getParameterMetadata(parameterIndex) != null;
        }

        @Override
        public boolean hasParameter( String parameterName ) {
            return work.getParameterMetadata(parameterName) != null;
        }

        /** */
        @Override
        public void close() {

            if( work != null ) {

                dataCallListeners.fire( new DataCallEvent( this, DataCallEvent.Type.CLOSE, true  ) );

                work.close();

                work = null;

                dataCallListeners.fire( new DataCallEvent( this, DataCallEvent.Type.CLOSE, false ) );
            }
        }
    }
}
