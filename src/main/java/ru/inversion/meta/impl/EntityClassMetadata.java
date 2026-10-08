package ru.inversion.meta.impl;

import ru.inversion.dataset.IRSRowMapper;
import ru.inversion.dataset.IRowMapper;
import ru.inversion.dataset.IStructMapper;
import ru.inversion.dataset.mark.*;
import ru.inversion.db.entity.*;
import ru.inversion.meta.*;
import ru.inversion.parser.statement.LangEnum;
import ru.inversion.tc.TCStorage;
import ru.inversion.tc.TaskContext;
import ru.inversion.utils.*;

import javax.json.*;
import javax.persistence.*;
import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.io.*;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 *
 * @author ssu
 */
@Deprecated
public class EntityClassMetadata<P> implements IEntityMetaData<P> {

    final private Class<P> entityClass;
    final private String   name;
    final private String   table;

    final public Map<String, IEntityProperty<P,?>> propertiesMap;
    final public Map<String, IEntityProperty<P,?>> columnMap;
    final public List<IEntityProperty<P,?>>        idList;

    private IRowMapper<P> rowMapper = null;

    /** */
    private EntityClassMetadata (
        Class<P>                        entityClass,
        String[]                        names,
        Map<String, IEntityProperty<P,?>> propertiesMap
    )
    {
        this.entityClass  = Objects.requireNonNull( entityClass, "entityClass is null" );
        this.name         = names[1];
        this.table        = names[0];

        this.propertiesMap= propertiesMap;

        idList    = propertiesMap.values( ).stream( ).filter( IEntityProperty::isId ).collect( Collectors.toList() );
        columnMap = createColumnsMap(propertiesMap);

    }

    public static <P> Comparator<IEntityProperty<P, ?>> CMP() {
        return (c1, c2) -> {
            if( c1 == c2 )
                return 0;

            if (c1.isId() && c2.isId())
                return c1.getColumnName().compareToIgnoreCase(c2.getColumnName());

            if (c1.isId())
                return -1;

            if (c2.isId())
                return 1;

            return c1.getColumnName().compareToIgnoreCase(c2.getColumnName() );
        };
    }

    /** */
    private static <P> Map<String, IEntityProperty<P,?>> createColumnsMap( Map<String, IEntityProperty<P,?>> propertiesMap )
    {
        List<IEntityProperty<P, ?>> columnList = propertiesMap.values().stream().filter(p -> p.getColumnInfo() != null).collect(Collectors.toList());
        // ставим первыми ID поля
        columnList.sort( CMP() );

        return new ListMap<>( columnList, IEntityProperty::getColumnName, String.CASE_INSENSITIVE_ORDER, true );
    }

    /** */
    public Class<P> getEntityClass( ) {
        return entityClass;
    }

    /** */
    @Override
    public String getName( ) {
        return name;
    }

    /** */
    @Override
    public String getTableName( ) {
        return table;
    }

    /** */
    @Override
    public IEntityProperty<P,?> getProperty(String property) {

        if( S.isNullOrEmpty( property ) )
            return null;

        IEntityProperty<P,?> p = propertiesMap.get(property);
        if( p != null )
            return p;

        return columnMap.get(property);
    }

    /** */
    @Override
    public List<IEntityProperty<P,?>> getIDList() {
        return Collections.unmodifiableList( idList );
    }

    /** */
    @Override
    public Map<String, IEntityProperty<P,?>> getPropertiesMap() {
        return Collections.unmodifiableMap( propertiesMap );
    }

    @Override
    public Map<String, IEntityProperty<P,?>> getColumnsMap() {
        return Collections.unmodifiableMap( columnMap );
    }

    /** */
    @Override
    public IRowMapper<P> getRowMapper( ) {
        if( rowMapper == null )
            rowMapper = createRowMapper( );
        return rowMapper;
    }

    /** */
    @Override
    public IEntityDao<P, Serializable> getEntityDao( TaskContext tc ) {
        return new EntityDao<>(this,tc);
    }

