package ru.inversion.db.expr;

import ru.inversion.datacall.CallException;
import ru.inversion.datacall.IDataCall;
import ru.inversion.datacall.SQLCallBuilder;
import ru.inversion.dataset.IParameters;
import ru.inversion.dataset.ParametersByName;
import ru.inversion.db.work.DBParametersWork;
import ru.inversion.tc.TCStorage;
import ru.inversion.tc.TaskContext;
import ru.inversion.utils.*;

import javax.persistence.NamedStoredProcedureQueries;
import javax.persistence.NamedStoredProcedureQuery;
import javax.persistence.StoredProcedureParameter;
import java.net.URL;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.function.BiConsumer;

/**
 *
 *
 * @author sulimoff
 */
 public class SQLExpressionFactory {

    //static zone
    private static final SQLExpressionFactory instance = new SQLExpressionFactory( );

    public static SQLExpressionFactory INSTANCE() { return instance; }

    //Cache<String, Map<String,ExpressionMetadata>> metadataCache;

    //final Lock parseLock = new ReentrantLock(false);

    /** */
    private SQLExpressionFactory( ) {
    }

    /** */
    public DBParametersWork prepareForExecute( URL location, String name, Connection c ) throws SQLExpressionException {

        final TaskContext tc = TCStorage.INSTANCE().getByConnection(c);

        if( tc == null )
            throw new RuntimeException( Tags.PRODUCT_LABEL + "TaskContext is null" );

        try {

            return new DBParametersWork( ) {

                final private Map<String,  Object> mapNamed   = new HashMap<>();
                final private Map<Integer, Object> mapIndexed = new TreeMap<>();

                final IDataCall dataCall
                        = SQLCallBuilder.NEW(tc)
                        .name(name    )
                        .url (location)
                        .callBackParameters(this)
                     .build();

                {
                    dataCall.valueListener(( o, o2 ) -> {
                        if(S.isString(o)) {mapNamed.put(o.toString(), o2);} else {mapIndexed.put((Integer)o, o2);}
                    });
                }

                @Override
                public Object getParameter( String parameterName ) {
                    return mapNamed.get(parameterName);
                }
                @Override
                public Object getParameter( int parameterIndex ) {
                    return mapIndexed.get(parameterIndex);
                }
                @Override
                public void setParameter( String parameterName, Object value ) {
                    mapNamed.put  ( parameterName, value );
                }
                @Override
                public void setParameter( int parameterIndex, Object value ) {
                    mapIndexed.put( parameterIndex, value );
                }
                @Override
                public void execute( Connection connection ) throws SQLException {
                    try {
                        dataCall.execute();
                    } catch(CallException e) {
                        throw new SQLException( Tags.PRODUCT_LABEL + "Error on execute DataCall", e );
                    }
                }
                /** */
                @Override
                public void getParametersMap( Map<String, Object> valueMap, boolean onlyDef ) {

                    if( mapNamed.isEmpty() || valueMap == null )
                        return;

                    if( onlyDef )
                        mapNamed.forEach( (String t, Object u) -> { if( valueMap.containsKey(t) ) valueMap.put( t, u ); });
                    else
                        valueMap.putAll(mapNamed);
                }
            };
        }
        catch ( Throwable th ) {
            throw new SQLExpressionException( Tags.PRODUCT_LABEL + "Error executing a PL/SQL expression '" + S.nvl( name ) + "'",
                    "location: " + ( location == null ? "<null>" : location.toString() ),
                    null, th
            );
        }

    }

    /** */
    public void execute( URL location, String name, Connection c ) throws SQLExpressionException {
        execute( location, name, c, null, null );
    }

    /** */
    public void execute( URL location, String name, Connection c, Map<String,Object> parameters ) throws SQLExpressionException {
        execute( location, name, c, parameters, null );
    }

    /** */
    public void execute( URL location, String name, Connection c, Object[] parameters ) throws SQLExpressionException {
        execute( location, name, c, null, Arrays.asList( parameters ) );
    }

    /** */
    public void execute( URL location, String name, Connection c, List<Object> parameters ) throws SQLExpressionException {
        execute( location, name, c, null, parameters );
    }


    /** */
    public void execute( URL location, String name, Connection c, Map<String,Object> parameters, List<Object> parameters2 ) throws SQLExpressionException {

        try {

            TaskContext tc = TCStorage.INSTANCE().getByConnection(c);

            if( tc == null )
                throw new RuntimeException( Tags.PRODUCT_LABEL + "TaskContext is null" );

            final Map<String,Object> map = U.nvl( parameters,  Collections.emptyMap() );
            final List<Object>      list = U.nvl( parameters2, Collections.emptyList());

            SQLCallBuilder.NEW ( tc )
                    .name(name)
                    .url (location)
                    .callBackParameters(new IParameters() {
                        @Override
                        public Object getParameter( String parameterName ) {
                            return map.get(parameterName);
                        }
                        @Override
                        public Object getParameter( int parameterIndex ) {
                            return list.get(parameterIndex);
                        }
                    }).build()
                      .valueListener(
                          ( n, o2 ) ->
                          {
                              if( S.isString(n) ) parameters.put(n.toString(), o2); else parameters2.set((Integer)n, o2);
                          }
                      )
                .execute();

            if( !map.containsKey("return#") )
            {
                Object v = map.get("_return");
                if( v != null )
                    map.put( "return#", v );
            }

        }
        catch ( Throwable th ) {
            throw new SQLExpressionException( Tags.PRODUCT_LABEL + "Error executing a PL/SQL expression '" + S.nvl( name ) + "'",
                    "location: " + ( location == null ? "<null>" : location.toString() ),
                    null, th
            );
        }
    }

    /*
    private void
    executeParametersWork( ExpressionMetadata em, Connection c, Map<String,Object> mapParameters, List<Object> listParameters ) throws Exception {

        try( final DBParametersWork work = em.prepareForExecute( c ) ) {

            if( mapParameters != null )
                mapParameters.forEach( work::setParameter );

            if( listParameters != null ) {
                for( int i = 0; i < listParameters.size(); i++ )
                    work.setParameter( i, listParameters.get(i) );
            }

            work.execute(c);

            if( mapParameters != null )
                work.getParametersMap( mapParameters, false );

            if( listParameters != null )
                work.getParametersList( listParameters );
        }
    }

    public DBParametersWork prepareForExecute( Class annotClass, String procedureName )  throws SQLExpressionException {
        return get( annotClass, procedureName ).prepareForExecute( );
    }

    public DBParametersWork prepareForExecute( Class annotClass, String procedureName, Connection c ) throws SQLExpressionException {
        return get( annotClass, procedureName ).prepareForExecute( c );
    }
    */

    // SQLExpressionFactory.INSTANCE().execute(XXI_ActionRights.class, "ARC_USER.isUserHasAllRights", con, params);

    /** */
    public void execute( Class annotClass, String procedureName, Connection c, Map<String,Object> mapParameters ) throws SQLExpressionException {
        execute(annotClass, procedureName, c, mapParameters, null );
    }

    /** */
    public void execute( Class annotClass, String procedureName, Connection c, List<Object> listParameters ) throws SQLExpressionException {
        execute( annotClass, procedureName, c, null, listParameters );
    }

    /** */
    public void execute( Class<?> annotClass, String procedureName, Connection c, Object[] parameters ) throws SQLExpressionException
    {
        try {

            TaskContext tc = TCStorage.INSTANCE().getByConnection(c);

            if( tc == null )
                throw new RuntimeException( Tags.PRODUCT_LABEL + "TaskContext is null" );

            final List<Object> list = ( parameters == null || parameters.length == 0 ) ? null : Arrays.asList( parameters);

            if( procedureName == null )
                procedureName = S.EMPTY_STRING;

            Map<String,Integer> map = new HashMap<>();

            {
                NamedStoredProcedureQuery nspq = (NamedStoredProcedureQuery)annotClass.getAnnotation( NamedStoredProcedureQuery.class );

                if( nspq != null && !procedureName.equals( nspq.procedureName() ) )
                    nspq = null;

                if( nspq == null )
                {
                    NamedStoredProcedureQueries nspqs = (NamedStoredProcedureQueries) annotClass.getAnnotation( NamedStoredProcedureQueries.class );

                    if( nspqs != null ) {

                        for( NamedStoredProcedureQuery n : nspqs.value() ) {

                             nspq = n;

                             if( procedureName.equals( nspq.procedureName() ) )
                                 break;
                        }
                    }//end if
                }

                if( nspq == null )
                    throw new RuntimeException( Tags.PRODUCT_LABEL + "No StoredProc " + procedureName + " data in annotations " + annotClass.getName() );

                final StoredProcedureParameter[] spParams = nspq.parameters();

                if( spParams != null && spParams.length > 0 )
                {
                    int i = nspq.resultClasses() != null && nspq.resultClasses().length > 0 ? 1 : 0;

                    if( i == 1 )
                        map.put( "_return", 0 );

                    for( StoredProcedureParameter p : spParams )
                    {
                        if( S.isNotNullOrEmpty( p.name() ) )
                            map.put( p.name(), i++ );
                    }
                }
            }

            Holder<Boolean> wasReturn = new Holder<>(false);

            SQLCallBuilder.NEW ( tc )
                .name(procedureName)
                .runClass (annotClass)
                .callBackParameters(new IParameters() {
                    @Override
                    public Object getParameter( String parameterName ) {
                        Integer i = map.get(parameterName);
                         return i == null || list.size() <= i ? null : list.get(i);
                    }
                    @Override
                    public Object getParameter( int parameterIndex ) {
                        return list.size() <= parameterIndex ? null : list.get(parameterIndex);
                    }
                }).build().valueListener (
                ( n, value )
                    ->
                        {
                            if( S.isString(n) )
                            {
//                                if( "return#".equals(n) )
//                                    wasReturn.set(true);

                                Integer i = map.get(n);

                                if( i != null && i < list.size() )
                                    list.set( i, value );
                            }
                            else
                                list.set( (Integer)n, value );
                        }
            )
                .execute();

//            if( !wasReturn.get() )
//            {
//
//            }
        }
        catch ( Throwable th ) {
            throw new SQLExpressionException( Tags.PRODUCT_LABEL + "Error executing stored procedure '" + S.nvl( procedureName ) + "'",
                    "annot class: " + ( annotClass == null ? "<null>" : annotClass.getName() ),
                    null, th
            );
        }

    }

    /*
    private void execute( Class annotClass, String procedureName, Connection c, Map<String,Object> mapParameters, List<Object> listParameters ) throws SQLExpressionException {

        try {
            executeParametersWork( get( annotClass, procedureName ), c, mapParameters, listParameters );
        }
        catch ( Throwable th ) {
            throw new SQLExpressionException( Tags.PRODUCT_LABEL + "Error executing stored procedure '" + S.nvl( procedureName ) + "'",
                                              "annot class: " + ( annotClass == null ? "<null>" : annotClass.getName() ),
                                              null, th
            );
        }
    }
    */

    /** */
    private void execute(
        Class<?> annotClass,
        String procedureName,
        Connection c,
        Map<String,Object> mapParameters,
        List<Object> listParameters
    )
            throws SQLExpressionException
    {
        try {

            final TaskContext tc = TCStorage.INSTANCE().getByConnection(c);

            if( tc == null )
                throw new RuntimeException( Tags.PRODUCT_LABEL + "TaskContext is null" );

            final Map<String,Object> map = U.nvl( mapParameters,  new HashMap<>()  );
            final List<Object>      list = U.nvl( listParameters, new ArrayList<>());

            SQLCallBuilder.NEW ( tc )
                .name(procedureName)
                    .runClass (annotClass)
                    .callBackParameters(new IParameters() {
                        @Override
                        public Object getParameter( String parameterName ) {
                            return map.get(parameterName);
                        }
                        @Override
                        public Object getParameter( int parameterIndex ) {
                            return list.get(parameterIndex);
                        }
                    }).build().valueListener( new BiConsumer<Object, Object>() {
                        @Override
                        public void accept(Object n, Object value)
                        {
                            if( n == null )
                                return;

                            if( S.isString(n) )
                            {
                                String s = (String)n;
                                map.put( s, value);
                            }
                            else
                                list.set( (Integer)n, value );
                        }
                    })
                .execute();

                if( !map.containsKey("return#") )
                {
                    Object v = map.get("_return");
                    if( v != null )
                        map.put( "return#", v );
                }
        }
        catch ( Throwable th ) {
            throw new SQLExpressionException( Tags.PRODUCT_LABEL + "Error executing stored procedure '" + S.nvl( procedureName ) + "'",
                    "annot class: " + ( annotClass == null ? "<null>" : annotClass.getName() ),
                    null, th
            );
        }
    }

    /** */
    public static URL getDefaultSQLExpressionUrl( Class clazz ) {

        Objects.requireNonNull( clazz, "clazz is null" );

        String s = String.format("%s/plsql/%s.xml", clazz.getPackage().getName().replace(".", "/"), clazz.getSimpleName() );

        URL url = clazz.getClassLoader().getResource(s);

        if (url == null)
            throw new IllegalArgumentException("Can't find url resource '" + s + "'");

        return url;
    }


    /** */
    public static void execute( String exprText, Connection connection, IParameters parameters ) throws SQLExpressionException {
        execute( null, exprText, connection, parameters );
    }

    static final public String CUSTOM_FPZ_EXPR = "$custom_fpz$";

    /** */
    public static void execute( String exprId, String exprText, Connection connection, IParameters parameters ) throws SQLExpressionException {
        try {

            String urlPath;
            URL    url = null;

            if( S.isNullOrEmpty(exprId) )
                urlPath = CUSTOM_FPZ_EXPR + ":no_cache:" + UUID.randomUUID();
            else
            {
                urlPath = CUSTOM_FPZ_EXPR + ":" + exprId;
                url = MemoryURL.getUrl(urlPath);
            }

            final TaskContext tc = TCStorage.INSTANCE().getByConnection(connection);

            if( url == null )
            {
                StringBuilder sb = new StringBuilder( exprText.length() + 150 );
                sb.append("<sql_expr_list>\n<expression name=").append('"').append(CUSTOM_FPZ_EXPR).append('"')
                  .append(" lang = \"" + ( tc.isOracle() ? "PL/SQL" : "PL/pgSQL" ) + "\">\n<script><![CDATA[\n")
                  .append(exprText)
                  .append("]]>\n</script>").append("</expression></sql_expr_list>");

                url = MemoryURL.create( urlPath, sb.toString() );
            }

            SQLCallBuilder.NEW(tc)
                .callBackParameters(parameters)
                .url(url)
                .name(CUSTOM_FPZ_EXPR)
                .runByDbmsSql( tc.isOracle() )
            .build()
                .execute();
        }
        catch( Throwable th ) {
            throw new SQLExpressionException( Tags.PRODUCT_LABEL + "Error on execute text PL/SQL expression", th );
        }
    }


    static final String sql1 = "/**/\n" +
            "DECLARE\n" +
            "    cMessage  VARCHAR2 (2000):=null;\n" +
            "    vPurp     VARCHAR2 (1024):=''; \n" +
            "BEGIN\n" +
            "\n" +
            "    DBMS_OUTPUT.put_line('Имя     '|| NVL(:BLK_TRN_ATTR.CNAME,'Пусто'));\n" +
            "    DBMS_OUTPUT.put_line('Имя     '|| NVL(:BLK_TRN_ATTR.CVALUE,'Пусто'));\n" +
            "    DBMS_OUTPUT.put_line('Назн    '|| NVL(:EDIT.cTrnPurp,'Пусто'));\n" +
            "\n" +
            "    IF :BLK_TRN_ATTR.CNAME='Цель перевода' AND :PARAMETER.Stage IN ('I') THEN\n" +
            "      vPurp:=NVL(:EDIT.cTrnPurp,'Пусто');\n" +
            "      :EDIT.cTrnPurp:=vPurp||' ('||NVL(:BLK_TRN_ATTR.CNAME,'Stop')||': '||NVL(:BLK_TRN_ATTR.CVALUE,'Stop')||')';\n" +
            "--      cMessage := cMessage || CHR (10) ||'Добавить в назначение платежа \"'||NVL(:BLK_TRN_ATTR.CNAME,'')||': '||NVL(:BLK_TRN_ATTR.CVALUE,'')||'\"?';\n" +
            "    END IF;\n" +
            "--    IF cMessage IS NOT NULL THEN\n" +
            "--       cMessage := 'YES_NO_CANCEL' || SUBSTR (cMessage, 2);\n" +
            "--    END IF;\n" +
            "    DBMS_OUTPUT.put_line('Назн 2  '|| NVL(:EDIT.cTrnPurp,'Пусто'));\n" +
            "--    :o1 := cMessage;\n" +
            "   :o1 := 'UPDATE_ITEM';\n" +
            "\n" +
            "END;";


    /** */
    public static void main(String[] args) {

        try {

            //System.setProperty( "ru.inversion.plang", LangEnum.PL_pgSQL.name() );

            execute( "test", sql1, null, new ParametersByName() {
                @Override
                public Object getParameter( String parameterName ) {
                    return "#";
                }
            });

        }
        catch( Throwable th ) {
            th.printStackTrace();
        }

    }

}
