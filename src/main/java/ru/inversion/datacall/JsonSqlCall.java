package ru.inversion.datacall;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.inversion.dataset.IParameters;
import ru.inversion.dataset.IStructMapper;
import ru.inversion.dataset.ParametersByName;
import ru.inversion.meta.impl.ArrayMetaData;
import ru.inversion.meta.impl.StructMetaData;
import ru.inversion.parser.statement.LangEnum;
import ru.inversion.parser.token.Token;
import ru.inversion.tc.TaskContext;
import ru.inversion.utils.*;
import ru.inversion.utils.converter.TypeConverter;
import ru.inversion.utils.lstn.IListenerManEvent;
import ru.inversion.utils.lstn.ListenerManFactory;

import javax.json.*;
import javax.json.stream.JsonGenerator;
import java.io.StringReader;
import java.io.StringWriter;
import java.sql.Array;
import java.sql.JDBCType;
import java.sql.Struct;
import java.sql.Types;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.StreamSupport;

import static javax.persistence.ParameterMode.IN;
import static javax.persistence.ParameterMode.OUT;
import static ru.inversion.dataset.parser.SqlValueFormatter.format;

/**
 * Выполнение скрипта на сервере PostgreSQL, через jf_exec_pkg
 * <p>
 * beta ver 0.5
 */
public class JsonSqlCall implements IDataCall {

    static public Function<Integer,String> g_pgTypesMap = jdbcType -> {
        switch( jdbcType ) {
            case Types.DECIMAL :
            case Types.NUMERIC :
            //case Types.INTEGER :
            //case Types.BIGINT  :
            //case Types.SMALLINT:
                return "numeric";
            case Types.VARCHAR:
                return "varchar";
            case Types.CLOB:
                return "text";
            case Types.BLOB:
                return "bytea";
            case Types.TIMESTAMP:
                return "timestamp";
            case Types.SQLXML:
                return "xml";
            case Types.DATE:
                return "date";
            case -1024:
            case -1025:
            case -1026:
                return "varchar";
        }
        return JDBCType.valueOf(jdbcType).getName();
    };

    static String normalizeParameterName( String name )
    {
        return "p_" + Objects.requireNonNull(name, "name")
                .replace('.', '_')
                .toLowerCase(Locale.ROOT);
    }

    /**
     * Registers one name in the PostgreSQL launcher namespace.
     * Repeated occurrences of the same case-insensitive source identifier are
     * valid; distinct identifiers must never silently share a normalized name.
     *
     * @return true when the normalized name was registered for the first time
     */
    static boolean registerNormalizedParameterName(
            Map<String,String> normalizedNames,
            String normalizedName,
            String sourceName
    ) {
        final String existing = normalizedNames.putIfAbsent(
                Objects.requireNonNull(normalizedName, "normalizedName"),
                Objects.requireNonNull(sourceName, "sourceName")
        );

        if( existing == null )
            return true;

        if( existing.equalsIgnoreCase(sourceName) )
            return false;

        throw new IllegalArgumentException(
                "PostgreSQL parameter name collision: '" + existing
                        + "' and '" + sourceName
                        + "' both normalize to '" + normalizedName + "'"
        );
    }

    public class CallMaker_Json extends AbstractCallMakerBase {

        private int nCounter = 0;

        public CallMaker_Json(  ) {
        }

        /** */
        @Override
        public void accept( Token t, CallPrepareContext c ) {

            switch( t.getType() ) {
                case INDEXED_PARAMETER:
                    nIndex++;
                    nCounter++;
                    c.sp().append("? ");
                    if( c.isCommentParameters() )
                        c.append("/* ").append(nCounter).append(", :").append( t.getValue() ).append(" */ ");
                    break;
                case NAMED_PARAMETER:
                {
                    final String sourceName = t.getValue().toString();
                    final String normalizedName = normalizeParameterName(sourceName);
                    c.sp().append(':').append(normalizedName).sp();
                    c.parameterHandler().accept(0, normalizedName, sourceName);
                }
                break;
                case INTEGER:
                case DECIMAL:
                case STRING:
                    c.append( format( t.getValue() ) ).sp();
                break;
                default :
                    c.append( t );
            }
        }
    }

    static final private Logger logger = LoggerFactory.getLogger("ru.inversion.sql");
    final private IListenerManEvent<IDataCallListener, DataCallEvent> dataCallListeners = ListenerManFactory.createListenerManEvent( IDataCallListener::dataCallChanged );
    /** */
    final private CallWork storedProcWork;
    /** */
    final private ParametersValues parametersValues;
    /** */
    private String name;
    final private CallStatement callStatement;

