package ru.inversion.datacall;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.inversion.dataset.IParameters;
import ru.inversion.dataset.IStructMapper;
import ru.inversion.db.JInvDbException;
import ru.inversion.db.rs.RSUtils;
import ru.inversion.meta.EntityMetadataFactory;
import ru.inversion.meta.impl.ArrayMetaData;
import ru.inversion.meta.impl.StructMetaData;
import ru.inversion.property.Property;
import ru.inversion.tc.TaskContext;
import ru.inversion.utils.*;
import ru.inversion.utils.converter.TypeConverter;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonWriter;
import java.io.*;
import java.nio.file.Files;
import java.sql.*;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static javax.persistence.ParameterMode.*;
import static ru.inversion.meta.EntityMetadataFactory.getEntityMetaData;


/**
 * Класс исполнения различного хранимого кода
 * <p>
 * PL/SQL, SQL - DML, JDBC call format
 */
public class CallWork implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger("ru.inversion.sql");

    /** Значения и характеристики одного параметра */
    private class PV<T,V> {

        /** */
        final private ParameterMetadata<?> pm;

        /** Текущее значение параметра, если задано */
        private V value = (V)IParameters.NO_PARAMETER_VALUE;

        /** Состояние, через которое уведомляются слушатели OUT-значения. */
        private Property<V> outProperty;

        /** Позиции в выражении */
        final private List<Integer> posList = new ArrayList<>();

        /** */
        public PV( ParameterMetadata<?> pm, V value ) {
            this.pm    = pm;
            this.value = value;
        }

        /** */
        public PV( ParameterMetadata<?> pm ) {
            this.pm = pm;
        }

        /** */
        public V value( ) {

            if( value == IParameters.NO_PARAMETER_VALUE ) {

                if( callBackParameters != null )
                    return (V)(pm.getName() == null ? callBackParameters.getParameter( pm.getPosition() ) : callBackParameters.getParameter( pm.getName() ));

                return (V)pm.getDefaultValue();
            }

            if( value != null ) {

                if( value instanceof Supplier )
                    return ((Supplier<V>)value).get();
            }

            return value;
        }

        /** */
        public void value( V v ) {
            final Object previous = this.value;

            if( previous instanceof Consumer)
                ((Consumer)previous).accept( v );
            else
                this.value = v;

            if( outProperty != null && outProperty != previous )
                outProperty.setValue(v);
        }
        /** */
        public T id( ) {
            return pm.getName() == null ? (T)pm.getPosition() : (T)pm.getName() ;
        }
        /** */
        public ParameterMetadata pm( ) {
            return pm;
        }
        /** */
        public void add( int pos ) {
            posList.add( pos );
        }
        /** */
        public List<Integer> posList() {
            return posList;
        }
        /** Параметр реально присутствует в подготовленном JDBC-выражении. */
        private boolean isBound() {
            return !posList.isEmpty();
        }
        /** */
        public void callback( BiConsumer<Object,V> c ) {

            if( !U.in( pm.getMode(), OUT, INOUT ) )
                throw new IllegalStateException( Tags.PRODUCT_LABEL + "Parameter '" + id() + "' is not OUT or INOUT" );

            if( outProperty == null )
                outProperty = value instanceof Property ? (Property<V>)value : new Property<>(value);

            outProperty.addListener( (property, oldValue, newValue) -> c.accept(id(), newValue) );
        }
        /** */
        public boolean isIn ( ) { return pm.getMode() == IN  || pm.getMode() == INOUT; }
        /** */
        public boolean isOut( ) { return pm.getMode() == OUT || pm.getMode() == INOUT; }
    }

    /** Значения параметров */
    final Map<Comparable<?>,PV> pvMap = new TreeMap<>((Comparator)ParametersValues.ParamIdComparator);

    /** Константы SQL, заменённые на bind-параметры */
    private final List<Pair<Integer,Object>> constantList = new ArrayList<>();

    /** Параметр помеченный как return инициализируется в initCallable */
    private PV returnPv = null;

    /** Подготовленный JDBC SQL и карта позиций; вычисляются один раз. */
    private String jdbcSql;

    /** Метаданные */
    final private CallStatement callStatement;

    /** БД */
    final private TaskContext tc;

    /** JDBC Statement*/
    private PreparedStatement jdbcStatement;

    /** Закрывать объект сразу после выполнения */
    private final boolean closeAfterExecute;

    /** Значения параметров из вне */
    private IParameters callBackParameters;

    /** Признак показывающий наличие ResultSet в out параметрах */
    private boolean hasResultSet = false;

    /** Признак, что нужно выполнять "большие"-Large операции */
    private boolean executeLarge = false;

    /** Общий слушатель для OUT параметров */
    private BiConsumer<Object,Object> commonOutListener;

    /** */
    CallWork( TaskContext tc, CallStatement callStatement, IParameters callBackParameters, boolean closeAfterExecute ) {
        this.tc                = tc;
        this.callStatement     = callStatement;
        this.closeAfterExecute = closeAfterExecute;
        this.callBackParameters= callBackParameters;
    }

    /** */
    public void setCallbackParameters( IParameters parameters ) {
        this.callBackParameters = parameters;
    }
    /** */
    public IParameters getCallbackParameters( ) {
        return callBackParameters;
    }

    /** */
    public TaskContext getTaskContext() { return tc; }

    /** Регистрация слушателя на изменения OUT параметра */
    public <T> CallWork outCallback( Comparable parameter, BiConsumer<Object,T> c ) throws CallException {

        if( c != null ) {

            PV pv = pvMap.get( parameter );

            if( pv == null ) {

                final ParameterMetadata pm = getStatementParameter(parameter); // callStatement.getParameter(parameter);

                if( pm != null ) {
                    pv = new PV( pm );
                    pvMap.put( parameter, pv );
                }
                else
                    throw new CallException( Tags.PRODUCT_LABEL + "No parameter with Id " + parameter );
            }

            pv.callback(c);
        }
        return this;
    }

    /**
     * Регистрация единого слушателя для всех OUT параметров,
     * чтобы отключить - передать null
     */
    public CallWork outCallback( BiConsumer<Object,Object> c ) throws CallException {
        commonOutListener = c;
        return this;
    }

    /** */
    private Optional<PV> getOrCreateParameter( Comparable<?> parameter )
    {
        PV pv = pvMap.get( parameter );

        if( pv != null )
            return Optional.of(pv);

        final ParameterMetadata<?> pm = getStatementParameter(parameter);
        if( pm != null ) {
            pv = new PV(pm);
            pvMap.put(parameter, pv);
            return Optional.of(pv);
        }
        return Optional.empty();
    }

    /** Установка значения параметра */
    private CallWork put( Comparable<?> parameter, Object value ) throws CallException {
        final Optional<PV> result = getOrCreateParameter(parameter);
        if( result.isPresent() ) {
            result.get().value(value);
            return this;
        }

        throw new CallException( Tags.PRODUCT_LABEL + "No parameter with Id " + parameter );
    }

    /** */
    public <T> CallWork set( Comparable parameter, Supplier<T> supplier ) throws CallException {
        return put( parameter, supplier );
    }

    /** */
    public <T> CallWork set( Comparable parameter, T value ) throws CallException {
        return put( parameter, value );
    }

    /** Получение значения параметра */
    public <T> T get( Comparable parameter ) throws CallException {

        PV pv = pvMap.get( parameter );

        if( pv != null )
            return (T)pv.value();

        // специальный случай для параметра return#
        if( S.isString(parameter) )
        {
            final String s = (String)parameter;

            if( s.length() == "return#".length() )
                if( "return#".equals(parameter) || "_return".equals(parameter) )
                    return getReturnValue();
        }

        return null;
    }

    /** Приведение типов JDBC */
    private int type2code( Integer t ) {

        if( tc.isOracle() && t == Types.REF_CURSOR )
            return -10;

        // JSON_STRUCT & JSON_ARRAY
        if( t == -1024 || t == -1025 )
            return Types.VARCHAR;

        // IN_LIST
        if( t == -1026 )
            return Types.VARCHAR;

        return t;
    }

    /** */
    private Array getArrayValue( ParameterMetadata pm, Collection col ) throws CallException {
        return ArrayMetaData.createArray( getTaskContext(), pm.getElementType(), col );
    }

    /** */
    private Struct getStructValue( ParameterMetadata pm, Object value ) throws CallException {

        try {

            final IStructMapper sm
                    = getEntityMetaData( value.getClass() )
                        .getStructMapper( getTaskContext(), pm.getElementType() );

            return sm.toStruct( getTaskContext(), value );
        }
        catch( Throwable th ) {
            throw new CallException( Tags.PRODUCT_LABEL + "Error creating a parameter of type STRUCT '" + pm.getElementType() + "'", th );
        }
    }

    /** */
    private SQLXML getSqlXmlValue( ParameterMetadata pm, Object value ) throws CallException {

        SQLXML sqlXml = null;

        try {

            sqlXml = getTaskContext().getConnection().createSQLXML();

            if( value instanceof String )
                sqlXml.setString( (String)value );
            else
            if( value instanceof Reader )
            {
                Reader reader = (Reader)value;
                try( Writer w = sqlXml.setCharacterStream() ) {
                    char[] buffer = new char[4096];
                    int charsRead;
                    while((charsRead = reader.read(buffer)) != -1) {
                        w.write(buffer, 0, charsRead);
                    }
                }
            }
            else
            if( value instanceof InputStream )
            {
                InputStream is = (InputStream)value;

                try( OutputStream sqlXmlStream = sqlXml.setBinaryStream() ) {
                    // Копируем данные из inputStream в sqlXmlStream
                    U.copyStreams( is, sqlXmlStream );
                }
            }
            else
            if( value instanceof File )
            {
                try (
                    InputStream is = Files.newInputStream(((File) value).toPath());
                    OutputStream sqlXmlStream = sqlXml.setBinaryStream()
                )
                {
                    U.copyStreams( is, sqlXmlStream );
                }
            }
            else
                throw new IllegalArgumentException( Tags.PRODUCT_LABEL + "Unsupported SQLXML value type: " + value.getClass().getName() );

            return sqlXml;
        }
        catch( Throwable th ) {
            if( sqlXml != null )
            {
                try {
                    sqlXml.free();
                }
                catch( Throwable closeError ) {
                    th.addSuppressed(closeError);
                }
            }
            throw new CallException( Tags.PRODUCT_LABEL + "Error creating a parameter of type SQLXML '" + pm.getElementType() + "'", th );
        }
    }

    /**
     * pre: value is not null
     */
    private <T> Object getJsonStructValue( ParameterMetadata pm, T value ) throws CallException {

        if( pm.getElementType() != null )
        {
            final JsonObject jo = StructMetaData
                .get( getTaskContext(), pm.getElementType() )
                    .toJson( getStructValue(pm, value) );

            StringWriter sw = new StringWriter();
            try( JsonWriter writer = Json.createWriter(sw) ) {
                  writer.writeObject(jo);
            }
            return sw.toString();
        }
        else
        {
            if( value instanceof IJsonable )
                return ((IJsonable)value).toJson();

            return EntityMetadataFactory.getEntityMetaData( (Class< T >)value.getClass() ).getJSonMapper().toJson( value );
        }
    }

    /**
     * pre: value is not null
     */
    private Object getJsonArrayValue( ParameterMetadata pm, Object value )
    {
        try {

            if( pm.getElementType() == null && value instanceof IJsonable )
                return ((IJsonable)value).toJson();

            Collection col;
            try {
                col = (Collection)value;
            }
            catch( ClassCastException e ) {
                throw new IllegalStateException( Tags.PRODUCT_LABEL + "Для параметра типа JSON_ARRAY значение должно быть Collection<T>", e );
            }

            final Object o = U.firstNotNull(col);

            if( pm.getElementType() != null )
            {
                final ArrayMetaData arrayMetaData = ArrayMetaData.get( getTaskContext(), pm.getElementType() );
                final Array array;

                if( arrayMetaData.getBaseType() == Types.STRUCT )
                {
                    if( o == null )
                        array = arrayMetaData.makeArray( Collections.nCopies(col.size(), null) );
                    else
                    {
                        final IStructMapper mapper = StructMetaData.get( getTaskContext(), arrayMetaData.getBaseTypeName() ).getMapper( o.getClass() );

                        final List sol = new ArrayList(col.size());

                        col.forEach( (e)-> {
                            try {
                                sol.add( e == null ? null : mapper.toStruct( getTaskContext(), e ) );
                            } catch(SQLException ex) {
                                throw new RuntimeException(Tags.PRODUCT_LABEL + "Error on map " + e.getClass() + " to Struct", ex );
                            }
                        });

                        array = arrayMetaData.makeArray(sol);
                    }
                }
                else
                {
                    array = ArrayMetaData.createArray( getTaskContext(), pm.getElementType(), col );
                }

                return arrayToJsonAndFree(arrayMetaData, array);
            }
            else
            {
                if( o == null )
                {
                    final JsonArrayBuilder builder = Json.createArrayBuilder();
                    col.forEach( ignored -> builder.addNull() );

                    final StringWriter sw = new StringWriter();
                    try( JsonWriter writer = Json.createWriter(sw) ) {
                        writer.writeArray(builder.build());
                    }
                    return sw.toString();
                }

                return EntityMetadataFactory.getEntityMetaData( o.getClass() ).getJSonMapper().toJsonArray( col );
            }
        }
        catch( Throwable th ) {
            throw new CallException( Tags.PRODUCT_LABEL + "Error creating a parameter '" + pm.getName() + "' of type JSON_ARRAY '" + pm.getElementType() + "'", th );
        }
    }

    /** Сериализует временный ARRAY и не маскирует ошибку сериализации. */
    private String arrayToJsonAndFree( ArrayMetaData metaData, Array array ) throws SQLException
    {
        Throwable failure = null;

        try {
            return metaData.toJson(getTaskContext(), array);
        }
        catch( RuntimeException | Error th ) {
            failure = th;
            throw th;
        }
        finally
        {
            try {
                array.free();
            }
            catch( SQLException freeFailure ) {
                if( failure == null )
                    throw freeFailure;
                failure.addSuppressed(freeFailure);
            }
        }
    }


    /** Устанавливает значение IN/INOUT-параметра в JDBC statement. */
    private void setParameterValue( PreparedStatement statement, PV pv, AutoCloseableList resources ) throws CallException
    {
        final Object originalValue = pv.value();

        try {

            final Object jdbcValue = prepareInputValue( pv, originalValue, resources );
            final List<Integer> positions = pv.posList();

            for( int position : positions )
            {
                Object positionValue = jdbcValue;

                if( originalValue instanceof File && jdbcValue == originalValue )
                {
                    final FileInputStream stream = new FileInputStream((File)originalValue);
                    resources.add(stream);
                    positionValue = stream;
                }
                else if( Integer.valueOf(Types.CLOB).equals( pv.pm().getSQLType()) && originalValue instanceof String )
                {
                    positionValue = new StringReader((String)originalValue);
                }

                bindParameterValue( statement, position, Jt2St.convert(positionValue), pv.pm() );
            }
        }
        catch( Throwable th ) {
            throw new CallException( Tags.PRODUCT_LABEL + "Error on set parameter '" + pv.id() + "' value into JDBC CallableStatement", th );
        }
    }


    /** Преобразует пользовательское значение в значение, понятное JDBC. */
    private Object prepareInputValue( PV pv, Object value, AutoCloseableList resources )
    {
        if( value == null || pv.pm().getSQLType() == null )
            return value;

        final int sqlType = pv.pm().getSQLType();

        if( sqlType == Types.ARRAY || sqlType == -1026 )
        {
            if( value instanceof Array )
                return value;

            if( !(value instanceof Collection) )
                throw new IllegalStateException( Tags.PRODUCT_LABEL + "Для параметра типа ARRAY значение должно быть Collection<T>" );

            final Array array = getArrayValue( pv.pm(), (Collection)value );
            resources.add(array::free);

            return array;
        }

        if( sqlType == Types.STRUCT )
            return value instanceof Struct ? value : getStructValue(pv.pm(), value);

        if( sqlType == -1024 )
            return getJsonStructValue(pv.pm(), value);

        if( sqlType == -1025 )
            return getJsonArrayValue(pv.pm(), value);

        if( sqlType == Types.SQLXML && !(value instanceof SQLXML) )
        {
            final SQLXML sqlXml = getSqlXmlValue(pv.pm(), value);
            resources.add(sqlXml::free);
            return sqlXml;
        }

        return value;
    }


    /** Выполняет один JDBC bind с учётом типа значения и метаданных. */
    private void bindParameterValue( PreparedStatement statement, int position, Object value, ParameterMetadata<?> parameter ) throws SQLException
    {
        if( value instanceof InputStream )
            statement.setBinaryStream(position, (InputStream)value);
        else if( value instanceof Blob )
            statement.setBlob(position, (Blob)value);
        else if( value instanceof Reader )
            statement.setCharacterStream(position, (Reader)value);
        else if( value instanceof Array )
            statement.setArray(position, (Array)value);
        else if( value instanceof byte[] )
            statement.setBytes(position, (byte[])value);
        else if( value instanceof SQLXML )
            statement.setSQLXML(position, (SQLXML)value);
        else if( parameter.getSQLType() == null )
            statement.setObject(position, value);
        else if( parameter.getSQLType() == -1024 || parameter.getSQLType() == -1025 )
        {
            if( value == null )
                statement.setNull(position, Types.VARCHAR);
            else
                statement.setString(position, (String)value);
        }
        else if( value == null )
        {
            final int sqlType = type2code(parameter.getSQLType());
            if( S.isNullOrEmpty(parameter.getElementType()) )
                statement.setNull(position, sqlType);
            else
                statement.setNull( position, sqlType, parameter.getElementType() );
        }
        else
            statement.setObject( position, value, type2code(parameter.getSQLType()) );
    }

    /** */
    private Object getOutCallableParameterValue( CallableStatement cs, PV pv ) throws CallException {

        if( pv.pm().getMode() == IN )
            throw new IllegalStateException( Tags.PRODUCT_LABEL + "No 'getValue' for parameter in 'IN' mode" );

        int parameterIndex = (Integer)pv.posList().get(0);

        final Object value;

        try {

            if( Integer.valueOf(Types.ARRAY).equals(pv.pm().getSQLType()) )
                value = cs.getArray (parameterIndex);
            else
                value = cs.getObject(parameterIndex);

            return getOutParameterValue( value, pv );

        } catch (SQLException e) {
            throw new CallException(Tags.PRODUCT_LABEL + "Error on getValue from CallableStatement", e );
        }
    }

    /** Получение OUT параметра из CallableStatement после выполнения */
    private Object getOutParameterValue( Object value, PV pv ) throws CallException {

        try {

            final Object converted = convertOutParameterValue(value, pv);
            pv.value(converted);
            return converted;

        } catch( Throwable th ) {
            throw new CallException( Tags.PRODUCT_LABEL + "Error on get OUT '" + pv.id() + "' parameter value", th );
        }
    }

    /** Преобразует JDBC-значение OUT-параметра в прикладное значение. */
    private Object convertOutParameterValue( Object value, PV pv ) throws SQLException
    {
        if( value == null )
            return null;

        if( value instanceof Array )
            return convertOutArray((Array)value, pv);

        if( value instanceof Struct && pv.pm().getParameterType() != null )
        {
            final Struct struct = (Struct)value;
            final IStructMapper mapper = getEntityMetaData( pv.pm().getParameterType() ).getStructMapper( getTaskContext(), struct.getSQLTypeName() );
            return mapper.from(struct);
        }

        if( value instanceof ResultSet )
        {
            if( pv.pm().getParameterType() != null && pv.pm().getParameterType() != ResultSet.class )
                return RSUtils.createIterator( (ResultSet)value, pv.pm().getParameterType() );

            return value;
        }

        if( Integer.valueOf(-1024).equals(pv.pm().getSQLType()) )
            return convertOutJsonStruct((String)value, pv.pm());

        if( Integer.valueOf(-1025).equals(pv.pm().getSQLType()) )
            return convertOutJsonArray((String)value, pv.pm());

        return pv.pm().getParameterType() == null ? Jt2St.r_convert(value) : TypeConverter.convert( value, pv.pm().getParameterType() );
    }

    /** Читает SQL ARRAY и гарантированно освобождает объект драйвера. */
    private Object convertOutArray( Array array, PV pv ) throws SQLException {

        Throwable failure = null;

        try {

            List<Object> values = Arrays.asList( (Object[])array.getArray() );

            if( pv.pm().getParameterType() != null )
            {
                final Class<?> targetType    = pv.pm().getParameterType();
                final List<Object> converted = new ArrayList<>(values.size());

                boolean converterPrepared = false;

                for( Object item : values )
                {
                    if( !converterPrepared && item != null )
                    {
                        if( item instanceof Struct )
                            getEntityMetaData(targetType).getStructMapper( getTaskContext(),((Struct)item).getSQLTypeName());

                        converterPrepared = true;
                    }

                    converted.add( TypeConverter.convert(item, targetType) );
                }
                values = converted;
            }
            return values;
        }
        catch( SQLException | RuntimeException | Error th )
        {
            failure = th;
            throw th;
        }
        finally
        {
            try {
                array.free();
            }
            catch( SQLException freeFailure ) {
                if( failure == null )
                    throw freeFailure;
                failure.addSuppressed(freeFailure);
            }
        }
    }


    /** Преобразует JSON_STRUCT с учётом текущего TaskContext. */
    private Object convertOutJsonStruct( String json, ParameterMetadata<?> parameter )
    {
        final Class<?> javaType = parameter.getParameterType();

        if( parameter.getElementType() == null )
            return javaType == null ? json : getEntityMetaData(javaType).getJSonMapper().fromJson(json);

        final StructMetaData metaData = StructMetaData.get( getTaskContext(), parameter.getElementType() );
        final Struct struct           = metaData.fromJson( getTaskContext(), json );

        return javaType == null ? struct : metaData.getMapper(javaType).from(struct);
    }


    /** Преобразует JSON_ARRAY с учётом текущего TaskContext. */
    private Object convertOutJsonArray( String json, ParameterMetadata<?> parameter )
    {
        final Class<?> javaType = parameter.getParameterType();

        if( parameter.getElementType() == null )
            return javaType == null ? json : getEntityMetaData(javaType).getJSonMapper().fromJsonArray(json);

        final ArrayMetaData  arrayMetaData  = ArrayMetaData.get( getTaskContext(), parameter.getElementType() );
        final StructMetaData structMetaData = StructMetaData.get( getTaskContext(), arrayMetaData.getBaseTypeName() );
        final List<Struct>   structs        = structMetaData.fromJsonArray( getTaskContext(), json );

        if( javaType == null )
            return structs;

        final IStructMapper mapper = structMetaData.getMapper(javaType);

        final List<Object> result = new ArrayList<>(structs.size());

        for( Struct struct : structs )
             result.add( struct == null ? null : mapper.from(struct) );

        return result;
    }


    /** */
    private ParameterMetadata<?> getStatementParameter( Comparable<?> id ) {

        if( id == null )
            return null;

        // адаптируем только если реально есть что адаптировать
        final ParameterMetadata<?> p = callStatement.getParameter(id);

        if( p == null )
            return null;

        if( !S.isNullOrEmpty( p.getElementType() ) )
            return new SqlParameterMetadata( p, tc.dialect());

        return p;
    }


    /** */
    private void setConstantValues() throws CallException {

        try {
            for( Pair<Integer,Object> p : constantList )
                jdbcStatement.setObject( p.first, p.second );
        }
        catch( SQLException ex ) {
            throw new CallException( Tags.PRODUCT_LABEL + "Error on set constants value", ex );
        }
    }

    /** Один раз готовит SQL, позиции параметров и констант. */
    private void prepareExecutionPlan()
    {
        if( jdbcSql != null )
            return;

        constantList.clear();
        returnPv = null;

        pvMap.values().forEach(pv -> pv.posList.clear());

        String sql = callStatement.prepareJdbcSql(
                this::registerParameterPosition,
                ( position, value ) -> constantList.add(Pair.makePair(position, value))
        );

        final List<Integer> hiddenPositions = U.toList( pvMap.values().stream().filter(PV::isBound).filter(pv -> !pv.pm.isLogValue()).flatMap(pv -> pv.posList.stream()).iterator() );

        if( !hiddenPositions.isEmpty() )
        {
            final StringBuilder builder = new StringBuilder(sql);
            if( S.lastChar(sql) != '\n' )
                builder.append('\n');
            builder.append("--lhv:").append( hiddenPositions.stream().map(String::valueOf).collect(Collectors.joining(",")) );

            sql = builder.toString();
        }

        returnPv = pvMap.values().stream().filter(PV::isBound).filter(pv -> pv.isOut() && pv.pm().isReturnValue())
                .findFirst()
                .orElseGet(() -> {
                    final PV byReturn = pvMap.get("return#");
                    if( byReturn != null && byReturn.isBound() )
                        return byReturn;
                    final PV byUnderscore = pvMap.get("_return");
                    return byUnderscore != null && byUnderscore.isBound() ? byUnderscore : null;
                });

        jdbcSql = sql;
    }

    /** Регистрирует позицию bind-параметра. */
    private void registerParameterPosition( Integer position, Comparable<?> identifier )
    {
        PV parameter = pvMap.get(identifier);

        if( parameter == null )
        {
            final ParameterMetadata<?> metadata =
                    getStatementParameter(identifier);
            if( metadata == null )
                throw new CallException(
                        Tags.PRODUCT_LABEL + "No parameter with Id "
                                + identifier
                );
            parameter = new PV(metadata);
            pvMap.put((Comparable<?>)parameter.id(), parameter);
        }
        parameter.add(position);
    }


    /** Открывает statement и выполняет его первоначальную настройку. */
    private void initJdbcStatement( AutoCloseableList resources ) throws CallException
    {
        prepareExecutionPlan();

        hasResultSet = false;

        try {

            if( callStatement.isPrepared() )
                jdbcStatement = callStatement.hasOutParameters() ? tc.getConnection().prepareStatement( jdbcSql, Statement.RETURN_GENERATED_KEYS ) : tc.getConnection().prepareStatement(jdbcSql);
            else
                jdbcStatement = tc.getConnection().prepareCall(jdbcSql);

            bindInputParameters(resources);

            registerOutParameters();
        }
        catch( Throwable th ) {
            final PreparedStatement statement = jdbcStatement;
            jdbcStatement = null;
            if( statement != null ) {
                try {
                    statement.close();
                }
                catch( Throwable closeFailure ) {
                    th.addSuppressed(closeFailure);
                }
            }
            CallException.throwOnPrepareJDBCCallable(jdbcSql, th);
        }
    }


    /** Устанавливает константы и IN/INOUT-параметры в порядке SQL-позиций. */
    private void bindInputParameters( AutoCloseableList resources )
    {
        setConstantValues( );

        pvMap.values().stream()
            .filter(PV::isBound)
                .filter(PV::isIn)
                    .sorted(Comparator.comparingInt(pv -> (Integer)pv.posList().get(0)))
                        .forEach(pv -> setParameterValue( jdbcStatement, pv, resources) );
    }


    /** Регистрирует OUT/INOUT-параметры CallableStatement. */
    private void registerOutParameters()
    {
        if( !(jdbcStatement instanceof CallableStatement) )
            return;

        final CallableStatement statement = (CallableStatement)jdbcStatement;
        final boolean postgreSql = getTaskContext().isPostgreSql();

        try {

            for( PV pv : pvMap.values() )
            {
                if( !pv.isBound() || !pv.isOut() )
                    continue;

                final List<Integer> positions = pv.posList();

                for( int position : positions )
                {
                    if( !postgreSql && !S.isNullOrEmpty(pv.pm().getElementType()) )
                        statement.registerOutParameter( position, type2code(pv.pm().getSQLType()), pv.pm().getElementType() );
                    else
                        statement.registerOutParameter( position, type2code(pv.pm().getSQLType()) );
                }
            }
        }
        catch( Throwable th ) {
            throw new CallException( Tags.PRODUCT_LABEL + "Error on registerOutParameter", th );
        }
    }


    /** Готовит statement к очередному выполнению. */
    private void prepareExecution( AutoCloseableList resources ) {
        if( jdbcStatement == null )
            initJdbcStatement(resources);
        else
            bindInputParameters(resources);
    }


    /** */
    private void executeRSReturning() throws SQLException, CallException {

        jdbcStatement.executeUpdate();

        try( ResultSet rs = jdbcStatement.getGeneratedKeys() )
        {
            if( !rs.next() )
                 throw new CallException( Tags.PRODUCT_LABEL + "PG RETURNING: no row returned" );

            final ResultSetMetaData md = rs.getMetaData();
            final int columnCount = md.getColumnCount();

            for( int i = 1; i <= columnCount; i++ )
            {
                final String outPrmName  = tc.dialect().outName( U.nvl( md.getColumnLabel(i), md.getColumnName(i) ) );
                final Object outColValue = rs.getObject(i);
                getOrCreateParameter( outPrmName ).ifPresent (
                    (pv)->{

                        Object value = getOutParameterValue( outColValue, pv );

                        if( commonOutListener != null )
                            commonOutListener.accept( pv.id(), value );

                        registerDeferredResult(value);
                    }
                );

            }
        }//end try
    }

    /** Выполняет JDBC statement и гарантированно восстанавливает autoCommit. */
    private void executeJdbcStatement( boolean prepared )
    {
        final boolean switchAutoCommit = callStatement.isAutoCommit() && !tc.isAutoCommit();

        Throwable failure = null;

        try {

            if( switchAutoCommit )
                tc.setAutoCommit(true);

            if( prepared && callStatement.hasOutParameters() )
                executeRSReturning();

            else if( executeLarge )
                jdbcStatement.executeLargeUpdate();
            else
                jdbcStatement.executeUpdate();
        }
        catch( SQLException ex )
        {
            failure = new JInvDbException(ex, jdbcSql);
            throw (JInvDbException)failure;
        }
        catch( RuntimeException | Error th ) {
            failure = th;
            throw th;
        }
        finally {
            if( switchAutoCommit ) {
                try {
                    tc.setAutoCommit(false);
                }
                catch( RuntimeException | Error restoreFailure ) {
                    if( failure == null )
                        throw restoreFailure;
                    failure.addSuppressed(restoreFailure);
                }
            }
        }
    }


    /** Читает значения OUT/INOUT-параметров CallableStatement. */
    private void readCallableOutParameters()
    {
        final CallableStatement statement = (CallableStatement)jdbcStatement;

        for( PV pv : pvMap.values() )
        {
            if( !pv.isBound() || !pv.isOut() )
                continue;

            final Object value = getOutCallableParameterValue(statement, pv);

            if( commonOutListener != null )
                commonOutListener.accept(pv.id(), value);

            registerDeferredResult(value);
        }
    }


    /** Отмечает результат, из-за которого statement должен закрыться позднее. */
    private void registerDeferredResult( Object value ) {
        if(!hasResultSet && closeAfterExecute && value != null && (value instanceof ResultSet || value instanceof Iterator) )
            hasResultSet = true;
    }

    /** Выполнение запроса. */
    public CallWork execute( ) throws CallException
    {
        CallException failure = null;

        try( AutoCloseableList resources = new AutoCloseableList(false) ) {

            prepareExecution(resources);

            final boolean prepared = !(jdbcStatement instanceof CallableStatement);

            executeJdbcStatement(prepared);

            if( !prepared )
                readCallableOutParameters();

            return this;
        }
        catch( Throwable th ) {
            failure = new CallException( Tags.PRODUCT_LABEL + "Error on execute stored work", th );
            throw failure;
        }
        finally {
            Throwable cleanupFailure = null;

            if( jdbcStatement != null )
            {
                try {
                    jdbcStatement.clearParameters();
                }
                catch( Throwable th ) {
                    cleanupFailure = th;
                }
            }

            if( closeAfterExecute )
            {
                try {
                    closeJdbcStatement();
                }
                catch( Throwable th ) {
                    if( cleanupFailure == null )
                        cleanupFailure = th;
                    else
                        cleanupFailure.addSuppressed(th);
                }
            }

            if( cleanupFailure != null )
            {
                if( failure == null )
                    throw new CallException( Tags.PRODUCT_LABEL + "Error on cleanup stored work", cleanupFailure );
                failure.addSuppressed(cleanupFailure);
            }
        }
    }

    /** Закрывает statement или передаёт его под управление ResultSet. */
    private void closeJdbcStatement() throws SQLException {
        final PreparedStatement statement = jdbcStatement;
        jdbcStatement = null;

        if( statement == null )
            return;

        if( hasResultSet )
            statement.closeOnCompletion();
        else
            statement.close();
    }

    /** */
    public void close() {
        try {
            closeJdbcStatement();
        }
        catch( SQLException ex ) {
            logger.warn("Ошибка закрытия JDBC statement в CallWork", ex);
        }
    }

    /** */
    public ParameterMetadata<?> getParameterMetadata( Comparable parameter ) {
        final PV pv = this.pvMap.get(parameter);
        if( pv != null )
            return pv.pm;
        return getStatementParameter(parameter);
    }

    /** */
    public Iterable<ParameterMetadata<?>> getParameterIter() {

        Iterable<ParameterMetadata<?>> stmtIter = callStatement.getParameterIter();
        return U.iterable(
        StreamSupport.stream( stmtIter.spliterator(),false )
            .map(ParameterMetadata::getIdentifier)
                .map(id->{

                    final PV pv = pvMap.get(id);

                    if( pv != null )
                        return (ParameterMetadata<?>) pv.pm;

                    return getStatementParameter(id);

                }).iterator()
        );
        //return pvMap.values().stream().map( (pv->(ParameterMetadata<?>)pv.pm ) ).collect(Collectors.toList());
    }

    /** */
    public <T> T getReturnValue() {
        return returnPv == null ? null : (T)returnPv.value();
    }
}
