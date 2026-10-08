package ru.inversion.datacall;

import ru.inversion.dataset.IParameters;
import ru.inversion.dataset.IStructMapper;
import ru.inversion.db.JInvDbException;
import ru.inversion.db.rs.RSUtils;
import ru.inversion.meta.EntityMetadataFactory;
import ru.inversion.meta.impl.ArrayMetaData;
import ru.inversion.meta.impl.StructMetaData;
import ru.inversion.parser.token.Token;
import ru.inversion.property.Property;
import ru.inversion.tc.TaskContext;
import ru.inversion.utils.*;
import ru.inversion.utils.converter.TypeConverter;

import javax.json.Json;
import javax.json.JsonObject;
import java.io.*;
import java.sql.*;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static javax.persistence.ParameterMode.*;
import static ru.inversion.meta.EntityMetadataFactory.getEntityMetaData;
import static ru.inversion.parser.statement.PrepareModeEnum.QUERY;


/**
 * Класс исполнения различного хранимого кода
 * <p>
 * PL/SQL, SQL - DML, JDBC call format
 */
public class CallWork implements AutoCloseable {

    /**
     * Все параметры - именованные, индексированные - храним в одной мапе
     * поэтому Comparator учитывает тип при сравнении
     * именованные - регистронезависимые
     */
    static final private Comparator<Comparable> ParamIdComparator = ( o1, o2 ) -> {

        if( o1.getClass() == o2.getClass() ) {

            if( o1.getClass() == String.class )
                return ( (String)o1).compareToIgnoreCase( (String)o2 );

            return o1.compareTo(o2);
        }

        if( o1.getClass() == String.class )
            return 1;

        return -1;
    };

    /** Значения и характеристики одного параметра */
    private class PV<T,V> {

        /** */
        final private ParameterMetadata<?> pm;

        /** Текущее значение параметра, если задано */
        private V value = (V)IParameters.NO_PARAMETER_VALUE;

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
            if( this.value instanceof Consumer)
                ((Consumer)value).accept( v );
            else
                this.value = v;
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
        /** */
        public void callback( BiConsumer<Object,V> c ) {

            if( !U.in( pm.getMode(), OUT, INOUT ) )
                throw new IllegalStateException( Tags.PRODUCT_LABEL + "Parameter '" + id() + "' is not OUT or INOUT" );

            Property<V> p;

            if( value instanceof Property )
                p = (Property)value;
            else {
                p = new Property<>(value);
                value = (V)p;
            }

            p.addListener( ( property, oldValue, newValue ) -> c.accept( id(), newValue ) );
        }
        /** */
        public boolean isIn ( ) { return pm.getMode() == IN  || pm.getMode() == INOUT; }
        /** */
        public boolean isOut( ) { return pm.getMode() == OUT || pm.getMode() == INOUT; }
    }

    /** Значения параметров */
    final Map<Comparable<?>,PV> pvMap = new TreeMap<>(ParamIdComparator);

    /** Параметр помеченный как return
     *  инициализируется в initCallable
     * */
    private PV returnPv = null;

    /** Признак, что statement был уже создан
     *  и не нужно второй раз настраивать параметры */
    private boolean wasCreated = false;

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
    private Optional<PV> getOrCreateParameter( Comparable<?> parameter, Object value )
    {
        PV pv = pvMap.get( parameter );

        if( pv != null )
        {
            pv.value(value);
            return Optional.of(pv);
        }
        else {

            final ParameterMetadata<?> pm = getStatementParameter(parameter);

            if( pm != null )
            {
                pv = new PV(pm, value );
                pvMap.put( parameter, pv );
                return Optional.of(pv);
            }
        }
        return Optional.empty();
    }

