package ru.inversion.db.dialect;

import ru.inversion.db.JInvDbException;
import ru.inversion.db.entity.RegisterEnum;
import ru.inversion.jdbc.common.ItemDto;
import ru.inversion.jdbc.common.meta.ResultSetMetaDataHandler;
import ru.inversion.jdbc.driver.lob.STRUCT;
import ru.inversion.meta.EntityMetaDataException;
import ru.inversion.meta.IEntityMetaData;
import ru.inversion.meta.IEntityProperty;
import ru.inversion.meta.impl.ArrayMetaData;
import ru.inversion.meta.impl.StructMetaData;
import ru.inversion.parser.statement.LangEnum;
import ru.inversion.tc.TaskContext;
import ru.inversion.utils.Checks;
import ru.inversion.utils.S;
import ru.inversion.utils.Tags;

import javax.persistence.EntityNotFoundException;
import java.io.*;
import java.sql.*;
import java.time.LocalDate;
import java.time.temporal.Temporal;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

final class PostgresDialect extends BaseDialect {

    static final private Map<String,String> g_typeNameMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    static {
        g_typeNameMap.put( "T_TAB_VARCHAR2_2000", "_varchar"   );
        g_typeNameMap.put( "T_Tab_Integer",       "_int8"      );
        g_typeNameMap.put( "sys.odcidatelist",    "_timestamp" );
        g_typeNameMap.put( "sys.odcinumberlist",  "_numeric"   );
        g_typeNameMap.put( "SYS.ODCIVARCHAR2LIST","_varchar"   );
        g_typeNameMap.put( "T_TAB_VARCHAR2_32767","_varchar"   );
    }


    @Override public DialectId type() { return DialectId.POSTGRES; }

    @Override
    public String sqlName(String rawIdentifier )
    {
        if( S.isNullOrEmpty(rawIdentifier) )
            throw new IllegalArgumentException("'rawIdentifier' is empty");

        if( rawIdentifier.charAt(0) == '"' && S.lastChar(rawIdentifier) == '"' )
            return rawIdentifier;

        return checkIdentifier(rawIdentifier) ? rawIdentifier : quote(rawIdentifier);
    }

    @Override
    public <P,R> String columnName( IEntityProperty<P,R> property )
    {
        return columnName( property.getColumnInfo().getName() );
    }

    /** */
    @Override
    public String columnName(String columnName) {

        if( S.isNullOrEmpty(columnName) || ( columnName.charAt(0) == '"' && S.lastChar(columnName) == '"') )
            ;
        else
        {
            if( columnName.indexOf('#') > -1 )
                columnName = S.quote( columnName.toLowerCase() );
        }

        return columnName;
    }

    @Override
    public String lockClause( int waitSeconds ) {
        return waitSeconds == 0 ? " FOR NO KEY UPDATE NOWAIT" : " FOR NO KEY UPDATE";
    }

    @Override
    public String sequenceNextVal(String sequenceName )
    {
        if( S.isNullOrEmpty(sequenceName) )
            throw new IllegalArgumentException("'sequenceName' is empty");

        String esc = sequenceName.replace("'", "''");
        return "nextval('" + esc + "')";
    }

    @Override
    public ReturningMode returningMode() {
        return ReturningMode.POSTGRES_RESULTSET;
    }

    @Override
    public LangEnum plLang() {
        return LangEnum.PL_pgSQL;
    }

    /** Выражения для преобразования к дате строки */
    @Override
    public String toDateClause( Object dateObj )
    {
        if( dateObj == null )
            return null;

        if( dateObj instanceof LocalDate)
            return " to_date(' " + to_dateFmt.format( (LocalDate)dateObj ) + "','YYYY-MM-DD') ";

        if( dateObj instanceof java.sql.Date )
            return " to_date(' " + to_dateFmt.format( ((java.sql.Date)dateObj).toLocalDate() ) + "','YYYY-MM-DD') ";

        if( dateObj instanceof java.util.Date )
            return " to_timestamp('" + to_timestampFmt.format( ((java.util.Date)dateObj).toInstant() ) + "','YYYY-MM-DD HH24:MI:SS') ";

        if( dateObj instanceof Temporal)
            return " to_timestamp('" + to_timestampFmt.format( (Temporal)dateObj ) + "','YYYY-MM-DD HH24:MI:SS') ";

        return super.toDateClause(dateObj);
    }

    /** */
    @Override
    public String elementType( String elementType )
    {
        String v = g_typeNameMap.get( elementType );
        if( v != null )
            elementType = v;

        return elementType.toLowerCase();
    }

