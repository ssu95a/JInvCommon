package ru.inversion.dataset.impl;

import ru.inversion.dataset.DataSetException;
import ru.inversion.dataset.ISQLExpression;
import ru.inversion.dataset.SQLExpression;
import ru.inversion.db.dialect.SqlDialect;
import ru.inversion.db.JInvDbException;
import ru.inversion.tc.TaskContext;
import ru.inversion.utils.ParametersValues;
import ru.inversion.utils.S;
import ru.inversion.utils.Tags;
import ru.inversion.utils.converter.TypeConverter;

import javax.persistence.NamedNativeQueries;
import javax.persistence.NamedNativeQuery;
import java.sql.*;
import java.util.*;
import java.util.function.BiConsumer;

/**
 *
 * @author ssu @
 */
public class XXIDsDao {

	/** */
	static public FilterData loadFilter( TaskContext tc, Long filterID, boolean fixed ) throws DataSetException {

        final String strSQLFilter = "SELECT cWhereBlk, cwherename FROM FRM_FILTER where ID = ?";

        final String strSQLParams = "select 'p' || IPARAMNUM, "
                                  + "decode( SUBSTR(cParamDefault, 1, 1), '&', JF_PKG_UTIL.CallMacro (SUBSTR (cParamDefault, 2), 0), cParamDefault ) CPARAMDEFAULT \n"
                                  + "  from FRM_FILTER_PARAMETER\n"
                                  + " where IdFilter = ?\n"
                                  + " order by IPARAMNUM ";

        if( filterID == null || filterID == 0L )
            return null;

        try {

            String filterName = null;
            String retFilterStr = null;
            String filterSQLText= null;

            try( PreparedStatement ps = tc.getConnection().prepareStatement(strSQLFilter) ) {

                ps.setLong( 1, filterID );

                try( ResultSet rs = ps.executeQuery() )
                {
                     if( rs.next() )
                     {
                         filterSQLText = rs.getString(1);
                         filterName = rs.getString(2);
                     }
                     else
                     {
                         return null;
                         //JInvDbException.throwNoDataFound(strSQLFilter, Collections.singletonList(filterID));
                     }
                }
            } catch ( SQLException ex ) {
                throw new JInvDbException( ex, strSQLFilter, Collections.singletonList(filterID));
            }

            if( filterSQLText != null ) {

                Map<String,Object> paramMap = new TreeMap<>();

                try( PreparedStatement ps = tc.getConnection().prepareStatement(strSQLParams) ) {

                    ps.setLong( 1, filterID );

                    try( ResultSet rs = ps.executeQuery() ) {

                        while( rs.next() ) {
                            paramMap.put( rs.getString(1), rs.getObject(2) );
                        }
                    }
                } catch ( SQLException ex ) {
                    throw new JInvDbException( ex, strSQLParams, Collections.singletonList(filterID));
                }


                retFilterStr = renderStoredFilter(filterSQLText, paramMap, tc.dialect());

            }// ifPresent

            return new FilterData(retFilterStr, filterName, filterID, fixed );
        }
        catch( Throwable th ) {
            throw new DataSetException( Tags.PRODUCT_LABEL + "Error on load filter with ID = " + filterID, th );
        }
	}

    /**
     * Сохранённые фильтры FRM_FILTER используют :p1, :p2, ...
     * Если значений нет, возвращаем исходный текст без трансформаций.
     * Legacy ~-маркеры удаляет SqlParameterizer при сборке SELECT.
     */
    static String renderStoredFilter(
            String filterSql,
            Map<String,Object> parameters,
            SqlDialect dialect
    ) {
        if( parameters == null || parameters.isEmpty() )
            return filterSql;

        SQLExpression expression = new SQLExpression(filterSql);
        expression.setParameters(parameters);
        return expression.getCompletedSQL(dialect);
    }

    /** */
    static public FilterData loadDefaultFilter( TaskContext tc, String formName, String dataSetName ) throws Exception {

        final String propertyName = "M_FILTER." + S.trimLongString(formName, 23) + "." + S.trimLongString(dataSetName, 23) + ".OPT";

        final Long filterId;
        final boolean fixed;

        try( CallableStatement cs = tc.getConnection().prepareCall("{call jf_pkg_util.get_prefupper( ?, ?, ? )}") )
        {
            cs.setString( 1, propertyName  );
            cs.setString( 2, S.EMPTY_STRING);
            cs.registerOutParameter( 3, Types.VARCHAR );

            cs.execute();

            Object o = cs.getObject(3);

            if( o == null || S.isNullOrEmpty(o.toString()) )
                return null;

            String s = o.toString();

            filterId = TypeConverter.convert( s.substring(2), Long.class);

            fixed  = s.charAt(1) == 'Y';
        }

        return loadFilter( tc, filterId, fixed );
    }