    /** Дополнительные пользовательские свойства DataCall */
    private Map<String,Object> properties;

    /** Значения IN/INOUT параметров, вычисляемые перед каждым execute */
    final private Map<Comparable,Supplier<?>> valueCallbacks =
            new TreeMap<>(ParametersValues.ParamIdComparator);

    /** Слушатели отдельных OUT/INOUT параметров */
    final private Map<Comparable,BiConsumer<Object,Object>> valueListeners =
            new TreeMap<>(ParametersValues.ParamIdComparator);

    /** Общий слушатель OUT/INOUT параметров */
    private BiConsumer<Object,Object> valueListener;

    /** IDataCall close is terminal; statement cleanup after execute is internal. */
    private boolean closed;

    static private CallWork initStoredProc( TaskContext tc ) throws CallException {
        return CallBuilder.NEW()
                   .sql("{call jf_exec.execute( :plSql, :rsltInf)}").lang(LangEnum.JDBC)
                   .putParameter( new ParameterMetadataBuilder().mode(IN ).sqlType(Types.VARCHAR).name("plSql").build() )
                   .putParameter( new ParameterMetadataBuilder().mode(OUT).name("rsltInf").sqlType(Types.VARCHAR).javaType(String.class).build() )
                .build()
                   .createCallWork( tc, null );
    }

    /** */
    static private ParameterMetadata<?> castParameter( TaskContext tc, ParameterMetadata<?> pm )
    {
        if( pm != null && !S.isNullOrEmpty(pm.getElementType() ) && !(pm instanceof SqlParameterMetadata) )
            return new SqlParameterMetadata( pm,tc.dialect() );
        return pm;

    }

    /** */
    JsonSqlCall( TaskContext tc, CallStatement callStatement, IParameters callBackParameters, String name ) throws CallException
    {
        this.name = name;
        this.callStatement = callStatement;
        this.parametersValues = new ParametersValues( callBackParameters );

        this.storedProcWork = initStoredProc(tc);
    }