    /** Установка значения параметра */
    private CallWork put( Comparable<?> parameter, Object value ) throws CallException {

//        PV pv = pvMap.get( parameter );
//
//        if( pv != null )
//            pv.value( value );
//        else {
//
//            final ParameterMetadata pm = getStatementParameter(parameter);
//
//            if( pm != null )
//                pvMap.put( parameter, new PV( pm, value ) );
//            else
//                throw new CallException( Tags.PRODUCT_LABEL + "No parameter with Id " + parameter );
//        }
        if( getOrCreateParameter( parameter, value ).isPresent() )
            return this;

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
    private Object getArrayValue( ParameterMetadata pm, Collection col ) throws CallException {
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

        try {

            final SQLXML sqlXml = getTaskContext().getConnection().createSQLXML();

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
            return sqlXml;
        }
        catch( Throwable th ) {
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
            Json.createWriter(sw).writeObject(jo);
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
            if( o == null )
                return null;

            if( pm.getElementType() != null )
            {
                final ArrayMetaData arrayMetaData = ArrayMetaData.get( getTaskContext(), pm.getElementType() );
                final Array array;

                if( arrayMetaData.getBaseType() == Types.STRUCT )
                {
                    final IStructMapper mapper = StructMetaData.get( getTaskContext(), arrayMetaData.getBaseTypeName() ).getMapper( o.getClass() );

                    final List sol = new ArrayList(col.size());

                    col.forEach( (e)-> {
                        try {
                            sol.add( mapper.toStruct( getTaskContext(), e ) );
                        } catch(SQLException ex) {
                            throw new RuntimeException(Tags.PRODUCT_LABEL + "Error on map " + e.getClass() + " to Struct", ex );
                        }
                    });

                    array = arrayMetaData.makeArray(sol);
                }
                else
                {
                    array = ArrayMetaData.createArray( getTaskContext(), pm.getElementType(), col );
                }

                return arrayMetaData.toJson(array);
            }
            else
            {
                return EntityMetadataFactory.getEntityMetaData( o.getClass() ).getJSonMapper().toJsonArray( col );
            }
        }
        catch( Throwable th ) {
            throw new CallException( Tags.PRODUCT_LABEL + "Error creating a parameter '" + pm.getName() + "' of type JSON_ARRAY '" + pm.getElementType() + "'", th );
        }
    }

    /** Устанавливает значение параметра в CallableStatement */
    private void setParameterValue( PreparedStatement cs, PV pv ) throws CallException {

        Object originalValue = pv.value();

        Object handleValue   = originalValue;

        try( AutoCloseableList acList = new AutoCloseableList() )
        {
            if( originalValue != null )
            {
                if( pv.pm().getSQLType() != null )
                {
                    if( pv.pm().getSQLType() == Types.ARRAY || pv.pm().getSQLType() == -1026 )
                    {
                        if( originalValue instanceof Array ) {
                            ;
                        }
                        else
                        {
                            try {
                                Collection col = (Collection)originalValue;
                                handleValue = getArrayValue( pv.pm(), col );
                            }
                            catch( ClassCastException e ) {
                                throw new IllegalStateException( Tags.PRODUCT_LABEL + "Для параметра типа ARRAY значение должно быть Collection<T>", e );
                            }
                        }
                    }
                    else if( pv.pm().getSQLType() == Types.STRUCT )
                    {
                        if( originalValue instanceof Struct ) {
                            ;
                        }
                        else
                        {
                            handleValue = getStructValue( pv.pm, originalValue );
                        }
                    }
                    else if( pv.pm().getSQLType() == -1024 ) // JSON_STRUCT
                    {
                        handleValue = getJsonStructValue( pv.pm, originalValue );
                    }
                    else if( pv.pm().getSQLType() == -1025 ) // JSON_ARRAY
                    {
                        handleValue = getJsonArrayValue( pv.pm, originalValue );
                    }
                    else if( pv.pm().getSQLType() == Types.CLOB )
                    {
                        if( originalValue instanceof String )
                            handleValue = new StringReader( (String)originalValue );
                    }
                    else if( pv.pm().getSQLType() == Types.SQLXML )
                    {
                        handleValue = getSqlXmlValue( pv.pm, originalValue );
                    }
                }

                if( originalValue instanceof File )
                {
                    FileInputStream is = new FileInputStream( (File)originalValue );
                    acList.add(is);
                    handleValue = is;
                }
            }

            final Object v = Jt2St.convert(handleValue);

            final List<Integer> list = pv.posList();

            for( int idx : list )
            {
                /*
                if( v == null && pv.pm().getSQLType() != null )
                {
                    if( S.isNullOrEmpty( pv.pm().getElementType() ) )
                        cs.setNull( idx, pv.pm().getSQLType() );
                    else
                        cs.setNull( idx, pv.pm().getSQLType(), pv.pm().getElementType() );
                }
                else
                */
                if( v instanceof InputStream )
                {
                    InputStream is = (InputStream)v;
                    cs.setBinaryStream( idx, is );
                }
                else if( v instanceof Blob )
                {
                    Blob b = (Blob)v;
                    cs.setBlob( idx, b );
                }
                else if( v instanceof Reader )
                {
                    Reader r = (Reader)v;
                    cs.setCharacterStream( idx, r );
                }
                else if( v instanceof Array)
                {
                    Array r = (Array)v;
                    cs.setArray( idx, r );
                }
                else if( v instanceof byte[])
                {
                    byte[] r = (byte[])v;
                    cs.setBytes( idx, r );
                }
                else if( v instanceof SQLXML)
                {
                    SQLXML r = (SQLXML)v;
                    cs.setSQLXML( idx, r );
                }
                else
                {
                    if( pv.pm().getSQLType() == null )
                        cs.setObject( idx, v );
                    else
                    {
                        // Для JSON_STRUCT и JSON_ARRAY
                        if( pv.pm().getSQLType() == -1024 || pv.pm().getSQLType() == -1025 )
                        {
                            if( v == null )
                                cs.setNull  ( idx, Types.VARCHAR );
                            else
                                cs.setString( idx, (String)v );
                        }
                        else
                        {
                            if( v == null )
                                cs.setNull  ( idx,    type2code( pv.pm().getSQLType() ), pv.pm().getElementType() );
                            else
                                cs.setObject( idx, v, type2code( pv.pm().getSQLType() ) );
                        }
                    }
                }
            }//end for
        }
        catch( Throwable th ) {
            throw new CallException( Tags.PRODUCT_LABEL + "Error on set parameter '" + pv.id() + "' value into JDBC CallableStatement", th );
        }
    }

    /** */
    private Object getOutCallableParameterValue( CallableStatement cs, PV pv ) throws CallException {

        if( pv.pm().getMode() == IN )
            throw new IllegalStateException( Tags.PRODUCT_LABEL + "No 'getValue' for parameter in 'IN' mode" );

        int parameterIndex = (Integer)pv.posList().get(0);

        final Object value;

        try {

            if( pv.pm().getSQLType() == Types.ARRAY )
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

            if( value != null )
            {
                // Чтение массива
                if( value instanceof Array )
                {
                    Array a = (Array)value;

                    List<Object> list = Arrays.asList( (Object[])a.getArray() );

                    // конвертируем содержимое в JavaType, если задан
                    if( list != null && pv.pm().getParameterType() != null ) {

                        final Class toType = pv.pm().getParameterType();

                        final List newList = new ArrayList<>(list.size());

                        boolean doIt = true;

                        for( Object o : list ) {

                            if( doIt && o != null )
                            {
                                if( o instanceof Struct )
                                {
                                    getEntityMetaData( pv.pm().getParameterType() )
                                            .getStructMapper( getTaskContext(), ((Struct)o).getSQLTypeName() );
                                }

                                doIt = false;
                            }

                            newList.add( TypeConverter.convert( o, toType ) );
                        }

                        list = newList;
                    }

                    a.free();

                    value = list;
                }
                else if( value instanceof Struct && pv.pm().getParameterType() != null )
                {
                    final Struct struct = (Struct)value;

                    final IStructMapper structMapper
                            = getEntityMetaData( pv.pm().getParameterType() )
                            .getStructMapper( getTaskContext(), struct.getSQLTypeName() );

                    value =  structMapper.from( value );
                }
                else if( value instanceof ResultSet )
                {
                    if( pv.pm().getParameterType() != null && pv.pm().getParameterType() != ResultSet.class )
                    {
                        final ResultSet rs = (ResultSet)value;
                        value = RSUtils.createIterator( rs, pv.pm().getParameterType() );
                    }
                }
                else if( pv.pm.getSQLType() == -1024 ) // JSON_STRUCT
                {
                    final String s = (String)value;

                    if( pv.pm.getElementType() == null )
                    {
                        value = getEntityMetaData( pv.pm().getParameterType() ).getJSonMapper().fromJson(s);
                    }
                    else //через Struct
                    {
                        final StructMetaData smd = StructMetaData.get( getTaskContext(), pv.pm.getElementType() );
                        final Struct struct = smd.fromJson(s);
                        value = smd.getMapper(pv.pm().getParameterType()).from(struct);
                    }
                }
                else if( pv.pm.getSQLType() == -1025 ) // JSON_ARRAY
                {
                    final String s = (String)value;

                    if( pv.pm.getElementType() == null )
                    {
                        value = getEntityMetaData( pv.pm().getParameterType() ).getJSonMapper().fromJsonArray(s);
                    }
                    else //через Struct
                    {
                        final ArrayMetaData  amd = ArrayMetaData.get( getTaskContext(), pv.pm.getElementType() );
                        final StructMetaData smd = StructMetaData.get( getTaskContext(), amd.getBaseTypeName() );
                        final IStructMapper    m = smd.getMapper(pv.pm().getParameterType());

                        final List<Struct> ls = smd.fromJsonArray(s);

                        final List lo = new ArrayList( ls.size() );
                        ls.forEach( v->lo.add(m.from(v)) );

                        value = lo;
                    }
                }
                else
                {
                    if( pv.pm().getParameterType() != null )
                        value = TypeConverter.convert( value, pv.pm().getParameterType() );
                    else
                        value = Jt2St.r_convert( value );
                }
            }

            pv.value( value );

            return value;

        } catch( Throwable th ) {
            throw new CallException( Tags.PRODUCT_LABEL + "Error on get OUT '" + pv.id() + "' parameter value", th );
        }
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
    private void initJdbcStatement( ) throws CallException {

        String sql = null;

        try {

            final List<Pair<Integer,Object>> constantList = new LinkedList<>();

            final TriConsumer<Integer,Comparable,Object> handler;

            if( wasCreated )
                handler = ( pos, id, value ) -> {
                    if( id instanceof Token.TypeEnum )
                        constantList.add( Pair.makePair( pos, value ) );
                };
            else
                handler = ( pos, id, value ) -> {

                    if( id instanceof Integer ) {

                        PV<Integer,?> pv = pvMap.get(id);

                        if( pv == null ) {
                            final ParameterMetadata pm = getStatementParameter(id); //callStatement.getParameter((Integer)id);
                            pv = new PV( pm );
                            pvMap.put( pv.id(), pv );
                        }
                        pv.add( pos );
                    }

                    else if( id instanceof String ) {

                        PV<String,?> pv = pvMap.get(id);

                        if( pv == null ) {
                            final ParameterMetadata pm = getStatementParameter(id); //callStatement.getParameter( (String)id );
                            pv = new PV( pm );
                            pvMap.put( pv.id(), pv );
                        }

                        pv.add( pos );
                    }
                    else if( id instanceof Token.TypeEnum ) {
                        constantList.add( Pair.makePair(pos,value) );
                    }
                };

            sql = this.callStatement.prepareSql( AbstractCallMakerBase.createPLSqlMaker(QUERY), handler );

            // Добавляем список скрытых значений параметров
            {
                final List<Integer> hList = U.toList(pvMap.values().stream().filter(( pv ) -> !pv.pm.isLogValue()).<Integer>flatMap(( pv ) -> pv.posList.stream()).iterator());

                if( !hList.isEmpty() )
                {
                    // StringBuilder sb = new StringBuilder("\n--lhv:");
                    final StringBuilder sb = new StringBuilder(sql);

                    if( S.lastChar(sql) != '\n' )
                        sb.append('\n');

                    sb.append( "--lhv:" ).append (
                            hList.stream().map((i)->Integer.toString(i)).collect( Collectors.joining(",") )
                    );

                    sql = sb.toString();
                }
            }

            try {

                if( callStatement.isPrepared() )
                {
                    if( callStatement.hasOutParameters() )
                        jdbcStatement = tc.getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                    else
                        jdbcStatement = tc.getConnection().prepareStatement(sql);
                }
                else
                    jdbcStatement = tc.getConnection().prepareCall(sql);

            } catch( SQLException ex ) {
                throw new CallException( Tags.PRODUCT_LABEL + "Error on jdbc call 'callableStatement.prepareXXX'", ex );
            }

            // Устанавливаем значение констант, если они есть
            // делаем один раз и забываем
            try {

                for( Pair<Integer,Object> p : constantList )
                    jdbcStatement.setObject( p.first, p.second );

            } catch(SQLException ex) {
                throw new CallException( Tags.PRODUCT_LABEL + "Error on set constants value", ex );
            }

            // Устанавливаем IN параметры //
            for( PV pv : U.iterable( pvMap.values().stream().filter(PV::isIn).sorted( new Comparator<PV>() {
                @Override
                public int compare( PV o1, PV o2 ) {
                    return Integer.compare( (Integer)o1.posList.get(0), (Integer)o2.posList.get(0) );
                }
            }).iterator() )
            )
            {
                setParameterValue( jdbcStatement, pv );
            }

            final boolean doFixTypes = getTaskContext().isPostgreSql();

            // Настраиваем OUT параметры
            if( jdbcStatement instanceof CallableStatement )
            {
                for( PV pv : pvMap.values() )
                {
                    if (!pv.isOut())
                        continue;

                    try {

                        final List<Integer> posList = pv.posList();

                        for( int pos : posList )
                        {
                            if ( /*tc.isOracle()*/ !doFixTypes && pv.pm().getElementType() != null)
                                ((CallableStatement) jdbcStatement).registerOutParameter( pos, type2code(pv.pm().getSQLType()), pv.pm().getElementType() );
                            else
                                ((CallableStatement) jdbcStatement).registerOutParameter( pos, type2code(pv.pm().getSQLType()) );
                        }//end if
                    } catch (Throwable th) {
                        throw new CallException(Tags.PRODUCT_LABEL + "Error on registerOutParameter", th);
                    }
                }//end for
            }
            else
            {
//                // если через PreparedStatement то
//                int pos = 0;
//                for( ParameterMetadata<?> pm : callStatement.getParameterIter(true) )
//                {
//                    if(!pvMap.containsKey( pm.getIdentifier()) )
//                    {
//                        PV pv = new PV(getStatementParameter(pm.getIdentifier()));
//                        pv.add( ++pos );
//                        pvMap.put(pm.getIdentifier(), pv );
//                    }
//                    else
//                        pos++;
//                }//end for
            }

            // Ищем return параметр, если есть
            final Optional<PV> optRet = pvMap.values().stream().filter(pv -> pv.isOut() && pv.pm().isReturnValue()).findFirst();

            // если не нашли, то ищем спец параметр с именем return#
            returnPv = optRet.orElseGet(() -> U.nvl(pvMap.get("return#"), pvMap.get("_return")));

            wasCreated = true;

        } catch( Throwable th ) {
            CallException.throwOnPrepareJDBCCallable( sql, th );
        }
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
                getOrCreateParameter( outPrmName, null ).ifPresent (
                    (pv)->{

                        Object value = getOutParameterValue( outColValue, pv );
                        pv.value(value);

                        if( commonOutListener != null )
                            commonOutListener.accept( pv.id(), value );

                        if( !hasResultSet && closeAfterExecute && value != null ) {
                            if( value instanceof ResultSet || value instanceof Iterator)
                                hasResultSet = true;
                        }
                    }
                );

            }
        }//end try
    }


