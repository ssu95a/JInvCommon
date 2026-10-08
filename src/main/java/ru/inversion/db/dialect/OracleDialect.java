package ru.inversion.db.dialect;

import oracle.jdbc.OracleConnection;
import oracle.sql.StructDescriptor;
import ru.inversion.dataset.IStructMapper;
import ru.inversion.db.JInvDbException;
import ru.inversion.db.entity.RegisterEnum;
import ru.inversion.meta.EntityMetaDataException;
import ru.inversion.meta.EntityMetadataFactory;
import ru.inversion.meta.IEntityMetaData;
import ru.inversion.meta.IEntityProperty;
import ru.inversion.meta.impl.ArrayMetaData;
import ru.inversion.meta.impl.StructMetaData;
import ru.inversion.parser.statement.LangEnum;
import ru.inversion.tc.TaskContext;
import ru.inversion.utils.Checks;
import ru.inversion.utils.S;
import ru.inversion.utils.Tags;
import ru.inversion.utils.U;

import javax.persistence.Entity;
import java.sql.*;
import java.time.LocalDate;
import java.time.temporal.Temporal;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

class OracleDialect extends BaseDialect {

    private static final Pattern ORACLE_NAME_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9_#$ ()&=,]*$");

    @Override public DialectId type() { return DialectId.ORACLE; }

    /** */
    protected final boolean checkIdentifier(String raw) {
        return raw != null && ORACLE_NAME_PATTERN.matcher(raw).matches() /*&& !isMixedCase(raw)*/;
    }

    /** */
    public String sqlName( String rawIdentifier )
    {
        if( S.isNullOrEmpty(rawIdentifier) )
            throw new IllegalArgumentException("'rawIdentifier' is empty");

        if( rawIdentifier.charAt(0) == '"' && S.lastChar(rawIdentifier) == '"' )
            return rawIdentifier;

        return checkIdentifier(rawIdentifier) ? rawIdentifier.toUpperCase() : quote(rawIdentifier);
    }

    /** */
    @Override
    public String lockClause( int waitSeconds )
    {
        if( waitSeconds < 0 )
            return " FOR UPDATE";

        if( waitSeconds == 0 )
            return " FOR UPDATE NOWAIT";

        return " FOR UPDATE WAIT ~3~";
    }

    /** */
    @Override
    public String sequenceNextVal(String sequenceName )
    {
        return Objects.requireNonNull( S.isNullOrEmpty(sequenceName) ? null : sequenceName, "'sequenceName' is null or empty") + ".NEXTVAL";
    }

    /** */
    @Override
    public ReturningMode returningMode( ) {
        return ReturningMode.ORACLE_OUT_PARAMS;
    }

    /** */
    @Override
    public LangEnum plLang() {
        return LangEnum.PL_SQL;
    }

    /** Выражения для преобразования к дате строки */
    @Override
    public String toDateClause( Object dateObj )
    {
        if( dateObj == null )
            return null;

        if( dateObj instanceof LocalDate )
            return " to_date(' " + to_dateFmt.format( (LocalDate)dateObj ) + "','YYYY-MM-DD') ";

        if( dateObj instanceof java.sql.Date )
            return " to_date(' " + to_dateFmt.format( ((java.sql.Date)dateObj).toLocalDate() ) + "','YYYY-MM-DD') ";

        if( dateObj instanceof java.util.Date )
            return " to_date('" + to_timestampFmt.format( ((java.util.Date)dateObj).toInstant() ) + "','YYYY-MM-DD HH24:MI:SS') ";

        if( dateObj instanceof Temporal)
            return " to_date('" + to_timestampFmt.format( (Temporal)dateObj ) + "','YYYY-MM-DD HH24:MI:SS') ";

        return super.toDateClause(dateObj);
    }

    final static private String PUBLIC = "PUBLIC.";
    final static private String SYS    = "SYS.";
    final static private String XXI    = "XXI.";

    /** */
    @Override
    public String elementType( String elementType )
    {
        if( !S.isNullOrEmpty(elementType) )
        {
            elementType = elementType.toUpperCase();

            if( elementType.startsWith(PUBLIC) || elementType.startsWith(SYS) )
            {
                //none;
            }
            else
            {
                if( elementType.startsWith(XXI) )
                    throw new IllegalArgumentException(Tags.PRODUCT_LABEL + "Не допустимо в именах типов ссылаться на объекты БД через имя 'XXI'!");

                elementType = S.prepend( PUBLIC, elementType );
            }
        }
        return elementType;
    }