    /** */
    String prepareJson( Map<String,String> substMap )
    {
        final StringWriter sw = new StringWriter( );

        final JsonArrayBuilder ab = Json.createArrayBuilder( );

        final String plSql = callStatement.prepareSql(new CallMaker_Json(), new TriConsumer< Integer, Comparable, Object>() {
            @Override
            public void accept( Integer integer, Comparable newName, Object oldName ) {
                registerNormalizedParameterName(
                        substMap,
                        newName.toString(),
                        oldName.toString()
                );
            }
        });

//System.out.println(plSql);
        final TaskContext tc = storedProcWork.getTaskContext( );

        Iterator<ParameterMetadata<?>> iter =
            StreamSupport.stream(callStatement.getParameterIter().spliterator(),false).sorted(new Comparator< ParameterMetadata<?> >() {
                @Override
                public int compare( ParameterMetadata p1, ParameterMetadata p2 ) {
                    return Integer.compare(
                            p2.getIdentifier().toString().length(),
                            p1.getIdentifier().toString().length()
                    );
                }
            }).map(new Function<ParameterMetadata<?>, ParameterMetadata<?>>() {
                @Override
                public ParameterMetadata<?> apply(ParameterMetadata<?> pm) {
/*
                    if( !S.isNullOrEmpty(pm.getElementType() ) )
                        return new SqlParameterMetadata( pm,tc.dialect() );
                    return pm;
*/
                   return castParameter(tc,pm);
                }
            }).iterator();

        final Map<String,String> emittedNames = new HashMap<>();

        for( ParameterMetadata<?> p : U.iterable(iter) )
        {
            final String sourceName = p.isIndexed()
                    ? Integer.toString(p.getPosition())
                    : p.getName();
            final String name = p.isIndexed()
                    ? sourceName
                    : normalizeParameterName(sourceName);

            registerNormalizedParameterName(substMap, name, sourceName);

            if( !registerNormalizedParameterName(
                    emittedNames, name, sourceName) )
                continue;

            final JsonObjectBuilder ob =
                Json.createObjectBuilder( )
                    .add( "name", name )
                    .add( "mode", p.getMode().name());

            Object value = null;

            if( p.getMode() != OUT )
            {
                value = getInputValue(p);

                if( value == null ) {
                    ob.addNull( "value" );
                }
                else
                {
                    if( p.getSQLType() != null )
                    {
                        if( p.getSQLType() == Types.STRUCT )
                        {
                            final String elementType = p.getElementType();
                            try {

                                final StructMetaData structMetaData = StructMetaData.get( tc, elementType );
                                final Struct struct = value instanceof Struct
                                        ? (Struct)value
                                        : structMetaData.getMapper((Class<Object>)value.getClass()).toStruct(tc, value);

                                ob.add( "sqlType",  "STRUCT" );
                                ob.add( "typeName", elementType );
                                ob.add( "value",    structMetaData.toJson(struct) );
                            }
                            catch( Throwable th ) {
                                throw new RuntimeException( Tags.PRODUCT_LABEL + "Error on prepare '" + elementType + "' STRUCT value", th );
                            }
                        }
                        else
                            if( p.getSQLType() == Types.ARRAY )
                            {
                                try {

                                    final ArrayMetaData arrayMetaData =
                                          ArrayMetaData.get( tc, p.getElementType() );

                                    final Array array;
                                    final boolean arrayOwned;

                                    if( value instanceof Array )
                                    {
                                        array = (Array)value;
                                        arrayOwned = false;
                                    }
                                    else
                                    {
                                        final Collection<?> col = (Collection<?>)value;
                                        final Object obj = U.firstNotNull(col);

                                        if( arrayMetaData.getBaseType() == Types.STRUCT )
                                        {
                                            if( obj == null )
                                                array = arrayMetaData.makeArray(Collections.nCopies(col.size(), null));
                                            else
                                            {
                                                final IStructMapper sm = StructMetaData.get(tc, arrayMetaData.getBaseTypeName()).getMapper(obj.getClass());

                                                final Struct sa[] = new Struct[col.size()];
                                                int i = 0;
                                                for( Object o : col )
                                                    sa[i++] = o == null ? null : sm.toStruct(tc, o);

                                                array = arrayMetaData.makeArray(Arrays.asList(sa));
                                            }
                                        }
                                        else
                                            array = arrayMetaData.makeArray(col);

                                        arrayOwned = true;
                                    }

                                    try {
                                        ob.add( "sqlType",    "ARRAY" );
                                        ob.add( "typeName",   arrayMetaData.getSqlTypeName() );
                                        ob.add( "elementType",arrayMetaData.getBaseTypeName() );
                                        ob.add( "value",      arrayMetaData.toJson(array) );
                                    }
                                    finally {
                                        if( arrayOwned )
                                            array.free();
                                    }
                                }
                                catch( Throwable th ) {
                                    throw new RuntimeException( Tags.PRODUCT_LABEL + "Error on prepare '" + p.getElementType() + "' ARRAY value", th );
                                }
                            }
                            else
                                ob.add( "value", value.toString() );
                    }
                    else
                        ob.add( "value", value.toString() );
                }
            }//end if

            if( p.getSQLType() != null )
            {
                if( p.getSQLType() == Types.STRUCT && ( p.getMode() == OUT || value == null ) )
                {
                    ob.add( "sqlType",  "STRUCT" );
                    ob.add( "typeName", p.getElementType() );
                }
                else if( p.getSQLType() == Types.ARRAY && ( p.getMode() == OUT || value == null ) )
                {
                    final ArrayMetaData arrayMetaData = ArrayMetaData.get( tc, p.getElementType() );
                    ob.add( "sqlType",     "ARRAY" );
                    ob.add( "typeName",    arrayMetaData.getSqlTypeName() );
                    ob.add( "elementType", arrayMetaData.getBaseTypeName() );
                }
                else
                {
                    ob.add( "sqlType", g_pgTypesMap.apply( p.getSQLType() ) );
                    //Для null значений, разных типов
                    if(!S.isNullOrEmpty( p.getElementType() ) )
                        ob.add( "typeName", p.getElementType() );
                }
            }
            else
            {
                if( p.getMode() != OUT )
                {
                    if( value != null )
                        ob.add( "sqlType", g_pgTypesMap.apply( Jt2St.get( value.getClass()) ) );
                    else
                        ob.addNull("sqlType");
                }
                else
                    throw new CallException( Tags.PRODUCT_LABEL + "Не описан тип OUT параметра '" + p.getName()
                                             + "' для PL/xxx выражения '" + name + "'. Необходимо задать тип данных." );
            }

            ab.add(ob);

        }//end for

        final JsonObjectBuilder obExpr = Json.createObjectBuilder();

        obExpr.add( "expression",
            Json.createObjectBuilder()
                .add( "name",    U.nvl( name, "<unnamed>" )  )
                .add( "lang",    callStatement.getLang().toString() )
                .add( "script",  plSql )
                .add( "parameters", ab )
        );

        try( JsonWriter jw = Json.createWriterFactory(
                U.toMap( JsonGenerator.PRETTY_PRINTING, true )
        ).createWriter(sw) ) {
            jw.writeObject( obExpr.build() );
        }

        final String jsonStr = sw.toString();

//        logger.debug( "json_call: " );
//        logger.debug( jsonStr       );

        return jsonStr;
    }

