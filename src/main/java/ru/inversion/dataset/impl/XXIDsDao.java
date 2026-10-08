package ru.inversion.dataset.impl;

import ru.inversion.dataset.DataSetException;
import ru.inversion.dataset.ISQLExpression;
import ru.inversion.dataset.ParametersByName;
import ru.inversion.dataset.parser.SQLParser;
import ru.inversion.db.JInvDbException;
import ru.inversion.tc.TaskContext;
import ru.inversion.utils.ParametersValues;
import ru.inversion.utils.S;
import ru.inversion.utils.Tags;
import ru.inversion.utils.U;
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


                if( paramMap.isEmpty() )
                {
                    retFilterStr = filterSQLText;
                }
                else
                {
                    SQLParser sp = new SQLParser(filterSQLText);
                    retFilterStr = sp.prepareSqlWithParametersValue((ParametersByName)parameterName -> paramMap.get(parameterName));
                    /*
                    String sql       = filterSQLText;
                    StringBuilder sb = new StringBuilder();

                    char ch;

                    for( int i = 0; i < sql.length(); i++ ) {

                        ch = sql.charAt(i);

                        if( ch == ':' ) {

                            while (
                                ++i < sql.length() && isIgnoreChar( sql.charAt(i) )
                            )
                            {;}

                            StringBuilder p = new StringBuilder( );

                            for( ; i < sql.length() && !isIgnoreChar( sql.charAt(i) ); i++ ) {
                                p.append( sql.charAt(i) );
                            }

                            if( p.length() > 0 )
                            {
                                Object parameterValue = paramMap.get( p.toString() );
                                p = null;
                                if( parameterValue != null )
                                    sb.append( castValue2Str( parameterValue ) );
                            }

                            continue;
                        }
                        sb.append(ch);
                    }//end for

                    retFilterStr = sb.toString();
                    */
                }//end else

            }// ifPresent

            return new FilterData(retFilterStr, filterName, filterID, fixed );
        }
        catch( Throwable th ) {
            throw new DataSetException( Tags.PRODUCT_LABEL + "Error on load filter with ID = " + filterID, th );
        }
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

    /** */
    private static <P> String getQueryString( final Class<P> clazz, final String nativeQueryName )
    {
        NamedNativeQueries nnqs = clazz.getAnnotation(NamedNativeQueries.class);

        if( nnqs != null ) {

            final NamedNativeQuery[] qList = nnqs.value();

            for( NamedNativeQuery q : qList ) {

                if( nativeQueryName.equals( q.name() ) ) {
//return
                    return q.query();
                }
            }//end for
        }

        NamedNativeQuery nnq = clazz.getAnnotation(NamedNativeQuery.class);

        if( nnq != null )
        {
            if( !nativeQueryName.equals( nnq.name() ) )
                return null;
//return
            return nnq.query();
        }

        return null;
    }

    final static public String PG_POSTFIX = ".pg";

    /** */
    public static <P> String getNativeQuery( TaskContext tc, final Class<P> clazz, final String qn )
    {
        if( clazz == null )
            return null;

        if( qn == null ) {

            String q = U.callIfNotNull( clazz.getAnnotation(NamedNativeQuery.class), NamedNativeQuery::query );

            if( S.isNullOrEmpty(q) )
            {
                final NamedNativeQueries qs = clazz.getAnnotation(NamedNativeQueries.class);

                if( qs != null && qs.value().length == 2 )
                {
                    final NamedNativeQuery[] aq = qs.value();

                    if( Math.abs( aq[0].name().length() - aq[1].name().length() ) == PG_POSTFIX.length() )
                    {
                        final int pg = aq[0].name().length() > aq[1].name().length() ? 0 : 1;
                        final int cm = pg == 0 ? 1 : 0;

                        if( aq[pg].name().substring( 0, aq[cm].name().length() ).equals( aq[cm].name() ) )
                        {
                            if( tc == null || tc.isOracle() )
                                q = aq[cm].query();
                            else
                                q = aq[pg].query();
                        }
                    }
                }
            }
            return q;
        }

        if( tc == null || tc.isOracle() )
            return getQueryString( clazz, qn );
        else
            return U.nvl( getQueryString( clazz, qn + PG_POSTFIX ), getQueryString( clazz, qn ) );
    }

    /** */
    public static <P> String getUnnamedNativeQuery( TaskContext tc, final Class<P> clazz )
    {
        if( clazz == null )
            return null;

        String q = U.callIfNotNull( clazz.getAnnotation(NamedNativeQuery.class), NamedNativeQuery::query );

        if( !S.isNullOrEmpty(q) )
            return q;

        final NamedNativeQueries qs = clazz.getAnnotation(NamedNativeQueries.class);

        if( qs != null && qs.value().length == 2 )
        {
            final NamedNativeQuery[] aq = qs.value();

            int or = 0;
            int pg = 1;

            if( aq[0].name().length() > aq[1].name().length() ) {
                pg = 0;
                or = 1;
            }

            if( aq[pg].name().length() - aq[or].name().length() == PG_POSTFIX.length() )
            {
                if( aq[pg].name().startsWith(aq[or].name()) & aq[pg].name().endsWith(PG_POSTFIX) )
                {
                    if( tc == null || tc.isOracle() )
                        return aq[or].query();
                    return aq[pg].query();
                }
            }
        }
        return null;
    }

}
