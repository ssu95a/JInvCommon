package ru.inversion.db;

import ru.inversion.dataset.IRowMapper;
import ru.inversion.dataset.impl.XXIDsDao;
import ru.inversion.meta.EntityMetadataFactory;
import ru.inversion.tc.TCStorage;
import ru.inversion.tc.TaskContext;
import ru.inversion.utils.S;
import ru.inversion.utils.Tags;
import ru.inversion.utils.U;
import ru.inversion.utils.converter.TypeConverter;

import javax.persistence.Entity;
import javax.persistence.NamedNativeQuery;
import java.lang.reflect.Array;
import java.lang.reflect.ParameterizedType;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Arrays;

/**
 * Класс-утилита выполнения простых запросов для получения одной записи или одного поля.
 * <p>
 * SQL выражение для получения данных задается через аннотацию {@code NamedNativeQuery} или {@code NamedNativeQueries},
 * но в последнем случае необходимо задать name выполняемого выражения.
 * <p>
 * Пример:
 * {@code 
    //создание
    @NamedNativeQuery(name="",query="select UTIL.MRK_GetNextMarkerID() from dual")
    private static class PGetMarkerID extends DBUniqueResult<Long> {}

    return ( new PGetMarkerID() ).execute( tc.getConnection() );
    }
    * 
 * @param <T>
 *      тип возвращаемой записи
 * 
 * @author sulimoff
 */
public class DBUniqueResult<T>  {
    
    /**
     * Класс у которого необходимо брать аннотацию с SQL выражением.
     */
    final private Class runClass;
    
    /** 
     * Тип данных получаемого значения в результате выполнения SQL выражения.
     * <p>
     * Задается через generic выполняемого класса.
     */
    final private Class<T> valClass; 
            
    /** 
     * SQL выражение для выполнения.
     */
    private String sqlExpression;
    final private String namedQueryName;

    /** */
    public DBUniqueResult(  ) {
        this( null, null );
    }
    
    /**
     * @param runClass
     *          класс из аннотации которого нужно брать SQL выражение, если передан {@code null} то будет использован {@code this}
     */
    public DBUniqueResult( Class runClass ) {
        this( runClass, null );
    }

    /**
     * @param runClass
     *        класс из аннотации которого нужно брать SQL выражение, если передан {@code null} то будет использован {@code this}
     * @param namedQueryName
     */
    public DBUniqueResult( Class runClass, String namedQueryName ) {
        this( runClass, null, namedQueryName );
    }
    
    /** */
    @SuppressWarnings("unchecked")    
    private String getSqlString( TaskContext tc, Class clazz, String namedQueryName, boolean doThrow ) {
        
        if( S.isNullOrEmpty(namedQueryName) ) {
            
            NamedNativeQuery nnq = (NamedNativeQuery) clazz.getAnnotation(NamedNativeQuery.class);
            
            if( nnq == null ) {

                String q = XXIDsDao.getUnnamedNativeQuery(tc,clazz);

                if( S.isNullOrEmpty(q) )
                    if( doThrow )
                        throw new IllegalArgumentException( Tags.PRODUCT_LABEL + "The class '" + clazz.getName() + "' is not specified annotation 'NamedNativeQuery'");
//return            
                return q;
            }
//return            
            return nnq.query();
        }
        else
        {
            String q =  XXIDsDao.getNativeQuery( tc, clazz, namedQueryName );
            if(!S.isNullOrEmpty(q) )
                return q;
            /*
            NamedNativeQueries nnqs = (NamedNativeQueries) clazz.getAnnotation(NamedNativeQueries.class);
            
            if( nnqs != null ) {

                NamedNativeQuery[] value = nnqs.value();

                for( NamedNativeQuery q : value ) {
                    
                    if( namedQueryName.equals( q.name() ) ) {
//return
                         return q.query();
                    }
                }//end for
            }
            
            NamedNativeQuery nnq = (NamedNativeQuery) clazz.getAnnotation(NamedNativeQuery.class);

            if( nnq != null ) {

                if( !namedQueryName.equals( nnq.name() ) ) {
                        
                    if( doThrow )
                        throw new IllegalArgumentException( Tags.PRODUCT_LABEL + "Not found the annotation 'NamedNativeQuery' with name = '" + namedQueryName + "', class - " + clazz.getName() );
//return            
                    return null;
                }
//return                
                return nnq.query();
            }
            */

        }//end else

        if( doThrow )
            throw new IllegalStateException( Tags.PRODUCT_LABEL + "SQL expression not specified ");
        
        return null;
    }
    
    /** 
     * Создает новый объект для выполнения SQL выражений.
     * <p>
     * @param rClass
     *        класс из аннотации которого нужно брать SQL выражение, если передан {@code null} то будет использован {@code this}
     * @param vClass
     *        класс значение которое необходимо вернуть
     * @param nqn
     *        Имя SQL выражения из аннотаций, если {@code null}, то подразумевается, что на классе задана аннотация {@NamedNativeQuery}
     * 
     * @see NamedNativeQuery
     */
    public DBUniqueResult( Class rClass, Class<T> vClass, String nqn ) {

        this.namedQueryName = nqn;

        runClass = U.nvl( rClass, getClass() );
        
        if( vClass == null ) {
        
            if( runClass == getClass() )
                this.valClass = (Class<T>) ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[0];
            else
                this.valClass = null;
        }
        else
            valClass = vClass;
    }
    
