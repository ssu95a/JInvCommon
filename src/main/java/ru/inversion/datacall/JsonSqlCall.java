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

    /** Builds the request understood by the PostgreSQL jf_exec launcher. */
    String prepareJson( Map<String,String> substMap )
    {
        Objects.requireNonNull(substMap, "substMap");

        final String plSql = callStatement.prepareSql(
                new CallMaker_Json(),
                (index, newName, oldName) -> registerNormalizedParameterName(
                        substMap,
                        newName.toString(),
                        oldName.toString()
                )
        );

        final TaskContext tc = storedProcWork.getTaskContext();
        final JsonArrayBuilder parameters = Json.createArrayBuilder();

        for( ParameterMetadata<?> parameter : requestParameters(tc, substMap) )
            parameters.add(requestParameter(parameter, tc));

        final JsonObject request = Json.createObjectBuilder()
                .add("expression", Json.createObjectBuilder()
                        .add("name", U.nvl(name, "<unnamed>"))
                        .add("lang", callStatement.getLang().toString())
                        .add("script", plSql)
                        .add("parameters", parameters))
                .build();

        final StringWriter result = new StringWriter();
        try( JsonWriter writer = Json.createWriterFactory(
                U.toMap(JsonGenerator.PRETTY_PRINTING, true)
        ).createWriter(result) ) {
            writer.writeObject(request);
        }
        return result.toString();
    }

    /** Returns parameters in the legacy order and validates launcher names first. */
    private List<ParameterMetadata<?>> requestParameters(
            TaskContext tc,
            Map<String,String> substMap
    ) {
        final List<ParameterMetadata<?>> parameters = new ArrayList<>();
        for( ParameterMetadata<?> parameter : callStatement.getParameterIter() )
            parameters.add(castParameter(tc, parameter));

        parameters.sort((left, right) -> Integer.compare(
                right.getIdentifier().toString().length(),
                left.getIdentifier().toString().length()
        ));

        for( ParameterMetadata<?> parameter : parameters ) {
            final String sourceName = sourceParameterName(parameter);
            final String launcherName = launcherParameterName(parameter, sourceName);

            registerNormalizedParameterName(substMap, launcherName, sourceName);
        }
        return parameters;
    }

    private static String sourceParameterName( ParameterMetadata<?> parameter ) {
        return parameter.isIndexed()
                ? Integer.toString(parameter.getPosition())
                : parameter.getName();
    }

    private static String launcherParameterName(
            ParameterMetadata<?> parameter,
            String sourceName
    ) {
        return parameter.isIndexed()
                ? sourceName
                : normalizeParameterName(sourceName);
    }

    private JsonObjectBuilder requestParameter(
            ParameterMetadata<?> parameter,
            TaskContext tc
    ) {
        final String sourceName = sourceParameterName(parameter);
        final JsonObjectBuilder result = Json.createObjectBuilder()
                .add("name", launcherParameterName(parameter, sourceName))
                .add("mode", parameter.getMode().name());

        final Object value = parameter.isIn()
                ? getInputValue(parameter)
                : null;
        final Integer sqlType = parameter.getSQLType();

        if( sqlType == null )
            addUntypedParameter(result, parameter, value);
        else if( sqlType == Types.STRUCT )
            addStructParameter(result, parameter, value, tc);
        else if( sqlType == Types.ARRAY )
            addArrayParameter(result, parameter, value, tc);
        else
            addScalarParameter(result, parameter, value, sqlType);

        return result;
    }

    private void addUntypedParameter(
            JsonObjectBuilder result,
            ParameterMetadata<?> parameter,
            Object value
    ) {
        if( !parameter.isIn() )
            throw new CallException(
                    Tags.PRODUCT_LABEL + "Не описан тип OUT параметра '"
                            + parameter.getName() + "' для PL/xxx выражения '"
                            + name + "'. Необходимо задать тип данных."
            );

        addStringValue(result, value);
        if( value == null )
            result.addNull("sqlType");
        else
            result.add("sqlType", g_pgTypesMap.apply(Jt2St.get(value.getClass())));
    }

    private static void addScalarParameter(
            JsonObjectBuilder result,
            ParameterMetadata<?> parameter,
            Object value,
            int sqlType
    ) {
        if( parameter.isIn() )
            addStringValue(result, value);

        result.add("sqlType", g_pgTypesMap.apply(sqlType));
        if( !S.isNullOrEmpty(parameter.getElementType()) )
            result.add("typeName", parameter.getElementType());
    }

    private static void addStringValue(JsonObjectBuilder result, Object value) {
        if( value == null )
            result.addNull("value");
        else
            result.add("value", value.toString());
    }

    @SuppressWarnings("unchecked")
    private static void addStructParameter(
            JsonObjectBuilder result,
            ParameterMetadata<?> parameter,
            Object value,
            TaskContext tc
    ) {
        final String elementType = parameter.getElementType();
        result.add("sqlType", "STRUCT");
        result.add("typeName", elementType);

        if( !parameter.isIn() )
            return;
        if( value == null ) {
            result.addNull("value");
            return;
        }

        try {
            final StructMetaData metaData = StructMetaData.get(tc, elementType);
            final Struct struct = value instanceof Struct
                    ? (Struct)value
                    : metaData.getMapper((Class<Object>)value.getClass())
                            .toStruct(tc, value);
            result.add("value", metaData.toJson(struct));
        }
        catch( Throwable th ) {
            throw new RuntimeException(
                    Tags.PRODUCT_LABEL + "Error on prepare '" + elementType
                            + "' STRUCT value",
                    th
            );
        }
    }

    private static void addArrayParameter(
            JsonObjectBuilder result,
            ParameterMetadata<?> parameter,
            Object value,
            TaskContext tc
    ) {
        final String elementType = parameter.getElementType();
        try {
            final ArrayMetaData metaData = ArrayMetaData.get(tc, elementType);
            result.add("sqlType", "ARRAY");
            result.add("typeName", metaData.getSqlTypeName());
            result.add("elementType", metaData.getBaseTypeName());

            if( !parameter.isIn() )
                return;
            if( value == null ) {
                result.addNull("value");
                return;
            }

            final Array array;
            final boolean owned;
            if( value instanceof Array ) {
                array = (Array)value;
                owned = false;
            }
            else {
                array = createInputArray(metaData, (Collection<?>)value, tc);
                owned = true;
            }

            try {
                result.add("value", metaData.toJson(tc, array));
            }
            finally {
                if( owned )
                    array.free();
            }
        }
        catch( Throwable th ) {
            throw new RuntimeException(
                    Tags.PRODUCT_LABEL + "Error on prepare '" + elementType
                            + "' ARRAY value",
                    th
            );
        }
    }

    @SuppressWarnings("unchecked")
    private static Array createInputArray(
            ArrayMetaData metaData,
            Collection<?> values,
            TaskContext tc
    ) throws Exception {
        if( metaData.getBaseType() != Types.STRUCT )
            return metaData.makeArray(values);

        final Object first = U.firstNotNull(values);
        if( first == null )
            return metaData.makeArray(Collections.nCopies(values.size(), null));

        final IStructMapper mapper = StructMetaData
                .get(tc, metaData.getBaseTypeName())
                .getMapper(first.getClass());
        final List<Struct> structs = new ArrayList<>(values.size());
        for( Object value : values )
            structs.add(value == null ? null : mapper.toStruct(tc, value));

        return metaData.makeArray(structs);
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

    /** Applies a jf_exec response without coupling JSON decoding to JDBC execution. */
    void applyJsonResponse( String response, Map<String,String> substMap ) {
        if( response == null )
            return;

        final JsonObject root;
        try( JsonReader reader = Json.createReader(new StringReader(response)) ) {
            root = reader.readObject();
        }

        if( !root.containsKey("parameters") || root.isNull("parameters") )
            return;

        final TaskContext tc = storedProcWork.getTaskContext();
        for( JsonValue jsonValue : root.getJsonArray("parameters") )
            applyOutputParameter((JsonObject)jsonValue, substMap, tc);
    }

    private void applyOutputParameter(
            JsonObject jsonParameter,
            Map<String,String> substMap,
            TaskContext tc
    ) {
        final Comparable parameterId = resolveOutputParameterId(
                jsonParameter.getString("name"), substMap
        );
        final ParameterMetadata<?> parameter = castParameter(
                tc, callStatement.getParameter(parameterId)
        );
        final Object value = getJsonValue(jsonParameter, "value");

        setOutputValue(parameterId, convertOutputValue(value, parameter, tc));
    }

    private static Object convertOutputValue(
            Object value,
            ParameterMetadata<?> parameter,
            TaskContext tc
    ) {
        if( parameter == null || parameter.getSQLType() == null || value == null )
            return value;

        switch( parameter.getSQLType() ) {
            case Types.STRUCT:
                return convertStructOutputValue(value, parameter, tc);
            case Types.ARRAY:
                return convertArrayOutputValue(value, parameter, tc);
            default:
                return convertScalarOutValue(value, parameter);
        }
    }

    @SuppressWarnings("unchecked")
    private static Object convertStructOutputValue(
            Object value,
            ParameterMetadata<?> parameter,
            TaskContext tc
    ) {
        final StructMetaData metaData = StructMetaData.get(
                tc, parameter.getElementType()
        );
        final Struct struct = S.isString(value)
                ? metaData.fromJson(tc, (String)value)
                : metaData.fromMap(tc, (Map)value);

        return parameter.getParameterType() == null
                ? struct
                : metaData.map(struct, parameter.getParameterType());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static List<?> convertArrayOutputValue(
            Object value,
            ParameterMetadata<?> parameter,
            TaskContext tc
    ) {
        final ArrayMetaData arrayMetaData = ArrayMetaData.get(
                tc, parameter.getElementType()
        );

        if( arrayMetaData.getBaseType() == Types.STRUCT ) {
            final StructMetaData structMetaData = StructMetaData.get(
                    tc, arrayMetaData.getBaseTypeName()
            );
            final List result = S.isString(value)
                    ? structMetaData.fromJsonArray(tc, (String)value)
                    : structMetaData.fromMapsArray(tc, (List)value);

            if( parameter.getParameterType() != null ) {
                final IStructMapper mapper = structMetaData.getMapper(
                        parameter.getParameterType()
                );
                for( int i = 0; i < result.size(); i++ ) {
                    final Object item = result.get(i);
                    result.set(i, item == null ? null : mapper.from(item));
                }
            }
            return result;
        }

        final List<Object> result = S.isString(value)
                ? arrayMetaData.fromJson((String)value)
                : (List<Object>)value;
        if( parameter.getParameterType() != null )
            result.replaceAll(item -> TypeConverter.convert(
                    item, parameter.getParameterType()
            ));
        return result;
    }

    private String executeRequest( String request ) {
        storedProcWork.set("plSql", request);

        final TaskContext tc = storedProcWork.getTaskContext();
        final boolean switchAutoCommit = callStatement.isAutoCommit()
                && !tc.isAutoCommit();
        Throwable failure = null;

        try {
            if( switchAutoCommit )
                tc.setAutoCommit(true);

            storedProcWork.execute();
            return storedProcWork.get("rsltInf");
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

    /** Выполнение запроса */
    @Override
    public JsonSqlCall execute() throws CallException {

        if( closed )
            throw new CallException(Tags.PRODUCT_LABEL + "DataCall is closed");

        dataCallListeners.fire( new DataCallEvent( this, DataCallEvent.Type.EXECUTE, true  ) );

        String jsonSql = null;
        CallException failure = null;

        try {
            final Map<String,String> substMap = new HashMap<>();
            jsonSql = prepareJson(substMap);
            final String response = executeRequest(jsonSql);

            logger.debug( "json_return: " );
            logger.debug( response );

            applyJsonResponse(response, substMap);
        }
        catch( Throwable th ) {
            failure = new CallException(
                    Tags.PRODUCT_LABEL + "Error on execute JSON work",
                    null,
                    jsonSql,
                    th
            );
            throw failure;
        }
        finally {
            try {
                try {
                    storedProcWork.set("plSql", null);
                }
                catch( RuntimeException | Error cleanupFailure ) {
                    if( failure == null )
                        throw cleanupFailure;
                    failure.addSuppressed(cleanupFailure);
                }
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