    /** */
    @Override
    public IRSRowMapper<P> createRSRowMapper( ResultSet rs ) {
        try {
            Objects.requireNonNull( rs,"ResultSet parameter 'rs' is null");
            return new RSRowMapper<>( rs, columnMap, this );
        } catch (SQLException ex) {
           throw new EntityMetaDataException( Tags.PRODUCT_LABEL + "Error on createRSRowMapper", ex );
        }
    }

    private IRowMapper<P> createRowMapper( ) {

        return new IRowMapper< P >() {

            private WeakReference<IRSRowMapper<P>> weakRm = null;

            @Override
            public P mapRow( ResultSet rs, int rowNum ) throws SQLException {

                IRSRowMapper<P> rsMapper = null;

                if( weakRm != null ) {
                    rsMapper = weakRm.get();
                    if( rsMapper != null && rsMapper.getResultSet() != rs )
                        rsMapper = null;
                }

                if( rsMapper == null ) {
                    rsMapper = createRSRowMapper(rs);
                    weakRm = new WeakReference<>(rsMapper);
                }

                return rsMapper.mapRow( rowNum );
            }
        };
    }

    /** */
    private static void method2pd( Method m, Map<String,Object> values ) {

        if( m.isAnnotationPresent(Transient.class) )
            values.put("transient", Boolean.TRUE );

        if( m.isAnnotationPresent(Required.class) )
            values.put("required", Boolean.TRUE );

        OrderBy orderBy = m.getAnnotation( OrderBy.class );
        if( orderBy != null )
            values.put("order_by", orderBy.value() );

        if( m.isAnnotationPresent(Id.class) )
            values.put("id", true );

        Content contentAnn = m.getAnnotation(Content.class);
        if( contentAnn != null )
            values.put("content", contentAnn.type() );

        DBReturningValue rv = m.getAnnotation(DBReturningValue.class);
        if( rv != null ) {
            values.put("column.returning.insert", rv.insert() );
            values.put("column.returning.update", rv.update() );
            values.put("column.returning.sequence_value", rv.sequenceValue() );
        }

        SequenceGenerator sg = m.getAnnotation(SequenceGenerator.class);
        if( sg != null )
            values.put("sequence.db_name", S.isNullOrEmpty( sg.sequenceName() ) ? sg.name() : sg.sequenceName()  );

        EntityAttribute ea = m.getAnnotation( EntityAttribute.class );

        if( ea != null )
        {
            values.put("attribute.caseSensitiveSearchMode", ea.caseSensitiveSearchMode() );

            if( ea.decimalStripTrailingZeros() && m.getReturnType() == BigDecimal.class )
                values.put("attribute.decimalStripTrailingZeros", Boolean.TRUE );

            values.put("attribute.useInLOVPredicate", ea.useInLOVPredicate() );
        }
    }

    /** */
    private static Optional<Method> findGetter( Class  clazz, String property ) {
        final String getter1 = "get" + property, getter2 = "is" + property;
        return Arrays.asList( clazz.getMethods() )
                     .stream()
                     .filter( m -> U.in(m.getName(), getter1, getter2 ) )
                     .findFirst();
    }
    /** */
    private static Optional<Method> findSetter( Class  clazz, String property ) {
        final String setter = "set" + property;
        return Arrays.asList( clazz.getMethods() )
                     .stream()
                     .filter( m -> U.in(m.getName(), setter ) )
                     .findFirst();
    }

    /** */
    private static boolean isOkMethod( Method m ) {

        if( m == null )
            return false;

        return !U.in( m.getDeclaringClass(),
                        Object.class,
                        UMarkable.class,
                        MarkableBase.class,
                        IMarkable.class,
                        IDMarkable.class,
                        ID2Markable.class,
                        RowIDMarkable.class,
                        IIDMarkable.class,
                        IID2Markable.class,
                        IRowIDMarkable.class,
                        IUMarkable.class
        );
    }

    // for Abramov only
    private static String decapitalize(String name) {
        if (name == null || name.length() == 0) {
            return name;
        }

        //если всё капсом, то не декапитализируем
        if( name.length() > 1 && name.equals(name.toUpperCase()) )
        {
            return name;
        }
        char chars[] = name.toCharArray();
        chars[0] = Character.toLowerCase(chars[0]);
        return new String(chars);
    }