    /** Выполнение запроса */
    public CallWork execute( ) throws CallException {

        try {

            if( jdbcStatement == null )
                initJdbcStatement( );
            else
            {
                for( PV pv : pvMap.values() ) {

                    if( !pv.isIn() )
                        continue;

                    setParameterValue( jdbcStatement, pv );
                }
            }

            boolean isPrepared = !(jdbcStatement instanceof CallableStatement);

            // Выполнение
            try {

                if( callStatement.isAutoCommit() )
                    tc.setAutoCommit(true);

                if( isPrepared )
                {
                    if( callStatement.hasOutParameters() ) {
                        executeRSReturning( );
                    }
                    else
                    {
                        long affected = executeLarge ? jdbcStatement.executeLargeUpdate() : jdbcStatement.executeUpdate();
                    }
                }
                else
                {
                    long affected = executeLarge ? jdbcStatement.executeLargeUpdate() : jdbcStatement.executeUpdate();
                }

            } catch( SQLException ex ) {

                if( callStatement.isAutoCommit() )
                    tc.setAutoCommit(false);

                throw new JInvDbException( ex, this.callStatement.prepareSql( AbstractCallMakerBase.createPLSqlMaker(QUERY), ( integer, o, o2 ) -> {} ) );
            }
            finally {
                if( callStatement.isAutoCommit() )
                    tc.setAutoCommit(false);
            }

            if( !isPrepared )
            {
                // Получение значений OUT параметров
                for( PV pv : U.<PV>iterable( U.mapIter( pvMap.values().iterator(), null, PV::isOut ) ) )
                {
                    Object v = getOutCallableParameterValue( (CallableStatement)jdbcStatement, pv );

                    // если есть глобальный слушатель на все OUT параметры
                    if( commonOutListener != null )
                        commonOutListener.accept( pv.id(), v );

                    // Если в OUT параметрах был ResultSet то не закрываем statement
                    if( !hasResultSet && closeAfterExecute && v != null ) {
                        if( v instanceof ResultSet || v instanceof Iterator )
                            hasResultSet = true;
                    }
                }//end for
            }

            jdbcStatement.clearParameters();

        }
        catch( Throwable th ) {
            throw new CallException( Tags.PRODUCT_LABEL + "Error on execute stored work", th );
        }
        finally {
            if( closeAfterExecute )
                close( );
        }

        return this;
    }

    /** */
    public void close() {

        if( jdbcStatement != null )
        {
            try {
                if( !hasResultSet ) {
                    if( !jdbcStatement.isClosed() )
                        jdbcStatement.close();
                }
                else
                    jdbcStatement.closeOnCompletion();

            } catch(SQLException e) {
                e.printStackTrace();
            }
            jdbcStatement = null;
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