    /**
     * Запускает на выполнение заданное SQL выражение.
     * <p>
     * @param tc
     *        Соединение к БД в котором происходит выполнение SQL выражения
     * @param throwOnNoDataFound
     *        Признак нужно ли выбрасывать исключение, если в результате выполнения SQL данные не были найдены
     * @param mapper
     *        Класс маппинга данных из JDBC resultSet в ОО представление нужного типа
     * @param params
     *        Параметры для подстановки в SQL выражение. Используются только параметры по индексу.
     * @return  
     *        Значение типа T, если данные не найдены  {@code throwOnNoDataFound = false}, то {@code null}
     * @exception 
     *        DbNoDataFoundException если в результате выполнения SQL записи не найдены и {@code throwOnNoDataFound = true}
     * @exception
     *        JInvDbException если при выполнении SQL были какие-то ошибки
     * 
     * @see IRowMapper
     * @see NamedNativeQuery
     */
    @SuppressWarnings("unchecked")
    public T execute( TaskContext tc, boolean throwOnNoDataFound, IRowMapper<T> mapper, Object ... params ) {

        if( sqlExpression == null )
        {
            if( valClass == null )
                sqlExpression = getSqlString( tc, runClass, namedQueryName, true );
            else
                sqlExpression = U.nvl (
                    getSqlString( tc, valClass, namedQueryName, false),
                    getSqlString( tc, runClass, namedQueryName, true )
                );
        }

        try( PreparedStatement ps = tc.getConnection().prepareStatement( sqlExpression ) )
        {
            if( params != null && params.length > 0 ) 
            {
                for( int i = 0; i < params.length; i++ )
                     ps.setObject( i+1, params[i] );
            }

            try( ResultSet rs = ps.executeQuery() )
            {
                if( !rs.next() ) {
                    if( throwOnNoDataFound )
                        JInvDbException.throwNoDataFound( sqlExpression, Arrays.asList( params ) );
                    return null;
//return                        
                }

                if( mapper == null ) 
                {
                    if( IRowMapper.class.isAssignableFrom(runClass) )
                        mapper = (IRowMapper<T>)runClass.newInstance();
                    else 
                        if( valClass != null && IRowMapper.class.isAssignableFrom(valClass) )
                            mapper = (IRowMapper<T>)valClass.newInstance();
                    else 
                        if( this instanceof IRowMapper )
                            mapper = (IRowMapper<T>)this;
                    else 
                        if( valClass != null && valClass.isAnnotationPresent(Entity.class) )
                            mapper = EntityMetadataFactory.<T>getEntityMetaData( valClass ).createRSRowMapper(rs);
                }
                
                if( mapper == null ) 
                {
                    int columnCount = rs.getMetaData().getColumnCount();

                    if( valClass == null ) {
                    
                        if( columnCount == 1 )
                            return (T)rs.getObject( 1 );
                        else 
                        {
                            Object[] values = new Object[columnCount];

                            for( int i = 1; i <= columnCount; i++ )
                                values[i-1] = rs.getObject(i);

                            return (T)values;
//return                        
                        }

                    }
                    else {
                        
                        if( valClass.isArray() ) {
                            
                            if( valClass == Object[].class ) {
                                
                                Object[] values = new Object[columnCount];

                                for( int i = 1; i <= columnCount; i++ )
                                    values[i-1] = rs.getObject(i);

                                return (T)values;
//return                        
                            }
                            else {
                            
                                Class itemClass = valClass.getComponentType();

                                T values = (T)Array.newInstance( itemClass, columnCount );
                                
                                Object value = null;
                                
                                for( int i = 1; i <= columnCount; i++ ) {
                                    value = TypeConverter.<Object>convert( rs.getObject(i), itemClass );
                                    Array.set(values, i-1, value);
                                }
                                
                                return (T)values;
//return                        
                            }
                        }
                        else {
                            Object value = rs.getObject(1);
                            if( valClass == Object.class )
                                return (T)value;
                            return TypeConverter.<T>convert( value, valClass );
//return                        
                        }
                    }
                }
                else
                    return mapper.mapRow(rs, 0);
//return                        
            }
        }
        catch( Throwable th ) {
            if( th instanceof JInvDbException )
                throw (JInvDbException)th;
            throw new JInvDbException( th, sqlExpression, params == null ? null : Arrays.asList( params ) );
        }
    }

    /**
     * @see #execute(ru.inversion.tc.TaskContext, boolean, ru.inversion.dataset.IRowMapper, java.lang.Object...) 
     */
    public T execute( Connection c, boolean throwOnNoDataFound, IRowMapper<T> mapper, Object ... params ) {
        return execute(TCStorage.INSTANCE().getByConnection(c), throwOnNoDataFound, (IRowMapper<T>)mapper, params );
    }
    
    /**
     * @see #execute(ru.inversion.tc.TaskContext, boolean, ru.inversion.dataset.IRowMapper, java.lang.Object...) 
     */
    public T execute( Connection connection, boolean throwOnNoDataFound, Object ... params ) {
        return execute( connection, throwOnNoDataFound, (IRowMapper<T>)null, params );
    }
    
    /**
     * @see #execute(ru.inversion.tc.TaskContext, boolean, ru.inversion.dataset.IRowMapper, java.lang.Object...) 
     */
    public T execute( TaskContext tc, boolean throwOnNoDataFound, Object ... params ) {
        return execute( tc, throwOnNoDataFound, (IRowMapper<T>)null, params );
    }

    /** */
    public static void main(String[] args) {
    
        try {
        
            TypeConverter.convert( "10", Long.class );
            
        }
        catch( Throwable th ) {
            th.printStackTrace();
        }
        
    }
    
}