    /** */
    private static <P> IEntityProperty<P,?> findProperty(Map<String, IEntityProperty<P,?>> propertiesMap, String key) {

        if( S.isNullOrEmpty(key) )
            return null;

        IEntityProperty<P,?> p = propertiesMap.get(key);
        if( p != null )
            return p;

        for (IEntityProperty<P,?> ep : propertiesMap.values() ) {
            if (ep.getColumnInfo() != null && key.equalsIgnoreCase(ep.getColumnInfo().getName()))
                return ep;
        }

        return null;
    }

    /** */
    public static <P> EntityClassMetadata<P> loadClassMetaData( Class<P> entityClazz ) throws IntrospectionException {

        final Map<String, IEntityProperty<P,?> > propertiesMap = new LinkedHashMap<>();

        List<Method> excludeList = new ArrayList<>();
        List<Pair<IEntityProperty<P,?>,String[]>> proxyColumnList = new LinkedList<>();

        for( Method m : entityClazz.getMethods() )
        {
            if( !isOkMethod(m) )
                 continue;

            String methodName = m.getName();

            if( m.isAnnotationPresent(Column.class) ) {

                final Column c = m.getAnnotation( Column.class );

                if( S.isNullOrEmpty( c.name() ) || IMarkable.MARK_COLUMN.equals(c.name()) ) {
                    continue;
                }

                PropertyDescriptor pd = null;
                String property = null;

                if( methodName.startsWith("set") )
                {
                    property = decapitalize( methodName.substring(3) );
                }
                else
                {
                    int s = 0;

                    if( methodName.startsWith("get") )
                        s = 3;
                    else if( methodName.startsWith("is") )
                        s = 2;

                    if( s == 0 )
                        continue;

                    property = decapitalize( methodName.substring(s) );
                }

                if( propertiesMap.containsKey( property ) )
                    continue; //property already in propertiesMap

                pd = new PropertyDescriptor( property, entityClazz );

                final Map<String,Object> values = new HashMap<>();

                method2pd( m, values );

                values.put("column.name",       c.name()       );
                values.put("column.insertable", c.insertable() );
                values.put("column.updatable" , c.updatable () );
                values.put("column.nullable"  , c.nullable  () );
                values.put("column.table"     , c.table()      );
                values.put("column.unique"    , c.unique()     );

                if( U.in(m.getReturnType(), String.class, Long.class, Integer.class) && c.length() != 255 ) // hack for def value
                    values.put("column.length", c.length() );

                // select выражение для столбца
                if( !S.isNullOrEmpty( c.columnDefinition() ) )
                    values.put( "column.query_expr", c.columnDefinition() );

                // Перекрывает columnDefinition в аннотации Column
                // + без аннотации Column не обрабатываем
                if( m.isAnnotationPresent(ColumnSQLDefinition.class) )
                {
                    final ColumnSQLDefinition cDef = m.getAnnotation( ColumnSQLDefinition.class );

                    final String def = (String)values.get("column.query_expr");

                    final Function<LangEnum,String> queryExprSupplier = new Function<LangEnum, String>() {
                        private final String queryExprDef = def;
                        private final LangEnum       lang = cDef.lang();
                        private final String queryExpr    = cDef.columnDefinition();
                        @Override
                        public String apply( LangEnum l ) {
                            return l == lang ? queryExpr : queryExprDef;
                        }
                    };

                    values.put( "column.query_expr", queryExprSupplier );

                }

                // Если getter аннотирован Column и Transient,
                // то считаем property и Column единым,
                // имя property равно имени Column.
                if( values.containsKey("transient") )
                    property = c.name();

                EntityProperty<P,?> ep = new EntityProperty<>( property, c.name() , pd, values );

                propertiesMap.put( property, ep );

                ProxyColumns proxyColumns = m.getAnnotation(ProxyColumns.class);

                if( proxyColumns != null && proxyColumns.columnNames().length > 0 )
                    proxyColumnList.add( Pair.makePair( ep, proxyColumns.columnNames() ) );

            }
            else {
                if( ( methodName.startsWith("get") || methodName.startsWith("is") ) && U.notIn( methodName, "getClass", "isMark") )
                      excludeList.add(m);
            }
        }//end for

        if( !excludeList.isEmpty() ) {

            for( Method m : excludeList ) {

                if( m.getParameterCount() > 0 )
                    continue;

                String methodName = m.getName();

                int s = 0;

                if( methodName.startsWith("get") )
                    s = 3;
                else if( methodName.startsWith("is") )
                    s = 2;

                if( s == 0 )
                    continue;

                String mn = methodName.substring(s);

                String property = decapitalize( mn );

                if( propertiesMap.containsKey( property ) )
                    continue;

                final Map<String,Object> values = new HashMap<>();

                PropertyDescriptor pd = new PropertyDescriptor( property, m, findSetter(entityClazz, mn).orElse(null) );

                //if( pd.getWriteMethod() == null )
                    values.put( "transient", Boolean.TRUE );

                method2pd( m, values );

                EntityProperty<P,?> transientProperty = new EntityProperty<>( property, property, pd, values );
                propertiesMap.put( property, transientProperty );

                final ProxyFor pf = m.getAnnotation( ProxyFor.class );
                if( pf != null )
                {
                    IEntityProperty<P,?> proxyForProperty = findProperty( propertiesMap, pf.columnName() ); //propertiesMap.get( pf.columnName() );
                    if( proxyForProperty != null )
                        transientProperty.setProxyFor(proxyForProperty, pf );
                }
            }

        }//end if isEmpty

        // process proxyColumns
        for( Pair<IEntityProperty<P,?>,String[]> p : proxyColumnList ) {

             for( String s : p.second ) {

                  EntityProperty<P,?> e = (EntityProperty<P,?>)findProperty(propertiesMap, s);

                  if( e != null ) {
                      if( e.getProxyFor() == null )
                          e.setProxyFor(p.first);
                  }
             }

        }//end for

        String[] names = new String[2];

        if( entityClazz.isAnnotationPresent(Table.class) ) {
            Table t = entityClazz.getAnnotation(Table.class);
            names[0] = t.name();
        }

        if( entityClazz.isAnnotationPresent(Entity.class) ) {
            Entity t = entityClazz.getAnnotation(Entity.class);
            if( !S.isNullOrEmpty(t.name()) )
                names[1] = t.name();
        }

        return new EntityClassMetadata<>( entityClazz, names, propertiesMap );
    }