	/** */
	static public FilterData loadAutoFilter(TaskContext tc, String formName, String dataSetName ) throws DataSetException {

        final String strSQLFilter =
				  "SELECT ID from FRM_FILTER where cAutoSetup = ? || '.' || user || '.' || ? \n"
                + " union all  \n"
                + "SELECT F.ID \n"
                + "  FROM FRM_FILTER F\n"
                + " WHERE F.cFormName = ?\n"
                + "   AND ( F.cBlockName IS NULL OR F.cBlockName = ? ) \n"
                + "   AND F.ID IN ( SELECT G.IDFilter\n"
                + "					  FROM FRM_FILTER_ODB_GROUP G,\n"
                + "                  	   ODB_GRP_MEMBER M\n"
                + "                  WHERE G.IDGroup = M.Grp_ID \n"
                + "                    AND M.iUsrID = ACCESS_1.Get_Cur_User_ID() )";


        if( S.isNullOrEmpty(formName) || S.isNullOrEmpty(dataSetName ) )
            return null;

        try {

            long filterID = 0L;

            try( PreparedStatement ps = tc.getConnection().prepareStatement(strSQLFilter) ) {

                ps.setString( 1, formName   );
                ps.setString( 2, dataSetName);
                ps.setString( 3, formName   );
                ps.setString( 4, dataSetName);

                try( ResultSet rs = ps.executeQuery() ) {
                     if( rs.next() )
                         filterID = rs.getLong(1);
                }

            } catch ( SQLException ex ) {
                throw new JInvDbException( ex, strSQLFilter, Arrays.asList( formName, dataSetName, formName, dataSetName ) );
            }

            if( filterID != 0L )
                return loadFilter( tc, filterID, false);

            return null;
        }
        catch( Throwable th ) {
            throw new DataSetException( Tags.PRODUCT_LABEL + "Error on load autoFilter, form - '" + formName + "', dataSet - '" + dataSetName + "'", th );
        }
	}

	public final static class FilterData implements ISQLExpression {

        private final ParametersValues params = new ParametersValues();

	    private final String filterSql;

	    private final String filterName;

        private final Long   id;

        private final boolean fixed;

        public FilterData( String filterSql, String filterName, Long id, boolean fixed ) {
            this.filterSql = filterSql;
            this.filterName = filterName;
            this.id = id;
            this.fixed = fixed;
        }

        public String getFilterSql() {
            return filterSql;
        }

        public String getFilterName() {
            return filterName;
        }

        public Long getId() {
            return id;
        }

        public boolean isFixed() {
            return fixed;
        }

        @Override
        public String getSQL() {
            return getFilterSql();
        }
        @Override
        public String getCompletedSQL() {
            return ISQLExpression.super.getCompletedSQL();
        }

        @Override
        public Object getParameter( String parameterName ) {
            return null;
        }
        @Override
        public Object getParameter( int parameterIndex ) {
            throw new UnsupportedOperationException( Tags.PRODUCT_LABEL + "getParameter" );
        }
        @Override
        public void setParameter( String parameterName, Object value ) {
            params.setParameter(parameterName,value);
        }
        /** */
        public void forEachParam( BiConsumer<Comparable,Object> cnsm )
        {
            params.forEach(cnsm);
        }
    }

    /** Exact lookup without dialect fallback. */
    private static <P> String getQueryString(
            final Class<P> clazz,
            final String nativeQueryName
    ) {
        NamedNativeQueries nnqs = clazz.getAnnotation(NamedNativeQueries.class);

        if( nnqs != null ) {
            for( NamedNativeQuery q : nnqs.value() ) {
                if( nativeQueryName.equals(q.name()) )
                    return q.query();
            }
        }

        NamedNativeQuery nnq = clazz.getAnnotation(NamedNativeQuery.class);
        if( nnq != null && nativeQueryName.equals(nnq.name()) )
            return nnq.query();

        return null;
    }

    final static public String PG_POSTFIX = ".pg";

    /**
     * Resolves a logical native-query name for the current database.
     * PostgreSQL first uses {@code qn + ".pg"} and falls back to {@code qn};
     * Oracle uses {@code qn}. A name already ending in {@code .pg} is treated
     * as an exact name and is not suffixed twice.
     *
     * <p>When {@code qn == null}, an unnamed {@link NamedNativeQuery} is used.
     * A {@link NamedNativeQueries} container is also accepted when all its
     * entries form one logical family: {@code name} and optional
     * {@code name.pg}. More than one logical family is ambiguous.</p>
     */
    public static <P> String getNativeQuery(
            TaskContext tc,
            final Class<P> clazz,
            final String qn
    ) {
        if( clazz == null )
            return null;

        boolean postgres = tc != null && tc.isPostgreSql();

        return qn == null
                ? getUnnamedQuery(clazz, postgres)
                : getDialectQuery(clazz, qn, postgres);
    }

    private static <P> String getDialectQuery(
            Class<P> clazz,
            String queryName,
            boolean postgres
    ) {
        if( postgres && !queryName.endsWith(PG_POSTFIX) ) {
            String query = getQueryString(
                    clazz, queryName + PG_POSTFIX);
            if( query != null )
                return query;
        }

        return getQueryString(clazz, queryName);
    }

    private static <P> String getUnnamedQuery(
            Class<P> clazz,
            boolean postgres
    ) {
        NamedNativeQuery unnamed =
                clazz.getAnnotation(NamedNativeQuery.class);
        if( unnamed != null && !S.isNullOrEmpty(unnamed.query()) )
            return unnamed.query();

        NamedNativeQueries container =
                clazz.getAnnotation(NamedNativeQueries.class);
        if( container == null || container.value().length == 0 )
            return unnamed == null ? null : unnamed.query();

        String logicalName = null;

        for( NamedNativeQuery query : container.value() ) {
            String candidate = logicalName(query.name());

            if( logicalName == null )
                logicalName = candidate;
            else if( !logicalName.equals(candidate) )
                return null;
        }

        return getDialectQuery(clazz, logicalName, postgres);
    }

    private static String logicalName(String queryName) {
        return queryName.endsWith(PG_POSTFIX)
                ? queryName.substring(
                        0, queryName.length() - PG_POSTFIX.length())
                : queryName;
    }

    /** Compatibility alias for the unnamed lookup. */
    public static <P> String getUnnamedNativeQuery(
            TaskContext tc,
            final Class<P> clazz
    ) {
        return getNativeQuery(tc, clazz, null);
    }

}