    /** */
    @Override
    public <P, R> String forLovPredicate( IEntityProperty<P, R> property, String lovParameter ) {

        if( property == null || S.isNullOrEmpty(lovParameter) )
            return null;

        if( property.getCaseSensitiveSearchMode() == RegisterEnum.UPPER_CASE )
            return String.format("UPPER(%s) like UPPER(:%s)", columnName(property), lovParameter );
        else if( property.getCaseSensitiveSearchMode() == RegisterEnum.LOWER_CASE )
            return String.format("lower(%s) like lower(:%s)", columnName(property), lovParameter );
        else
            return String.format("%s like :%s", columnName(property), lovParameter );
    }


    @Override
    public <P> String forLovPredicate( IEntityMetaData<P> md, String lovParameter, Map<String,Object> parameters )
    {
        if( md == null || S.isNullOrEmpty(lovParameter) )
            return null;

        final StringBuilder sb = new StringBuilder();

        final String lp;

        if( lovParameter.charAt(0) != ':' )
            lp = ":" + lovParameter;
        else
            lp = lovParameter;

        final Map<String, IEntityProperty<P, ?>> columnsMap = md.getColumnsMap();
        Collection<IEntityProperty<P, ?>> columnsList = columnsMap.values();

        if( parameters != null && parameters.containsKey("columnOrder") )
        {
            Collection<String> columnOrder = (Collection<String>) parameters.get("columnOrder");
            columnsList = sortColumns( md, columnOrder );
        }//end if

        Map<String, List<IEntityProperty<P, ?>>> result = columnsList
                .stream().filter(IEntityProperty::useInLOVPredicate)
                .collect(Collectors.groupingBy(new Function<IEntityProperty<P, ?>, String>() {
                    @Override
                    public String apply(IEntityProperty<P, ?> p) {
                        //if (p.getColumnInfo().isIndexed())
                        {
                            if( p.getCaseSensitiveSearchMode() == RegisterEnum.LOWER_CASE )
                                return "lower";
                            if( p.getCaseSensitiveSearchMode() == RegisterEnum.UPPER_CASE )
                                return "upper";
                        }
                        return "concat";
                    }
                }));

        result.entrySet().forEach(new Consumer<Map.Entry<String, List<IEntityProperty<P, ?>>>>() {
            @Override
            public void accept( Map.Entry<String, List<IEntityProperty<P, ?>>> e ) {

                if( "lower".equals( e.getKey() ) )
                {
                    int i = 0;

                    for( IEntityProperty<P, ?> p : e.getValue() ) {

                        if( i++ > 0 )
                            sb.append(" OR ");

                        sb.append("lower(").append(columnName(p)).append(") like lower(").append(lp).append(") ");
                    }
                }

                if( "upper".equals( e.getKey() ) )
                {
                    int i = 0;

                    if( sb.length() > 0 )
                        i++;

                    for( IEntityProperty<P,?> p : e.getValue() ) {

                        if( i++ > 0 )
                             sb.append(" OR ");

                         sb.append("UPPER(").append( columnName(p) ).append(") like UPPER(").append(lp).append(") ");
                    }
                }

                if( "concat".equals( e.getKey() ) )
                {
                    if( sb.length() > 0 )
                        sb.append(" OR ");

                    sb.append("UPPER(");

                    int i = 0;

                    for( IEntityProperty<P, ?> p : e.getValue() ) {

                        if( i++ > 0 )
                            sb.append(" || ");

                        sb.append( columnName(p) );
                    }

                    sb.append(") like UPPER(").append( lp ).append(")");
                }
            }
        });

        return sb.toString();
    }


    @Override
    public String forLovPredicate( String lovParameter, List<String> columns )
    {
        if( S.isNullOrEmpty(lovParameter) || columns == null || columns.isEmpty() )
            return null;

        final String lp;

        if( lovParameter.charAt(0) != ':' )
            lp = ":" + lovParameter;
        else
            lp = lovParameter;

        StringBuilder sb = new StringBuilder("~UPPER(");

        int i = 0;

        for( String column : columns ) {

            if( i++ > 0 )
                sb.append(" || ' ' || "); // через пробел

            sb.append( columnName(column) );
        }

        sb.append(")~ like UPPER(").append( lp ).append(")");

        return sb.toString();
    }