    /** */
    private static class RSRowMapper<T> implements IRSRowMapper<T> {

        final private ResultSet rs;
        final private List<Triplet<IEntityProperty<T,?>,Integer, IStructMapper<?>>> propertyList = new ArrayList<>();
        final private Class<T>  entityClass;
        //final private Function<Object,Object> ddpl = new DDpl();//(o)->o;//new DDpl();
        /** */
        private boolean isStructMap( final IEntityProperty<T,?> property )
        {
            if (
                property.getType() == Object.class
                ||
                //Struct.class.isAssignableFrom( property.getType() )
                !property.getType().isPrimitive()
            )
                return true;

            return false;
        }

        /** */
        public RSRowMapper( ResultSet rs, Map<String, IEntityProperty<T,?>> columnMap, EntityClassMetadata<T> em ) throws SQLException {

            Objects.requireNonNull( rs, Tags.PRODUCT_LABEL + "'rs' is null" );

            ResultSetMetaData metaData = rs.getMetaData();

            final int count = metaData.getColumnCount();

            final TaskContext tc = TCStorage.INSTANCE().getByConnection( rs.getStatement().getConnection() );

            for( int i = 1; i <= count; i++ ) {

                String key = metaData.getColumnLabel(i);

                if( S.isNullOrEmpty(key) )
                    key = metaData.getColumnName(i);

                // key = tc.dialect().sqlName(key);
                IEntityProperty<T,?> property = null;

                boolean exit = false;

                do {

                    exit = !exit;

                    property = columnMap.get(key);

                    if( property == null ) {
                        property = em.getProperty(key);

                        if( property == null) {
                            if( tc != null )
                                key = tc.dialect().sqlName(key);
                        }
                    }

                } while( property == null && !exit );

                if( property != null )
                {
                    IStructMapper<?> sm = null;

                    // Теперь в поле может приходить Struct
                    if( metaData.getColumnType(i) == Types.STRUCT && isStructMap( property ) )
                    {
                        sm = EntityMetadataFactory.
                            getEntityMetaData( property.getType() ).
                                getStructMapper( tc, metaData.getColumnTypeName(i) );
                    }

                    propertyList.add( new Triplet<>( property, i, sm ) );
                }
            }

            this.rs = rs;
            this.entityClass = em.getEntityClass();
        }