    /** */
    private static Comparable normalizeParameterId( Object id ) {
        if( id == null )
            throw new IllegalArgumentException("'id' param is null");
        return id instanceof Number ? ((Number)id).intValue() : id.toString();
    }

    /** */
    private Object getInputValue( ParameterMetadata<?> parameter ) {
        final Comparable id = parameter.getIdentifier();

        if( valueCallbacks.containsKey(id) ) {
            final Supplier<?> supplier = valueCallbacks.get(id);
            return supplier == null ? null : supplier.get();
        }

        return parameter.isIndexed()
                ? parametersValues.get(parameter.getPosition())
                : parametersValues.get(parameter.getName());
    }

    /** */
    private void setOutputValue( Comparable id, Object value ) {
        if( id instanceof Number )
            parametersValues.setParameter(((Number)id).intValue(), value);
        else
            parametersValues.setParameter(id.toString(), value);

        if( valueListener != null )
            valueListener.accept(id, value);

        final BiConsumer<Object,Object> listener = valueListeners.get(id);
        if( listener != null )
            listener.accept(id, value);
    }

    /** */
    private Comparable resolveOutputParameterId( String returnedName, Map<String,String> substMap ) {
        final String sourceName = substMap.getOrDefault(returnedName, returnedName);

        if( callStatement.getParameter(sourceName) != null )
            return sourceName;

        try {
            final Integer index = Integer.valueOf(sourceName);
            if( callStatement.getParameter(index) != null )
                return index;
        }
        catch( NumberFormatException ignored ) {
        }

        return sourceName;
    }

    /** */
    private static List<Object> jsonArray2Java(JsonArray a) {
        final List<Object> result = new ArrayList<>(a.size());
        for( JsonValue value : a )
             result.add( json2Java(value) );
        return result;
    }

    /** */
    public static Map<String, Object> jsonObject2Map(JsonObject o) {
        final Map<String, Object> result = new HashMap<>();
        for( Map.Entry<String, JsonValue> entry : o.entrySet() ) {
             result.put(entry.getKey(), json2Java( entry.getValue()) );
        }
        return result;
    }

    /** */
    private static Object json2Java( JsonValue v )
    {
        if( v == null )
            return null;

        switch( v.getValueType() ) {
            case STRING:
                return ((JsonString) v).getString();
            case NUMBER:
            {
                final JsonNumber jsonNumber = (JsonNumber)v;
                if( jsonNumber.isIntegral() )
                    return jsonNumber.longValue();
                else
                    return jsonNumber.bigDecimalValue();
            }
            case TRUE:
                return Boolean.TRUE;
            case FALSE:
                return Boolean.FALSE;
            case NULL:
                return null;
            case ARRAY:
                return jsonArray2Java((JsonArray )v);
            case OBJECT:
                return jsonObject2Map((JsonObject)v);
            default:
                return v;
        }
    }

    /** */
    public static Object getJsonValue( JsonObject job, String attr )
    {
        final JsonValue jv = job.get(attr);
        return json2Java( jv );
    }

    /** */
    static Object convertScalarOutValue( Object value, ParameterMetadata<?> parameter )
    {
        final Class<?> javaType = parameter.getParameterType();
        return javaType == null ? value : TypeConverter.convert( value, javaType );
    }

    /** */
    static void storeStructArrayOutValue(
            ParametersValues parametersValues,
            String parameterName,
            List values,
            IStructMapper mapper
    )
    {
        if( mapper != null )
            for( int i = 0; i < values.size(); i++ )
            {
                final Object value = values.get(i);
                values.set( i, value == null ? null : mapper.from(value) );
            }

        parametersValues.setParameter( parameterName, values );
    }