    @Override
    public ResultSetMetaData getStructMetaData(TaskContext tc, String sqlTypeName) {

        try {

            Objects.requireNonNull( tc, "'tc' is null" );
            Objects.requireNonNull( sqlTypeName, "'sqlTypeName' is null" );

            final OracleConnection connection = tc.getConnection().unwrap(OracleConnection.class);

            return StructDescriptor.createDescriptor( sqlTypeName, connection ).getMetaData();

        } catch (Throwable th) {
            throw new EntityMetaDataException( Tags.PRODUCT_LABEL + "Error loading Oracle struct metadata for '" + S.nvl(sqlTypeName) + "'", th );
        }
    }

    /** */
    @Override
    public Struct createStruct( TaskContext tc, String sqlTypeName, Object[] attributes )
    {
        try {

            Objects.requireNonNull( tc, "'tc' is null" );
            Objects.requireNonNull( sqlTypeName, "'sqlTypeName' is null" );

            /*
             * Не обязательно для Oracle creation, но полезно:
             * 1. metadata гарантированно есть в StructMetaData cache;
             * 2. если тип не существует, ошибка будет ранней и одинаковой.
             */
            StructMetaData.get( tc, sqlTypeName );

            final OracleConnection connection = tc.getConnection().unwrap(OracleConnection.class);

            return connection.createStruct( sqlTypeName, attributes );

        } catch (Throwable th) {
            throw new EntityMetaDataException( Tags.PRODUCT_LABEL + "Error creating Oracle struct '" + S.nvl(sqlTypeName) + "'", th );
        }
    }

    @Override
    public ArrayMetaData getArrayMetaData(TaskContext tc, String sqlTypeName) {

        try {

            Objects.requireNonNull(tc, "'tc' is null");
            Objects.requireNonNull(sqlTypeName, "'sqlTypeName' is null");

            final OracleConnection connection = tc.getConnection().unwrap(OracleConnection.class);

            Array array = connection.createARRAY(sqlTypeName, new Object[0]);

            return new ArrayMetaData( sqlTypeName, array.getBaseTypeName(), array.getBaseType() );

        } catch (Throwable th) {
            throw new JInvDbException(th, "createARRAY", Collections.singletonList(sqlTypeName));
        }
    }

    /** */
    private Object[] prepareOracleArrayValues (
        TaskContext tc,
        ArrayMetaData arrayMetaData,
        Collection<?> values
    )
        throws Exception
    {
        if( values.isEmpty() )
            return new Object[0];

        Object value = U.firstNotNull(values);

        if( value == null )
            return values.toArray();

        if( arrayMetaData.getBaseType() == Types.STRUCT && value.getClass().isAnnotationPresent(Entity.class) )
        {
            IStructMapper mapper = EntityMetadataFactory.getEntityMetaData( value.getClass() ).getStructMapper( tc, arrayMetaData.getBaseTypeName() );

            final Struct[] structs = new Struct[values.size()];

            int i = 0;

            for( Object v : values)
                 structs[i++] = v == null ? null : mapper.toStruct( tc, v );

            return structs;
        }

        return values.toArray();
    }

    @Override
    public Array createArray( TaskContext tc, ArrayMetaData arrayMetaData, Collection<?> values )
    {
        try {

            Checks.Require.object( tc, "tc" );
            Checks.Require.object( arrayMetaData, "arrayMetaData" );

            Collection<?> safeValues = values == null
                    ? Collections.emptyList()
                    : values;

            Object[] arrayData = prepareOracleArrayValues (
                tc,
                arrayMetaData,
                safeValues
            );

            final OracleConnection connection = tc.getConnection().unwrap( OracleConnection.class);

            return connection.createARRAY( arrayMetaData.getSqlTypeName(), arrayData );

        } catch (Throwable th) {
            throw new RuntimeException( Tags.PRODUCT_LABEL + "Error creating Oracle ARRAY '" + (arrayMetaData == null ? null : arrayMetaData.getSqlTypeName()) + "'", th );
        }
    }


    /*
    @Override
    public <T extends Comparable<T>> IMarkerDao<T> createMarkerDao( MarkModeEnum markMode ) {
        return null;
    }

    @Override
    public <T extends Comparable<T>> IMarkerDao<T> createMarkerDao(MarkModeEnum markMode, long markerId) {
        return null;
    }
    */
}
