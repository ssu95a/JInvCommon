package ru.inversion.datacall;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import ru.inversion.datacall.impl.SQLExprXMLLoader;
import ru.inversion.dataset.IParameters;
import ru.inversion.db.dialect.SqlDialect;
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
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import static javax.persistence.ParameterMode.OUT;
import static ru.inversion.parser.statement.LangEnum.*;


/**
 * Построитель объектов для выполнения хранимого блока кода на языке SQL, PL/SQL, JDBC, PLpg/SQL
 * <p>
 * Системное свойство {@code ru.inversion.datacall.pg_anonymous_direct=true}
 * включает выполнение анонимных блоков PL/pgSQL через {@link PGAnonymousSqlCall}.
 * По умолчанию используется {@link JsonSqlCall}.
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

    /** Закрывать JDBC statement после каждого execute */
    private boolean closeAfterExecute = true;

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
        this.closeAfterExecute = v;
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
        SqlDialect d = Checks.Require.object( tc.dialect(), "'tc.dialect()' is null");
        return base + "::" + d.type().name();
    }


    /** */
    public IDataCall build( ) throws CallException {

        registerCall( );

        if( fcs != null )
            return buildByFcs();

        return url == null ? buildByRunClass( ) : buildByUrl(this::build);
    }

    /** Подготавливает метаданные анонимного блока PostgreSQL из URL для одного запуска. */
    public IDataCall buildWithAnonymousPostgresParameters(UnaryOperator<ParameterMetadata<?>> prepare) {
        Objects.requireNonNull(prepare, "prepare");
        registerCall();
        return buildByUrl(statement -> build(statement, prepare));
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

            return new SQLDataCall( st.createCallWork( tc, this.callBackParameters, closeAfterExecute ), name );

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
    private <T> T buildByUrl( Function<CallStatement, T> finish ) throws CallException {

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

            return finish.apply(callStatement);

        } catch( Throwable th ) {
            throw new CallException( Tags.PRODUCT_LABEL + "Error on create CallStatement by Url: " + ( url == null ? "<null>" : url.toString() ), th );
        }
    }

    /** */
    CallStatement buildStatement() {
        return buildByUrl(Function.identity());
    }

    /** */
    private IDataCall build( CallStatement callStatement )
    {
        return build(callStatement, null);
    }

    /** */
    private IDataCall build(CallStatement callStatement, UnaryOperator<ParameterMetadata<?>> prepare)
    {
        if( !runByDbmsSql )
        {

            if( tc.dialect().type() == SqlDialect.DialectId.POSTGRES && U.in( callStatement.getLang(), PL_pgSQL, PL ) && callStatement.isAnonymousBlock() )
            {
                //if( Boolean.parseBoolean(System.getProperty("ru.inversion.datacall.pg_anonymous_direct")) )
                CallStatement prepared = prepare == null ? callStatement : callStatement.mapParameters(prepare);
                return new PGAnonymousSqlCall( tc, prepared, callBackParameters, name );
                //return new JsonSqlCall( tc, callStatement, callBackParameters, name );
            }

            return new SQLDataCall(
                callStatement.createCallWork( tc, callBackParameters, closeAfterExecute ),
                name
            );
        }

        return new DbmsSqlDataCall( tc, callStatement, callBackParameters, name );
    }

    /** */
    private Map<String,CallStatement> parseUrl( ) throws CallException {
        SQLExprXMLLoader loader = new SQLExprXMLLoader( url );
        return loader.parse( tc.dialect() );
    }

    /** Адаптер IDataCall для выполнения через Oracle DBMS_SQL. */
    static class DbmsSqlDataCall implements IDataCall {

        private DbmsSqlWork work;
        private final CallStatement callStatement;
        private final ParametersValues values;

        private final Map<Comparable,Supplier<?>> valueCallbacks = new TreeMap<>(ParametersValues.ParamIdComparator);
        private final Map<Comparable,BiConsumer<Object,Object>> valueListeners = new TreeMap<>(ParametersValues.ParamIdComparator);

        private final IListenerManEvent<IDataCallListener, DataCallEvent> dataCallListeners = ListenerManFactory.createListenerManEvent(IDataCallListener::dataCallChanged);

        private BiConsumer<Object,Object> valueListener;
        private String name;
        private Map<String,Object> properties;

        private DbmsSqlDataCall(
            TaskContext tc,
            CallStatement callStatement,
            IParameters callBackParameters,
            String name
        ) throws CallException {
            this.callStatement = callStatement;
            this.values = new ParametersValues(callBackParameters);
            this.name = name;
            this.work = new DbmsSqlWork(tc, callStatement, values);
        }

        /** Позволяет проверять контракт адаптера без подключения к Oracle. */
        DbmsSqlDataCall(
            DbmsSqlWork work,
            CallStatement callStatement,
            ParametersValues values,
            String name
        ) {
            this.work = Checks.Require.object(work, "work");
            this.callStatement = callStatement;
            this.values = values;
            this.name = name;
        }

        /** */
        private static Comparable normalizeParameterId( Object id ) {
            if( id == null )
                throw new IllegalArgumentException("'id' param is null");

            return id instanceof Number ? ((Number)id).intValue() : id.toString();
        }

        /** */
        private Object value( ParameterMetadata<?> parameter ) {
            return parameter.isIndexed()
                    ? values.get(parameter.getPosition())
                    : values.get(parameter.getName());
        }

        /** */
        private void setValue( Comparable id, Object value ) {
            if( id instanceof Number )
                values.setParameter(((Number)id).intValue(), value);
            else
                values.setParameter(id.toString(), value);
        }

        @Override
        public IDataCall execute() throws CallException {

            if( work == null )
                throw new CallException(Tags.PRODUCT_LABEL + "DataCall is closed");

            dataCallListeners.fire(new DataCallEvent(this, DataCallEvent.Type.EXECUTE, true));

            Throwable failure = null;
            try {

                for( Map.Entry<Comparable,Supplier<?>> entry : valueCallbacks.entrySet() ) {
                    final Supplier<?> supplier = entry.getValue();
                    setValue(entry.getKey(), supplier == null ? null : supplier.get());
                }

                work.execute(null);

                for( ParameterMetadata<?> parameter : callStatement.getParameterIter(true) )
                {
                    final Comparable id = parameter.getIdentifier();
                    final Object outValue = value(parameter);

                    if( valueListener != null )
                        valueListener.accept(id, outValue);

                    final BiConsumer<Object,Object> listener = valueListeners.get(id);

                    if( listener != null )
                        listener.accept(id, outValue);
                }

                return this;
            }
            catch( RuntimeException ex ) {
                failure = ex instanceof CallException ? ex : new CallException(Tags.PRODUCT_LABEL + "Error on call " + DbmsSqlWork.class.getName(), ex);
                throw (RuntimeException)failure;
            }
            catch( Error error ) {
                failure = error;
                throw error;
            }
            finally {
                try {
                    dataCallListeners.fire(new DataCallEvent(this, DataCallEvent.Type.EXECUTE, false, failure == null));
                }
                catch( RuntimeException | Error afterFailure ) {
                    if( failure == null )
                        throw afterFailure;
                    failure.addSuppressed(afterFailure);
                }
            }
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public void setName( String name ) {
            this.name = name;
        }

        @Override
        public void setProperty( String propertyName, Object value ) {
            if( properties == null )
                properties = new HashMap<>();
            properties.put(propertyName, value);
        }

        @Override
        public Object getProperty( String propertyName ) {
            return properties == null ? null : properties.get(propertyName);
        }

        @Override
        public <V> void setValueCallback( Object id, Supplier<V> supplier ) throws CallException {
            final Comparable parameterId = normalizeParameterId(id);
            if( callStatement.getParameter(parameterId) == null )
                throw new CallException(Tags.PRODUCT_LABEL + "No parameter with Id " + parameterId);
            valueCallbacks.put(parameterId, supplier);
        }

        @Override
        public <V> void setValueListener( Object id, BiConsumer<Object,V> listener ) throws CallException {
            final Comparable parameterId = normalizeParameterId(id);
            final ParameterMetadata<?> parameter = callStatement.getParameter(parameterId);

            if( parameter == null )
                throw new CallException(Tags.PRODUCT_LABEL + "No parameter with Id " + parameterId);
            if( !parameter.isOut() )
                throw new CallException(Tags.PRODUCT_LABEL + "Parameter '" + parameterId + "' is not OUT or INOUT");

            valueListeners.put(parameterId, (BiConsumer<Object,Object>)(BiConsumer)listener);
        }

        @Override
        public void setValueListener( BiConsumer<Object,Object> listener ) {
            valueListener = listener;
        }

        @Override
        public void addDataCallListener( IDataCallListener listener ) {
            dataCallListeners.addListener(listener);
        }

        @Override
        public void removeDataCallListener( IDataCallListener listener ) {
            dataCallListeners.removeListener(listener);
        }

        @Override
        public ParameterMetadata<?> getParameterMetadata( Object id ) {
            return id == null ? null : callStatement.getParameter(normalizeParameterId(id));
        }

        @Override
        public Iterable<ParameterMetadata<?>> getParameterIter() {
            return callStatement.getParameterIter();
        }

        @Override
        public Object getParameter( String parameterName ) {
            return values.get(parameterName);
        }

        @Override
        public Object getParameter( int parameterIndex ) {
            return values.get(parameterIndex);
        }

        @Override
        public void setParameter( String parameterName, Object value ) {
            values.setParameter(parameterName, value);
        }

        @Override
        public void setParameter( int parameterIndex, Object value ) {
            values.setParameter(parameterIndex, value);
        }

        @Override
        public boolean hasParameter( String parameterName ) {
            return callStatement.getParameter(parameterName) != null;
        }

        @Override
        public boolean hasParameter( int parameterIndex ) {
            return callStatement.getParameter(parameterIndex) != null;
        }

        @Override
        public <T> T getReturnValue() {
            for( ParameterMetadata<?> parameter : callStatement.getParameterIter() )
                if( parameter.isReturnValue() )
                    return (T)value(parameter);
            return null;
        }

        @Override
        public void close()
        {
            if( work == null )
                return;

            Throwable failure = null;
            try {
                dataCallListeners.fire(new DataCallEvent(this, DataCallEvent.Type.CLOSE, true));
            }
            catch( RuntimeException | Error beforeFailure ) {
                failure = beforeFailure;
            }
            try {
                work.close();
            }
            catch( RuntimeException | Error cleanupFailure ) {
                if( failure == null )
                    failure = cleanupFailure;
                else
                    failure.addSuppressed(cleanupFailure);
            }
            work = null;
            try {
                dataCallListeners.fire(new DataCallEvent(this, DataCallEvent.Type.CLOSE, false));
            }
            catch( RuntimeException | Error afterFailure ) {
                if( failure == null )
                    failure = afterFailure;
                else
                    failure.addSuppressed(afterFailure);
            }
            if( failure instanceof Error )
                throw (Error)failure;
            if( failure != null )
                throw (RuntimeException)failure;
        }
    }

    /** */
    static class SQLDataCall implements IDataCall {

        /** Исполнение */
        private CallWork work;

        /** Защита от повторного вызова из слушателей событий. */
        private boolean executing;
        private boolean closing;

        /** Коллекция слушателей событий самого DataCall */
        final private IListenerManEvent<IDataCallListener, DataCallEvent> dataCallListeners = ListenerManFactory.createListenerManEvent( IDataCallListener::dataCallChanged );

        /** */
        SQLDataCall( CallWork work, String name ) {
            this.work = Checks.Require.object( work, "'work' is null" );
            this.name = name;
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
        public synchronized IDataCall execute( ) throws CallException {

            final CallWork current = requireWork();
            if( executing || closing )
                throw new CallException(Tags.PRODUCT_LABEL + "DataCall is already executing or closing");

            executing = true;
            try {
                dataCallListeners.fire( new DataCallEvent( this, DataCallEvent.Type.EXECUTE, true ) );

                Throwable failure = null;
                try {
                    current.execute();
                }
                catch( RuntimeException | Error th ) {
                    failure = th;
                    throw th;
                }
                finally {
                    try {
                        dataCallListeners.fire( new DataCallEvent( this, DataCallEvent.Type.EXECUTE, false, failure == null ) );
                    }
                    catch( RuntimeException | Error afterFailure ) {
                        if( failure == null )
                            throw afterFailure;
                        if( failure != afterFailure )
                            failure.addSuppressed(afterFailure);
                    }
                }
                return this;
            }
            finally {
                executing = false;
            }
        }

        private CallWork requireWork() {
            if( work == null )
                throw new CallException(Tags.PRODUCT_LABEL + "DataCall is closed");
            return work;
        }

        @Override
        public synchronized <V> void setValueCallback( Object id, Supplier<V> supplier ) throws CallException {

            final CallWork current = requireWork();

            try {

                if( id == null )
                    throw new IllegalArgumentException("'id' param is null");

                if( id instanceof Number )
                    current.set( ((Number)id).intValue(), supplier );
                else
                    current.set( id.toString(), supplier );
            }
            catch( RuntimeException th ) {
                throw new CallException( Tags.PRODUCT_LABEL + "Error on 'setValueCallback' to dataCall", th );
            }
        }

        @Override
        public synchronized <V> void setValueListener( Object id, BiConsumer< Object, V > listener ) throws CallException {

            final CallWork current = requireWork();

            try {

                if( id == null )
                    throw new IllegalArgumentException("'id' param is null");

                final Comparable parameter = id instanceof Number
                        ? ((Number)id).intValue()
                        : id.toString();

                current.outCallback( parameter, listener);
            }
            catch( RuntimeException th ) {
                throw new CallException( Tags.PRODUCT_LABEL + "Error on 'setValueListener' to dataCall", th );
            }
        }

        @Override
        public synchronized void setValueListener( BiConsumer< Object, Object > listener ) throws CallException {
            final CallWork current = requireWork();
            try {
                current.outCallback( listener );
            }
            catch( RuntimeException th ) {
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
        public synchronized ParameterMetadata getParameterMetadata( Object id ) throws CallException {

            if( id == null )
                return null;

            final CallWork current = requireWork();

            if( id instanceof Number )
                return current.getParameterMetadata( ((Number)id).intValue() );
            else
                return current.getParameterMetadata( id.toString() );
        }

        @Override
        public synchronized Iterable<ParameterMetadata<?>> getParameterIter() {
            return requireWork().getParameterIter();
        }

        @Override
        public synchronized <T> T getReturnValue() {
            return requireWork().getReturnValue();
        }

        @Override
        public synchronized Object getParameter( String parameterName ) {
            final CallWork current = requireWork();
            try {
                return current.get(parameterName);
            }
            catch( RuntimeException th ) {
                throw new RuntimeException( Tags.PRODUCT_LABEL + "Error on get '" + parameterName + "' parameter value", th );
            }
        }
        /** */
        @Override
        public synchronized Object getParameter( int parameterIndex ) {
            final CallWork current = requireWork();
            try {
                return current.get(parameterIndex);
            }
            catch( RuntimeException th ) {
                throw new RuntimeException( Tags.PRODUCT_LABEL + "Error on get '" + parameterIndex + "' parameter value", th );
            }
        }
        @Override
        public synchronized void setParameter( String parameterName, Object value ) {
            final CallWork current = requireWork();
            try {
                current.set( parameterName, value );
            }
            catch( RuntimeException th ) {
                throw new RuntimeException( Tags.PRODUCT_LABEL + "Error on set '" + parameterName + "' parameter value", th );
            }
        }
        /** */
        @Override
        public synchronized void setParameter( int parameterIndex, Object value ) {
            final CallWork current = requireWork();
            try {
                current.set(parameterIndex, value);
            }
            catch( RuntimeException th ) {
                throw new RuntimeException( Tags.PRODUCT_LABEL + "Error on set '" + parameterIndex + "' parameter value", th );
            }
        }

        @Override
        public synchronized boolean hasParameter( int parameterIndex ) {
            return requireWork().getParameterMetadata(parameterIndex) != null;
        }

        @Override
        public synchronized boolean hasParameter( String parameterName ) {
            return requireWork().getParameterMetadata(parameterName) != null;
        }

        /** */
        @Override
        public synchronized void close() {
            if( executing )
                throw new IllegalStateException("Cannot close DataCall during execution");
            if( closing || work == null )
                return;

            closing = true;
            try {
                Throwable failure = null;
                try {
                    dataCallListeners.fire( new DataCallEvent( this, DataCallEvent.Type.CLOSE, true ) );
                }
                catch( RuntimeException | Error th ) {
                    failure = th;
                }

                try {
                    work.close();
                    work = null;
                }
                catch( RuntimeException | Error cleanupFailure ) {
                    if( failure == null )
                        failure = cleanupFailure;
                    else if( failure != cleanupFailure )
                        failure.addSuppressed(cleanupFailure);
                }

                try {
                    dataCallListeners.fire( new DataCallEvent( this, DataCallEvent.Type.CLOSE, false ) );
                }
                catch( RuntimeException | Error afterFailure ) {
                    if( failure == null )
                        failure = afterFailure;
                    else if( failure != afterFailure )
                        failure.addSuppressed(afterFailure);
                }

                if( failure instanceof Error )
                    throw (Error)failure;
                if( failure != null )
                    throw (RuntimeException)failure;
            }
            finally {
                closing = false;
            }
        }
    }
}