    /** Выполнение запроса */
    @Override
    public JsonSqlCall execute() throws CallException {

        if( closed )
            throw new CallException(Tags.PRODUCT_LABEL + "DataCall is closed");

        dataCallListeners.fire( new DataCallEvent( this, DataCallEvent.Type.EXECUTE, true  ) );

        String jsonSql = null;

        try {

            final Map<String,String> substMap = new HashMap<>();

            jsonSql = prepareJson(substMap);

            storedProcWork.set( "plSql", jsonSql );

            final TaskContext tc = storedProcWork.getTaskContext();
            final boolean switchAutoCommit = callStatement.isAutoCommit() && !tc.isAutoCommit();

            try {
                if( switchAutoCommit )
                    tc.setAutoCommit(true);

                storedProcWork.execute();
            }
            finally {
                if( switchAutoCommit )
                    tc.setAutoCommit(false);
            }

            String o = storedProcWork.get("rsltInf");

            logger.debug( "json_return: " );
            logger.debug( o );

            if( parametersValues != null && o != null )
            {
                final JsonObject jo;
                try( JsonReader jr = Json.createReader(new StringReader(o)) ) {
                    jo = jr.readObject();
                }

                if( jo.containsKey("parameters") && !jo.isNull("parameters") )
                {
                    final JsonArray parameters = jo.getJsonArray("parameters");
                    for ( JsonValue p : parameters )
                    {
                        JsonObject j = (JsonObject)p;
                        final Object v = getJsonValue( j, "value" );

                        final Comparable parameterId = resolveOutputParameterId(
                                j.getString("name"), substMap
                        );

                        final ParameterMetadata parameter = castParameter(
                                tc, callStatement.getParameter(parameterId)
                        );

                        if( parameter == null || parameter.getSQLType() == null || v == null )
                        {
                            setOutputValue(parameterId, v);
                        }
                        else
                        {
                            if( parameter.getSQLType() == Types.STRUCT )
                            {
                                final StructMetaData metaData
                                        = StructMetaData
                                            .get( tc, parameter.getElementType() );

                                final Struct struct = S.isString(v) ? metaData.fromJson( (String)v ) : metaData.fromMap((Map)v);

                                final Object value;

                                if( parameter.getParameterType() != null )
                                    value = metaData.map( struct, parameter.getParameterType() );
                                else
                                    value = struct;

                                setOutputValue(parameterId, value);
                            }
                            else
                                  if( parameter.getSQLType() == Types.ARRAY )
                                  {
                                      final ArrayMetaData arrayMetaData =
                                            ArrayMetaData.get( tc, parameter.getElementType() );

                                      if( arrayMetaData.getBaseType() == Types.STRUCT )
                                      {
                                          final StructMetaData metaData = StructMetaData.get( tc, arrayMetaData.getBaseTypeName() );
                                          final List list = S.isString(v)
                                                  ? metaData.fromJsonArray((String)v)
                                                  : metaData.fromMapsArray((List)v);
                                          IStructMapper mapper = null;

                                          if( parameter.getParameterType() != null )
                                              mapper = metaData.getMapper( parameter.getParameterType() );

                                          if( mapper != null )
                                              for( int i = 0; i < list.size(); i++ )
                                              {
                                                  final Object value = list.get(i);
                                                  list.set( i, value == null ? null : mapper.from(value) );
                                              }

                                          setOutputValue(parameterId, list);
                                      }
                                      else
                                      {
                                          final List<Object> list = S.isString(v) ? arrayMetaData.fromJson( (String)v ) : (List)v;

                                          if( parameter.getParameterType() != null )
                                              list.replaceAll(value -> TypeConverter.convert( value, parameter.getParameterType()) );

                                          setOutputValue(parameterId, list);
                                      }
                                  }
                                  else
                                      setOutputValue(
                                              parameterId,
                                              convertScalarOutValue(v, parameter)
                                      );
                        }
                    }
                }
            }
        }
        catch( Throwable th ) {
            throw new CallException( Tags.PRODUCT_LABEL + "Error on execute JSON work", null, jsonSql, th );
        }
        finally {
            try {
                storedProcWork.set( "plSql", null );
            }
            finally {
                dataCallListeners.fire( new DataCallEvent( this, DataCallEvent.Type.EXECUTE, false ) );
            }
        }

        return this;
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
    public void setProperty( String propertyName, Object value ) throws CallException {
        if( properties == null )
            properties = new HashMap<>();
        properties.put(propertyName, value);
    }
    @Override
    public Object getProperty( String propertyName ) throws CallException {
        return properties == null ? null : properties.get(propertyName);
    }

    @Override
    public < V > void setValueCallback( Object id, Supplier< V > supplier ) throws CallException {
        final Comparable parameterId = normalizeParameterId(id);
        if( callStatement.getParameter(parameterId) == null )
            throw new CallException( Tags.PRODUCT_LABEL + "No parameter with Id " + parameterId );
        valueCallbacks.put(parameterId, supplier);
    }

    @Override
    public < V > void setValueListener( Object id, BiConsumer< Object, V > consumer ) throws CallException {
        final Comparable parameterId = normalizeParameterId(id);
        final ParameterMetadata<?> parameter = callStatement.getParameter(parameterId);

        if( parameter == null )
            throw new CallException( Tags.PRODUCT_LABEL + "No parameter with Id " + parameterId );
        if( !parameter.isOut() )
            throw new CallException( Tags.PRODUCT_LABEL + "Parameter '" + parameterId + "' is not OUT or INOUT" );

        valueListeners.put(parameterId, (BiConsumer<Object,Object>)(BiConsumer)consumer);
    }

    @Override
    public void setValueListener( BiConsumer< Object, Object > listener ) throws CallException {
        valueListener = listener;
    }
    /** */
    @Override
    public void addDataCallListener( IDataCallListener l ) {
        dataCallListeners.addListener(l);
    }
    @Override
    public void removeDataCallListener( IDataCallListener l ) {
        dataCallListeners.removeListener(l);
    }
    /** */
    @Override
    public ParameterMetadata getParameterMetadata( Object id ) throws CallException {
        return id == null ? null : callStatement.getParameter(normalizeParameterId(id));
    }

    @Override
    public Iterable< ParameterMetadata<?> > getParameterIter() {
        return callStatement.getParameterIter();
    }

    /** */
    @Override
    public void setParameter( String parameterName, Object value ) {
        parametersValues.setParameter(parameterName, value);
    }
    /** */
    @Override
    public void setParameter( int parameterIndex, Object value ) {
        parametersValues.setParameter(parameterIndex, value);
    }
    /** */
    @Override
    public Object getParameter( String parameterName ) {
        return parametersValues.getParameter(parameterName);
    }
    /** */
    @Override
    public Object getParameter( int parameterIndex ) {
        return parametersValues.getParameter(parameterIndex);
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
                return parameter.isIndexed()
                        ? (T)parametersValues.get(parameter.getPosition())
                        : (T)parametersValues.get(parameter.getName());
        return null;
    }
    /** */
    @Override
    public void close()  {
        if( closed )
            return;

        dataCallListeners.fire(new DataCallEvent(this, DataCallEvent.Type.CLOSE, true));
        try {
            storedProcWork.close();
            closed = true;
        }
        finally {
            dataCallListeners.fire(new DataCallEvent(this, DataCallEvent.Type.CLOSE, false));
        }
    }

    public static void main( String[] args ) {
        try {

            //Logon.login( new String[] {"--ru.inversion.userid=FWFhJL0NBU1BFUkBkZXY4aQ=="});

            final  String plSQLStruct = "declare\n" +
                    "  vsesid   varchar(30):=:session;\n" +
                    "  TAB_RCF  xxi.T_TAB_INTEGER;\n" +
                    "begin\n" +
                    "  TAB_RCF:=:rcfbase;\n" +
                    "  delete from RCF where crcfsession=vsesid;\n" +
                    "  for i in 1..coalesce(array_length(TAB_RCF,1),0) by 1 LOOP\n" +
                    "    insert into RCF (cRCFsession, iRCFnum) \n" +
                    "      values(vsesid, TAB_RCF[i]);\n" +
                    "  end loop;\n" +
                    "  call XXI_CONTEXT.RcfSave();\n" +
                    "--  commit;\n" +
                    "end;";

        CallStatement st =
                CallBuilder.NEW()
                        .sql(plSQLStruct)
                        .parameter(IN, "rcfbase", Types.ARRAY, "_int", null, null, true )
                        .build();

            new JsonSqlCall( null,st, new ParametersByName() {
                @Override
                public Object getParameter( String parameterName ) {
                    //return parameterName;
                    return Arrays.asList("1","2","3");

                }
            }, "test").execute();

            }
        catch( Throwable th ) {
            th.printStackTrace();
        }

    }

}