        @Override
        public ResultSet getResultSet() {
            return rs;
        }

        @Override
        public T mapRow( int rowNum ) throws SQLException {

            try {

                T pojo = entityClass.newInstance();

                Object value = null;

                for( Triplet<IEntityProperty<T,?>,Integer, IStructMapper<?>> p : propertyList ) {

                    try {

                        value = rs.getObject( p.second );

                        if( value == null )
                            continue;

                        if( p.third != null  )
                            p.first.invokeSetter( pojo, p.third.from( (Struct)value ) );
                        else {

                            if( value instanceof BigDecimal && p.first.isDecimalStripTrailingZeros() && ((BigDecimal)value).scale() > 0 ) {

                                BigDecimal bg = (BigDecimal)value;
                                bg = bg.stripTrailingZeros();

                                if( bg.scale() < 0 )
                                    bg = bg.setScale( 0, RoundingMode.HALF_UP);

                                value = bg;
                            }

                            p.first.invokeSetter( pojo, value );
                        }
                    }
                    catch( Throwable th  ) {
                        throw new RuntimeException( Tags.PRODUCT_LABEL + "Error getting column values '" + p.first.getColumnInfo().getName() + "' из ResultSet", th );
                    }
                }//end for

                return pojo;

            } catch( Throwable th ) {
                throw new SQLException( Tags.PRODUCT_LABEL + "Error when mapping record from the ResultSet", th );
            }
        }
    }


    static private Method getDescriptor;
    static private Method getMetaData;

    /** */
    private IStructMapper<P> createMapper( TaskContext tc, String structTypeName ) throws Exception {

        final List<Triplet<IEntityProperty,Integer,String> > list = new ArrayList<>();

        String columnName;
        int    columnType;
        String columnTypeName;

        ResultSetMetaData rs;

        if( tc.isOracle() )
        {
            Struct struct = tc.getConnection().createStruct( structTypeName, null );

            if( getDescriptor == null )
                getDescriptor = struct.getClass().getMethod( "getDescriptor" );

            Object d  = getDescriptor.invoke(struct);

            if( getMetaData == null )
                getMetaData = d.getClass().getMethod( "getMetaData" );

            rs = (ResultSetMetaData)getMetaData.invoke( d );

        }
        else
        {
            rs = StructMetaData.get(tc, structTypeName).asResultSetMetaData();
        }

        for( int i = 1; i <= rs.getColumnCount(); i++ )
        {
            columnName     = rs.getColumnName(i);
            columnType     = rs.getColumnType(i);
            columnTypeName = rs.getColumnTypeName(i);

            final IEntityProperty<P,?> property = getProperty(columnName);

            list.add( Triplet.makeTriplet( property, columnType, columnTypeName ) );
        }

        return new EntityStructMapper( structTypeName, this, list );
    }

    /** */
    @Override
    public IStructMapper<P> getStructMapper( TaskContext tc, String structTypeName ) {

        if( S.isNullOrEmpty(structTypeName) )
            return null;
        return StructMetaData.get( tc, structTypeName ).getMapper( getEntityClass() );
    }

    /** */
    private class JsonMapper implements IJsonMapper<P> {
        @Override
        public void toJson( P p, Writer w ) throws IOException {

            if( p == null )
                return;

            final JsonObjectBuilder ob = Json.createObjectBuilder( );

            for( IEntityProperty<P,?> ep : propertiesMap.values() )
            {
                Object v = ep.invokeGetter(p);

                if( v == null )
                    ob.addNull(ep.getPropertyName());
                else
                    ob.add( ep.getPropertyName(), v.toString() );
            }
            //Json.createWriterFactory( U.toMap( JsonGenerator.PRETTY_PRINTING, true ) ).createWriter(w).writeObject(ob.build());
            Json.createWriter(w).writeObject( ob.build() );
        }

