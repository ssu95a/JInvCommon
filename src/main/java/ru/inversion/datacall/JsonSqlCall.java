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
import static ru.inversion.dataset.parser.SQLParser.value2Str;

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
                return "varchar";
        }
        return JDBCType.valueOf(jdbcType).getName();
    };

    private String normalize_Name( String name, boolean p )
    {
        return p ?
           new StringBuilder(":p_").append( name.replace('.', '_').toLowerCase()).toString()
           :
           new StringBuilder("p_").append( name.replace('.', '_').toLowerCase()).toString();
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
                    //parameters.add( c.callBackParameters().getParameter(nIndex++) );
                    c.sp().append("? ");
                    if( c.isCommentParameters() )
                        c.append("/* ").append(nCounter).append(", :").append( t.getValue() ).append(" */ ");
                    break;
                case NAMED_PARAMETER:
                {
                    String newName = normalize_Name(t.getValue().toString(), true ); //new StringBuilder(":p_").append(t.getValue().toString().toLowerCase()).toString();
                    c.sp().append(newName).sp();
                    c.parameterHandler().accept(0, newName.substring(1), t.getValue().toString() );
                }
                break;
                case INTEGER:
                case DECIMAL:
                case STRING:
                    c.append( value2Str( t.getValue() ) ).sp();
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
    final private String name;
    final private CallStatement callStatement;

    static private CallWork initStoredProc( TaskContext tc ) throws CallException {

        CallWork dbms_sql_work = (CallWork)tc.getProperty("dbms_sql_work");

        if( dbms_sql_work != null )
            return dbms_sql_work;

        dbms_sql_work =

        CallBuilder.NEW()
                   .sql("{call jf_exec.execute( :plSql, :rsltInf)}").lang(LangEnum.JDBC)
                   .putParameter( new ParameterMetadataBuilder().mode(IN ).sqlType(Types.VARCHAR).name("plSql").build() )
                   .putParameter( new ParameterMetadataBuilder().mode(OUT).name("rsltInf").sqlType(Types.VARCHAR).javaType(String.class).build() )
                .build()
                   .createCallWork( tc, null );

        tc.setProperty("dbms_sql_work", dbms_sql_work );

        return dbms_sql_work;
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
    private String prepareJson( Map<String,String> substMap )
    {
        final StringWriter sw = new StringWriter( );
        final JsonWriter   jw = Json.createWriterFactory( U.toMap( JsonGenerator.PRETTY_PRINTING, true ) ).createWriter(sw);

        final JsonArrayBuilder ab = Json.createArrayBuilder( );

        final String plSql = callStatement.prepareSql(new CallMaker_Json(), new TriConsumer< Integer, Comparable, Object>() {
            @Override
            public void accept( Integer integer, Comparable newName, Object oldName ) {
                substMap.put( newName.toString(), oldName.toString() );
            }
        });

//System.out.println(plSql);
        final TaskContext tc = storedProcWork.getTaskContext( );

        Iterator<ParameterMetadata<?>> iter =
            StreamSupport.stream(callStatement.getParameterIter().spliterator(),false).sorted(new Comparator< ParameterMetadata<?> >() {
                @Override
                public int compare( ParameterMetadata p1, ParameterMetadata p2 ) {
                    return Integer.compare(p2.getName().length(), p1.getName().length());
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

        Set<String> pms = new HashSet<>();

        for( ParameterMetadata<?> p : U.iterable(iter) )
        {
            String name = p.isIndexed() ? Integer.toString( p.getPosition() ) : normalize_Name( p.getName(), false );// "p_" + p.getName().toLowerCase() )

            if( pms.contains(name) )
                continue;

            pms.add(name);

            final JsonObjectBuilder ob =
                Json.createObjectBuilder( )
                    .add( "name", name )
                    .add( "mode", p.getMode().name());

            Object value = null;

            if( p.getMode() != OUT )
            {
                value = p.isIndexed()
                        ? parametersValues.get( p.getPosition() )
                        : parametersValues.get( p.getName());

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
                                final Struct struct= structMetaData.getMapper( (Class< Object >)value.getClass()).toStruct( tc, value );

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

                                    final Collection<?> col = (Collection< ? extends Object >)value;
                                    Object obj = null;
                                    for(Object o : col) { if( o != null ) { obj = o; break; } }

                                    final Array array;

                                    if( arrayMetaData.getBaseType() == Types.STRUCT )
                                    {
                                        final IStructMapper sm = StructMetaData.get( tc, arrayMetaData.getBaseTypeName() ).getMapper(obj.getClass() );

                                        final Struct sa[] = new Struct[col.size()];
                                        int i = 0;
                                        for(Object o : col)
                                             sa[i++] = sm.toStruct( tc, o );

                                        array = arrayMetaData.makeArray( Arrays.asList(sa) );
                                    }
                                    else
                                        array = arrayMetaData.makeArray( col );

                                    ob.add( "sqlType",    "ARRAY" );
                                    ob.add( "typeName",   arrayMetaData.getSqlTypeName() );
                                    ob.add( "elementType",arrayMetaData.getBaseTypeName() );
                                    ob.add( "value",      arrayMetaData.toJson(array) );
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

        jw.writeObject( obExpr.build() );

        final String jsonStr = sw.toString();

//        logger.debug( "json_call: " );
//        logger.debug( jsonStr       );

        return jsonStr;
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

    /** Выполнение запроса */
    @Override
    public JsonSqlCall execute() throws CallException {

        dataCallListeners.fire( new DataCallEvent( this, DataCallEvent.Type.EXECUTE, true  ) );

        String jsonSql = null;

        try {

            final Map<String,String> substMap = new HashMap<>();

            jsonSql = prepareJson(substMap);

            storedProcWork.set( "plSql", jsonSql );

            try {

                if( callStatement.isAutoCommit() )
                    storedProcWork.getTaskContext().getConnection().setAutoCommit(true);

                storedProcWork.execute();
            }
            catch( Throwable th ) {

                if( callStatement.isAutoCommit() )
                    storedProcWork.getTaskContext().getConnection().setAutoCommit(false);

                throw th;
            }
            finally {

                if( callStatement.isAutoCommit() )
                    storedProcWork.getTaskContext().getConnection().setAutoCommit(false);
            }

            String o = storedProcWork.get("rsltInf");

            logger.debug( "json_return: " );
            logger.debug( o );

            if( parametersValues != null && o != null )
            {
                final TaskContext tc = storedProcWork.getTaskContext( );

                final JsonReader jr = Json.createReader(new StringReader(o));
                final JsonObject jo = jr.readObject();

                if( !jo.isNull("parameters") )
                {
                    final JsonArray parameters = jo.getJsonArray("parameters");
                    for ( JsonValue p : parameters )
                    {
                        JsonObject j = (JsonObject)p;
                        final Object v = getJsonValue( j, "value" );

                        final String ns;
                        {
                            final String n = j.getString("name" );
                            ns = substMap.getOrDefault(n,n);
                        }

                        final ParameterMetadata parameter = castParameter( tc, callStatement.getParameter(ns) );

                        if( parameter == null || parameter.getSQLType() == null )
                        {
                              parametersValues.set( ns, v );
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

                                parametersValues.set( ns, value );
                            }
                            else
                                  if( parameter.getSQLType() == Types.ARRAY )
                                  {
                                      final ArrayMetaData arrayMetaData =
                                            ArrayMetaData.get( tc, parameter.getElementType() );

                                      if( arrayMetaData.getBaseType() == Types.STRUCT )
                                      {
                                          final StructMetaData metaData = StructMetaData.get( tc, arrayMetaData.getBaseTypeName() );
                                          final List list = metaData.fromMapsArray( (List)v );

                                          if( parameter.getParameterType() != null )
                                          {
                                              final IStructMapper mapper = metaData.getMapper( parameter.getParameterType() );

                                              for( int i = 0; i < list.size(); i++ )
                                                   list.set( i, mapper.from(list.get(i) ) );

                                              parametersValues.set( ns, list );
                                          }
                                      }
                                      else
                                      {
                                          final List<Object> list = S.isString(v) ? arrayMetaData.fromJson( (String)v ) : (List)v;

                                          if( parameter.getParameterType() != null )
                                              list.replaceAll(value -> TypeConverter.convert( value, parameter.getParameterType()) );

                                          parametersValues.set( ns, list );
                                      }
                                  }
                                  else
                                      parametersValues.set( ns, TypeConverter.convert( v,parameter.getParameterType() ) );
                        }
                    }
                }
            }
        }
        catch( Throwable th ) {
            throw new CallException( Tags.PRODUCT_LABEL + "Error on execute JSON work", null, jsonSql, th );
        }
        finally {
            storedProcWork.set( "plSql", null );
            close( );
        }

        dataCallListeners.fire( new DataCallEvent( this, DataCallEvent.Type.EXECUTE, false ) );

        return this;
    }

    @Override
    public String getName() {
        return name;
    }
    @Override
    public void setName( String name ) { }
    @Override
    public void setProperty( String propertyName, Object value ) throws CallException { }
    @Override
    public Object getProperty( String propertyName ) throws CallException { return null;}

    @Override
    public < V > void setValueCallback( Object id, Supplier< V > supplier ) throws CallException {
        throw new UnsupportedOperationException("setValueCallback");
    }

    @Override
    public < V > void setValueListener( Object id, BiConsumer< Object, V > consumer ) throws CallException {
        throw new UnsupportedOperationException("setValueListener");
    }

    @Override
    public void setValueListener( BiConsumer< Object, Object > listener ) throws CallException {
        parametersValues.setValueListener(listener);
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
        return callStatement.getParameter((Comparable)id);
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
    /** */
    @Override
    public void close()  {
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
