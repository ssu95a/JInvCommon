package ru.inversion.db;

import ru.inversion.dataset.IRowMapper;
import ru.inversion.meta.EntityMetadataFactory;
import ru.inversion.tc.TaskContext;
import ru.inversion.utils.S;
import ru.inversion.utils.Tags;
import ru.inversion.utils.converter.TypeConverter;

import javax.persistence.Entity;
import javax.persistence.NamedNativeQueries;
import javax.persistence.NamedNativeQuery;
import java.lang.reflect.Array;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class DBSingleResult<R,V> extends AbstractDBResult<R,V, DBSingleResult<R,V>> {

    private Optional<V> result = Optional.empty();


    /** */
    private boolean findById = false;

    /** */
    public DBSingleResult( TaskContext tc ) {
        super(tc);
    }

    /** */
    public Optional<V> getLastResult( ) {
        return result;
    }

    /** */
    private String getNativeQuerySqlString( Class clazz, boolean doThrow ) {

        if( S.isNullOrEmpty(this.query) ) {

            NamedNativeQuery nnq = (NamedNativeQuery) clazz.getAnnotation(NamedNativeQuery.class);

            if( nnq == null ) {

                if( doThrow )
                    throw new IllegalArgumentException( Tags.PRODUCT_LABEL + "The class '" + clazz.getName() + "' is not specified annotation 'NamedNativeQuery'");
//return
                return null;
            }
//return
            return nnq.query();
        }
        else {

            NamedNativeQueries nnqs = (NamedNativeQueries) clazz.getAnnotation(NamedNativeQueries.class);

            if( nnqs != null ) {

                NamedNativeQuery[] value = nnqs.value();

                for( NamedNativeQuery q : value ) {

                    if( this.query.equals( q.name() ) ) {
//return
                        return q.query();
                    }
                }//end for
            }

            NamedNativeQuery nnq = (NamedNativeQuery) clazz.getAnnotation(NamedNativeQuery.class);

            if( nnq != null ) {

                if( !query.equals( nnq.name() ) ) {

                    if( doThrow )
                        throw new IllegalArgumentException( Tags.PRODUCT_LABEL + "Not found the annotation 'NamedNativeQuery' with name = '" + query + "', class - " + clazz.getName() );
//return
                    return null;
                }
//return
                return nnq.query();
            }

        }//end else

        if( doThrow )
            throw new IllegalStateException( Tags.PRODUCT_LABEL + "SQL expression not specified ");

        return null;
    }

    /** */
    private String getSQL( ) {

        String strSQL = null;

        if( runClass != null )
            strSQL = getNativeQuerySqlString( runClass, valueClass != null );

        if( strSQL == null )
            strSQL = getNativeQuerySqlString( valueClass, true );

        return strSQL;
    }

    /** */
    public Optional<V> execute() throws DBResultException {

        Object o = internalExecute();

        if( o == null )
            result = Optional.empty();
        else
            result = Optional.of( (V)o );

        return result;
    }

    /** */
    private IRowMapper<V> getMapper() {

        if( mapper != null )
            return mapper;


        return null;
    }

    /** */
    private PreparedStatement getOrCreateStatement() throws SQLException {

        return null;
    }

    /** */
    private Object internalExecute( ) throws DBResultException {

        check4Exec( );

        try( PreparedStatement ps = taskContext.getConnection().prepareStatement( null /*sqlExpression*/ ) )
        {
            {
                int i = 1;

//                for(Object o : params)
//                    ps.setObject(i++, o);
            }

            try( ResultSet rs = ps.executeQuery() )
            {
                if( !rs.next() ) {

                    if( throwOnNoDataFound )
                        ;//JInvDbException.throwNoDataFound( sqlExpression, params );

                    return null;
//return
                }

                if( mapper == null )
                {
                    if( valueClass != null && valueClass.isAnnotationPresent(Entity.class) )
                        mapper = EntityMetadataFactory.<V>getEntityMetaData( valueClass ).createRSRowMapper(rs);
                }

                if( mapper == null )
                {
                    int columnCount = rs.getMetaData().getColumnCount();

                    if( valueClass == null ) {

                        if( columnCount == 1 )
                            return rs.getObject( 1 );
                        else
                        {
                            Object[] values = new Object[columnCount];

                            for( int i = 1; i <= columnCount; i++ )
                                 values[i-1] = rs.getObject(i);

                            return values;
//return
                        }
                    }
                    else {

                        if( valueClass.isArray() ) {

                            if( valueClass == Object[].class ) {

                                Object[] values = new Object[columnCount];

                                for( int i = 1; i <= columnCount; i++ )
                                     values[i-1] = rs.getObject(i);

                                return values;
//return
                            }
                            else {

                                Class itemClass = valueClass.getComponentType();

                                V values = (V)Array.newInstance( itemClass, columnCount );

                                Object value = null;

                                for( int i = 1; i <= columnCount; i++ ) {
                                    value = TypeConverter.<Object>convert( rs.getObject(i), itemClass );
                                    Array.set(values, i-1, value);
                                }

                                return values;
//return
                            }
                        }
                        else
                        {
                            Object value = rs.getObject(1);

                            if( valueClass == Object.class )
                                return value;

                            return TypeConverter.<V>convert( value, valueClass );
//return
                        }
                    }
                }
                else
                    return mapper.mapRow( rs, 0 );
//return
            }
        }
        catch( Throwable th ) {
            if( th instanceof JInvDbException )
                throw (JInvDbException)th;
            throw new JInvDbException( th, ""/*sqlExpression, params == null ? null : params*/ );
        }
    }

}