        @Override
        public void toJsonArray( Iterable< P > pl, Writer w ) throws IOException {

            if( pl == null )
                return;

            final JsonArrayBuilder ab = Json.createArrayBuilder();

            for( P p : pl )
            {
                final JsonObjectBuilder ob = Json.createObjectBuilder( );

                for( IEntityProperty<P,?> ep : propertiesMap.values() )
                {
                    Object v = ep.invokeGetter(p);

                    if( v == null )
                        ob.addNull(ep.getPropertyName());
                    else
                        ob.add( ep.getPropertyName(), v.toString() );
                }
                ab.add( ob.build() );
            }

            Json.createWriter(w).writeArray( ab.build() );
        }

        /** */
        private P fromJsonObject( JsonObject job ) throws Exception {

            P p = getEntityClass().newInstance();

            for( IEntityProperty<P,?> ep : propertiesMap.values() )
            {
                if( ep.isReadOnly() )
                    continue;

                final String  jKey = ep.getPropertyName();
                final JsonValue jv = job.get( jKey );

                if( jv == null || jv == JsonValue.NULL ) {
                    ep.invokeSetter( p, null);
                    continue;
                }

                switch( jv.getValueType() ) {
                    case NULL:
                        ep.invokeSetter( p, null );
                        break;
                    case TRUE:
                    case FALSE:
                        ep.invokeSetter( p, job.getBoolean( jKey ) );
                        break;
                    case STRING:
                        ep.invokeSetter( p, job.getString ( jKey ) );
                        break;
                    case NUMBER:
                    {
                        final JsonNumber jnum = job.getJsonNumber( jKey );
                        Object v = jnum.isIntegral() ? jnum.longValue() : jnum.bigDecimalValue();
                        ep.invokeSetter( p, v );
                    }
                    break;
                }
            }
            return p;
        }


        /** */
        public P fromJson( String jsonStr )
        {
            try {

                final JsonReader jsr = Json.createReader( new StringReader(jsonStr) );
                return fromJsonObject(jsr.readObject());
            }
            catch( Throwable th ) {
                throw new RuntimeException( Tags.PRODUCT_LABEL + "Error on make struct from json string", th );
            }
        }

        @Override
        public List<P> fromJsonArray( String jsonStr ) {

            try {

                final JsonArray jsa = Json.createReader( new StringReader(jsonStr) ).readArray();
                final List<P> retList = new ArrayList<>(jsa.size());

                for( JsonValue jov : jsa )
                {
                    if( jov == JsonValue.NULL )
                        retList.add( null );
                    else
                    {
                        JsonObject job = (JsonObject)jov;
                        retList.add( fromJsonObject(job) );
                    }
                }

                return retList;
            }
            catch( Throwable th ) {
                throw new RuntimeException( Tags.PRODUCT_LABEL + "Error on make array from json string", th );
            }


        }
    }

    transient private JsonMapper jsonMapper;

    @Override
    public IJsonMapper<P> getJSonMapper() {
        if( jsonMapper == null )
            jsonMapper = new JsonMapper();
        return jsonMapper;
    }

    /** */
    @Override
    public Comparator<P> getIdComparator() {

        final List<IEntityProperty<P, ?> > l = getIDList();

        if( l == null || l.isEmpty() )
            throw new IllegalStateException( Tags.PRODUCT_LABEL + "Id list is empty. No comparator by Id." );

        if( l.size() == 1 )
        {
            final IEntityProperty<P,?> prpId = l.get(0);

            return new Comparator< P >() {
                @Override
                public int compare( P o1, P o2 ) {
                    Object id1 =  prpId.invokeGetter(o1);
                    Object id2 =  prpId.invokeGetter(o2);
                    return U.equals(id1, id2) ? 0 : -1;
                }
            };
        }
        else
        {
            return new Comparator< P >() {
                @Override
                public int compare( P o1, P o2 ) {

                    for( IEntityProperty<P,?> prpId : l )
                    {
                        Object id1 =  prpId.invokeGetter(o1);
                        Object id2 =  prpId.invokeGetter(o2);

                        if( !U.equals( id1, id2 ) )
                            return -1;
                    }

                    return 0;
                }//
            };
        }

    }

    @Override
    public P newInstance() {
        return null;
    }
}