    @Override
    public <P, R> String forLovPredicate(IEntityProperty<P, R> property, String lovParameter) {

        if( property == null || S.isNullOrEmpty(lovParameter) )
            return null;

        final String lp;

        if( lovParameter.charAt(0) != ':' )
            lp = ":" + lovParameter;
        else
            lp = lovParameter;

        if( property.getCaseSensitiveSearchMode() == RegisterEnum.UPPER_CASE )
            return String.format("UPPER(%s::varchar) like UPPER(%s)", columnName(property), lp );
        else if( property.getCaseSensitiveSearchMode() == RegisterEnum.LOWER_CASE )
            return String.format("lower(%s::varchar) like lower(%s)", columnName(property), lp );
        else
            return String.format("%s::varchar like %s", columnName(property), lp );
    }

    @Override
    public <P> String forLovPredicate(IEntityMetaData<P> md, String lovParameter, Map<String,Object> parameters )
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

        Map<String, List<IEntityProperty<P, ?>>> result = columnsList.stream().filter(IEntityProperty::useInLOVPredicate)
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
                        sb.append("lower(").append(columnName(p)).append("::varchar) like lower(").append(lp).append(") ");
                    }
                }
                else if( "upper".equals( e.getKey() ) )
                {
                    int i = 0;

                    if( sb.length() > 0 )
                        i++;

                    for( IEntityProperty<P, ?> p : e.getValue() ) {

                        if( i++ > 0 )
                            sb.append(" OR ");

                        sb.append("UPPER(").append(columnName(p)).append("::varchar) like UPPER(").append(lp).append(") ");
                    }
                }
                else if( "concat".equals( e.getKey() ) )
                {
                    //strSQL = columnSet.stream().collect( Collectors.joining("::varchar),upper(", "CONCAT(upper(", "::varchar)) like upper(:lov_parameter)"));

                    if( sb.length() > 0 )
                        sb.append(" OR ");

                    sb.append("UPPER(concat(");
                    int i = 0;

                    for( IEntityProperty<P, ?> p : e.getValue() ) {
                        if( i++ > 0 )
                            sb.append(", ");
                        sb.append( columnName(p) ).append("::varchar");
                    }
                    sb.append(")) like UPPER(").append( lp ).append(")");
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

        StringBuilder sb = new StringBuilder("concat_ws( ' '");

        for( String column : columns ) {
            sb.append(", ");
            sb.append( columnName(column) );
        }

        sb.append(") ilike ").append( lp );

        return sb.toString();
    }

    private static final String PG_SQL_STRUCT_TYPE =
    "select a.attname column_name, t.typname column_type_name,\n" +
            "       case t.typname \n" +
            "          when 'text' then 2005\n" +
            "          when 'bytea' then 2004\n" +
            "          else\n" +
            "            case t.typcategory\n" +
            "               when 'A' then 2003\n" +
            "               when 'C' then 2002\n" +
            "               when 'N' then 2\n" +
            "               when 'S' then 12\n" +
            "               when 'D' then 93\n" +
            "           end\n" +
            "       end column_type\n" +
            "  from pg_catalog.pg_attribute a\n" +
            "       inner join pg_catalog.pg_type t on t.oid = a.atttypid\n" +
            " where attRelId = (\n" +
            "       select oid\n" +
            "         from pg_catalog.pg_class c\n" +
            "        where c.relname = ?\n" +
            "          and c.relnamespace = (\n" +
            "              select oid\n" +
            "                from pg_catalog.pg_namespace\n" +
            "               where nspname = ?\n" +
            "          )\n" +
            " )\n" +
            "   and attnum > 0\n" +
            " order by a.attnum";

    @Override
    public ResultSetMetaData getStructMetaData( TaskContext tc, String sqlTypeName ) {

        try {

            Objects.requireNonNull(tc, "'tc' is null");
            Objects.requireNonNull(sqlTypeName, "'sqlTypeName' is null");

            String ns;
            String tp;

            int index = sqlTypeName.indexOf('.');

            if (index == -1) {
                ns = "pg_catalog";
                tp = sqlTypeName;
            } else {
                ns = sqlTypeName.substring(0, index);
                tp = sqlTypeName.substring(index + 1);
            }

            List<ItemDto> dtoList = new ArrayList<>();

            try (PreparedStatement ps = tc.getConnection().prepareStatement(PG_SQL_STRUCT_TYPE)) {

                ps.setString(1, tp);
                ps.setString(2, ns);

                try (ResultSet rs = ps.executeQuery()) {

                    if( !rs.next())
                        throw new EntityNotFoundException( Tags.PRODUCT_LABEL + "Не найден тип '" + sqlTypeName + "' в словаре БД.");

                    do {

                        ItemDto dto = new ItemDto(5, 7);

                        /*
                         * ResultSetMetaDataHandler ожидает значения в тех же позициях,
                         * которые раньше использовались в StructMetaData.loadPg(...).
                         */
                        dto.setInt   (1, rs.getInt("column_type"));
                        dto.setString(3, rs.getString("column_name"));
                        dto.setString(4, rs.getString("column_type_name"));

                        dtoList.add(dto);

                    } while (rs.next());
                }
            }

            return ResultSetMetaDataHandler.fromDto(dtoList.toArray(new ItemDto[0]));

        } catch (Throwable th) {
            throw new EntityMetaDataException( Tags.PRODUCT_LABEL + "Error loading PostgreSQL struct metadata for '" + S.nvl(sqlTypeName) + "'", th );
        }
    }

    @Override
    public Struct createStruct( TaskContext tc, String sqlTypeName, Object[] attributes )
    {
        try {

            Checks.Require.object( tc, "tc");
            Checks.Require.text  ( sqlTypeName, "sqlTypeName" );

            final ResultSetMetaData metaData = StructMetaData.get( tc, sqlTypeName ).asResultSetMetaData();

            return new STRUCT( sqlTypeName, attributes, metaData );

        } catch (Throwable th) {
            throw new EntityMetaDataException( Tags.PRODUCT_LABEL + "Error creating PostgreSQL struct '" + S.nvl(sqlTypeName) + "'", th );
        }
    }

    // -- ARRAY zone --

    private static final String PG_SQL_ARRAY_TYPE =
            "SELECT typname AS base_name,\n" +
                    "       CASE typname\n" +
                    "           WHEN 'text' THEN 2005\n" +
                    "           WHEN 'bytea' THEN 2004\n" +
                    "           ELSE CASE typcategory\n" +
                    "               WHEN 'A' THEN 2003\n" +
                    "               WHEN 'C' THEN 2002\n" +
                    "               WHEN 'N' THEN 2\n" +
                    "               WHEN 'S' THEN 12\n" +
                    "               WHEN 'D' THEN 93\n" +
                    "           END\n" +
                    "       END AS base_type\n" +
                    " FROM pg_type\n" +
                    "WHERE typname = ? AND typnamespace = to_regnamespace(?)";

    @Override
    public ArrayMetaData getArrayMetaData(TaskContext tc, String sqlTypeName) {

        try {
            Objects.requireNonNull(tc, "'tc' is null");
            Objects.requireNonNull(sqlTypeName, "'sqlTypeName' is null");

            String ns;
            String tp;

            int index = sqlTypeName.indexOf('.');

            if (index == -1) {
                ns = "pg_catalog";
                tp = sqlTypeName;
            } else {
                ns = sqlTypeName.substring(0, index);
                tp = sqlTypeName.substring(index + 1);
            }

            if( tp.isEmpty() || tp.charAt(0) != '_')
                throw new IllegalArgumentException( Tags.PRODUCT_LABEL + "Не корректный тип названия массива '" + sqlTypeName + "'. Имена типов массивов должны начинаться с символа _");

            tp = tp.substring(1);

            try(PreparedStatement ps = tc.getConnection().prepareStatement(PG_SQL_ARRAY_TYPE)) {
                ps.setString(1, tp);
                ps.setString(2, ns);

                try (ResultSet rs = ps.executeQuery())
                {
                    if( !rs.next() )
                        throw new EntityNotFoundException( Tags.PRODUCT_LABEL + "Не найден массив с типом '" + sqlTypeName + "' в словаре БД." );

                    String baseName = rs.getString(1);
                    int baseType = rs.getInt(2);

                    return new ArrayMetaData( sqlTypeName, index == -1 ? baseName : ns + "." + baseName, baseType );
                }
            }

        } catch (SQLException ex) {
            throw new JInvDbException(ex, PG_SQL_ARRAY_TYPE, Arrays.asList(sqlTypeName));
        }
    }

    @Override
    public Array createArray
    (
        TaskContext tc,
        ArrayMetaData arrayMetaData,
        Collection<?> values
    )
    {
        try {

            Objects.requireNonNull( tc, "'tc' is null" );
            Objects.requireNonNull( arrayMetaData, "'arrayMetaData' is null" );

            Collection<?> safeValues = values == null ? Collections.emptyList() : values;

            // if( arrayMetaData.getBaseType() == Types.STRUCT )
            //    throw new IllegalStateException( Tags.PRODUCT_LABEL + "PostgreSQL array of STRUCT is not supported for type '" + arrayMetaData.getSqlTypeName() + "'");

            // Пусть драйвер ругнется о не поддерживаемых типах для массива

            return tc.getConnection().createArrayOf( arrayMetaData.getBaseTypeName(), safeValues.toArray() );

        } catch (Throwable th) {
            throw new RuntimeException( Tags.PRODUCT_LABEL + "Error creating PostgreSQL ARRAY '" + (arrayMetaData == null ? null : arrayMetaData.getSqlTypeName()) + "'",  th );
        }
    }

}
