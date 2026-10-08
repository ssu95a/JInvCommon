package ru.inversion.db.rs;

import ru.inversion.dataset.IDataReader;
import ru.inversion.dataset.IParameters;
import ru.inversion.dataset.IRowMapper;
import ru.inversion.dataset.impl.JDBCDataReader;
import ru.inversion.dataset.parser.ExprInfo;
import ru.inversion.dataset.parser.SQLParser;
import ru.inversion.meta.EntityMetadataFactory;
import ru.inversion.meta.IEntityMetaData;
import ru.inversion.meta.IEntityProperty;
import ru.inversion.tc.TCStorage;
import ru.inversion.utils.S;
import ru.inversion.utils.Tags;
import ru.inversion.utils.U;

import javax.persistence.NamedNativeQuery;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Класс предоставляющий набор методов по реализации OO обертки Iterator вокруг JDBC ResultSet.
 * <p>
 * 
 * @author sulimoff
 */
public class RSUtils {
    
    /** 
     * Имплементация Iterator вокруг ResultSet.
     * <P>
     * (for internal use only)
     */
    private static class RSIterator<P> implements Iterator<P> {
        
        private final IRowMapper<P>  rsMapper;
        private       ResultSet   rs;
        private int   rowCounter = 0;
        
        /** */        
        public RSIterator( ResultSet rs, IRowMapper<P> rsMapper ) {
            this.rs       = rs;
            this.rsMapper = rsMapper;
        }
        
        /** */
        @Override
        public boolean hasNext( ) {
            return rs != null;
        }
        
        /** */
        @Override
        public P next( ) {

            if( !hasNext() )
                throw new NoSuchElementException();
            
            P row = null;
			
			try {
				
				row = rsMapper.mapRow( rs, rowCounter++ );
                
				if( !rs.next() ) {
					rs.close();
                    rs = null;
				}
				
			} catch( SQLException ex) {
				throw new RSException( Tags.PRODUCT_LABEL + "Error while getting records from ResultSet", ex);
			}
			return row;
        }
    }
    
    /** 
     * impl
     */
    private static <P> Iterator<P> createIterator( ResultSet rs, IRowMapper<P> rsMapper, Class<P> entityClass ) {
        
        try {
            
            if( rs == null || rs.isClosed() )
                return Collections.emptyIterator( );

            //if( rs.isBeforeFirst() )
            {
                if( !rs.next() ) {
                     rs.close();
                     return Collections.emptyIterator( );
                }
            }

            if( rsMapper == null )
                rsMapper = EntityMetadataFactory.getEntityMetaData( entityClass ).createRSRowMapper( rs );

            return new RSIterator<>( rs, rsMapper );
        }
        catch( Throwable th ) {
            RSException.throwOnCreateIterableOnResultSet( entityClass, th );
        }
        return null;
    }
    
    /**
     * Создает Iterator вокруг переданного ResultSet.
     * <P>
     * Преобразование из {@code ResultSet} в pojo осуществляется через {@link IRowMapper} который будет сформирован для класса {@code entityClass}
     * @param <P>
     *          тип Java (pojo) в который необходимо преобразовать одну запись из rs.
     * @param rs
     *          ResultSet из которого необходимо сделать преобразование,
     *          если передан {@code null} то возвращается пустой Iterator
     * @param entityClass
     *          Класс в который нужно преобразовать записи из ResultSet 
     * @return  
     *          Созданный Iterator
     * 
     * @throws NullPointerException 
     *          если entityClass {@code null}
     * 
     * @see ResultSet
     * @see Iterator
     */
    public static <P> Iterator<P> createIterator( ResultSet rs, Class<P> entityClass ) {
        return createIterator( rs, null, entityClass );
    }
    
    /**
     * Создает Iterator вокруг переданного ResultSet.
     * <P>
     * @param <P>
     *          тип Java (pojo) в который необходимо преобразовать одну запись из rs.
     * @param rs
     *          ResultSet из которого необходимо сделать преобразование,
     *          если передан {@code null} то возвращается пустой Iterator
     * @param rsMapper
     *          Объект {@link IRowMapper} который осуществляет преобразование между ResultSet и pojo.
     *          Не должен быть {@code null}
     * @return  
     *          Созданный Iterator
     * 
     * @throws NullPointerException 
     *          если rsMapper {@code null}
     * 
     * @see ResultSet
     * @see Iterator
     * @see IRowMapper
     */
    public static <P> Iterator<P> createIterator( ResultSet rs, IRowMapper<P> rsMapper ) {
        return createIterator( rs, Objects.requireNonNull( rsMapper, "rsMapper is null" ), null );
    }

    /**
     * Создает объект Iterable вокруг переданного ResultSet.
     * <p>
     * @param <P>
     *          тип Java (pojo) в который необходимо преобразовать одну запись из rs.
     * @param rs
     *          ResultSet из которого необходимо сделать преобразование,
     * @param entityClass
     *          Класс в который нужно преобразовать записи из ResultSet 
     * @return  
     *          Созданный Iterable
     * 
     * @see ResultSet
     * @see Iterable
     */
    public static <P> Iterable<P> createIterable( ResultSet rs, Class<P> entityClass ) {
        return U.<P>iterable( createIterator( rs, null, entityClass ) );
    }
    
    /**
     * Создает Iterable вокруг переданного ResultSet.
     * <P>
     * @param <P>
     *          тип Java (pojo) в который необходимо преобразовать одну запись из rs.
     * @param rs
     *          ResultSet из которого необходимо сделать преобразование,
     *          если передан {@code null} то возвращается пустой Iterable
     * @param rsMapper
     *          Объект {@link IRowMapper} который осуществляет преобразование между ResultSet и pojo.
     *          Не должен быть {@code null}
     * @return  
     *          Созданный Iterable
     * 
     * @throws NullPointerException 
     *          если rsMapper {@code null}
     * 
     * @see ResultSet
     * @see Iterable
     * @see IRowMapper
     */
    public static <P> Iterable<P> createIterable( ResultSet rs, IRowMapper<P> rsMapper ) {
        return U.<P>iterable( createIterator( rs, rsMapper, null ) );
    }

    /**
     * Создает Iterable из переданного SQL выражения.
     * <P>
     * @param <P>
     *          тип Java (pojo) в который необходимо преобразовать одну запись из rs.
     * @param connection
     *          Соединение к БД в рамках которого нужно сделать выборку на основе {@code strSQL}
     * @param clazz
     *          Java тип в который необходимо преобразовать запись из полученного на основе {@code strSQL ResultSet} 
     * @param strSQL
     *          Выражение SQL на основе которого нужно сделать выборку
     * @param parameters
     *          Параметры для strSQL, если необходимо
     * @return  
     *          Созданный Iterable
     * 
     * @throws NullPointerException 
     *          если rsMapper {@code null}
     * 
     * @see Iterable
     * @see IParameters
     */
    public static <P> Iterable<P> createIterable( Connection connection, Class<? extends P> clazz, String strSQL, IParameters parameters ) {
        try {

            //ArrayList f;

            final JDBCDataReader<? extends P> dataReader = new JDBCDataReader<>( connection, strSQL, null, clazz, false );
            dataReader.executeQuery( parameters );

            return U.<P>iterable( (Iterator<P>)IDataReader.iterator( dataReader, 50 ));
        }
        catch( Throwable th ) {
            RSException.throwOnCreateIterableOnSQL( strSQL, clazz, th );
        }
        return null;
    }

    /**
     * Создает Iterable из переданного SQL выражения.
     * <P>
     * @param <P>
     *          тип Java (pojo) в который необходимо преобразовать одну запись из rs.
     * @param connection
     *          Соединение к БД в рамках которого нужно сделать выборку на основе {@code strSQL}
     * @param rsMapper
     *          Объект {@link IRowMapper} который осуществляет преобразование между ResultSet и pojo.
     *          Если будет предан {@code null} то вместо pojo будет возвращаться {@code Object[]} 
     * @param strSQL
     *          Выражение SQL на основе которого нужно сделать выборку
     * @param parameters
     *          Параметры для strSQL, если необходимо
     * @return  
     *          Созданный Iterable
     * 
     * @throws RSException
     *          если в процессе были ошибки
     * 
     * @see Iterable
     * @see IParameters
     */
    public static <P> Iterator<P> createIterator( Connection connection, IRowMapper<P> rsMapper, String strSQL, IParameters parameters ) {
        try {
            JDBCDataReader<P> dataReader = new JDBCDataReader<>( connection, strSQL, rsMapper, null, false );
            dataReader.executeQuery( parameters );
            return IDataReader.iterator( dataReader,50);
        }
        catch( Throwable th ) {
            throw new RSException( Tags.PRODUCT_LABEL + "Error when creating an 'Iterable' to an SQL expression", strSQL, th );
        }
    }
    /** */
    public static <P> Iterable<P> createIterable( Connection connection, IRowMapper<P> rsMapper, String strSQL, IParameters parameters ) {
        return U.iterable( createIterator( connection, rsMapper, strSQL, parameters ) );
    }
    /** */
    public static <P> Iterable<P> createIterable( Connection connection, Class entityClass, IParameters parameters ) {
        return createIterable( connection, entityClass, null, null, parameters );
    }
    
    /**
     * Создает Iterable из переданного аннотаций класса Entity.
     * <P>
     * @param <P> тип Java (pojo) в который необходимо преобразовать одну запись из rs.
     * @param connection
     *          Соединение к БД в рамках которого нужно сделать выборку на основе {@code strSQL}
     * @param entityClass
     *          класс из аннотаций которого необходимо брать данные для построения SQL выражений
     * @param strWhere
     *          предикат на построенное из аннотаций SQL выражение
     * @param strOrderBy
     *          параметры сортировки полученного множества
     * @param parameters
     *          Параметры для strSQL, если необходимо
     * @return  
     *          Созданный Iterable
     * 
     * @throws RSException
     *          если в процессе были ошибки
     * 
     * @see Iterable
     * @see IParameters
     */
    public static <P> Iterable<P> createIterable( Connection connection, Class<? extends P> entityClass, String strWhere, String strOrderBy, IParameters parameters) {

        try {
        
            Objects.requireNonNull( entityClass, "entityClass is null");
            
            String strSQL = null;

            NamedNativeQuery nnq = entityClass.getAnnotation(NamedNativeQuery.class);
            if( nnq != null )
                strSQL = nnq.query();

            if( strSQL == null ) {

                IEntityMetaData<? extends P> entityMetaData = EntityMetadataFactory.getEntityMetaData( entityClass );

                Function< IEntityProperty, String > colMapper = new Function< IEntityProperty, String >() {

                    @Override
                    public String apply( IEntityProperty ec ) {

                        if( ec.getColumnInfo().getQueryExpr() == null )
                            return ec.getColumnName();

                        return ec.getColumnInfo().makeQueryExpr( TCStorage.INSTANCE().getByConnection(connection).dialect() );
                    }
                };

                strSQL = entityMetaData
                        .getColumnsMap()
                        .values()
                        .stream()
                        //.map( ( IEntityProperty p)->p.getColumnInfo().getName() )
                        .map( colMapper )
                        .collect( Collectors.joining( ",", "SELECT ", " FROM " + entityMetaData.getTableName() ) );
                
                StringBuilder sb = new StringBuilder(strSQL);
                
                if( !S.isNullOrEmpty( strWhere ) )
                    sb.append("\nwhere ").append(strWhere);
                if( !S.isNullOrEmpty( strOrderBy ) )
                    sb.append("\n order by ").append(strOrderBy);
                
                strSQL = sb.toString();
            }
            else 
            {
                if( !S.isNullOrEmpty( strWhere ) )
                {
                    SQLParser prs      = new SQLParser( strSQL );
                    ExprInfo  exprInfo = prs.analyze( );

                    StringBuilder sb = new StringBuilder();

                    if( exprInfo.isMysterious() ) {   
                    
                        sb.append("select * from (").append( strSQL ).append(") qrslt where ").append(strWhere);
                        
                    } else {
                        
                        if( exprInfo.isHasWhere() )
                            sb.append( strSQL ).append(" and (").append(strWhere).append(")");
                        else
                            sb.append( strSQL ).append(" where ").append(strWhere);
                    }

                    if( strOrderBy != null )
                        sb.append("\n order by ").append(strOrderBy);
                    
                    strSQL = sb.toString();
                }
                else {
                    if( !S.isNullOrEmpty( strOrderBy ) )
                        strSQL = strSQL + "\n order by " + strOrderBy;
                }
            }

            return createIterable( connection, entityClass, strSQL, parameters );
        }
        catch( Throwable th ) {
            RSException.throwOnCreateIterableOnEntity( entityClass, th );
        }
        return null;
    }

    /** */
    static public <T> IDataReader<T> createResultSetDataReader( ResultSet rs, Class<T> tClass ) {
        return new IDataReader<T> (){

            private final Iterator<T> iterator = RSUtils.createIterator (rs, tClass );

            @Override
            public boolean isEOF() {
                return !iterator.hasNext();
            }

            @Override
            public List< T > getNextPage( int nRowCount ) {

                List<T> list = new ArrayList<> (nRowCount == -1 ? 50 : nRowCount);

                for( int i = 0; (i < nRowCount || nRowCount == -1) && iterator.hasNext (); i++)
                     list.add (iterator.next ());

                return list;
            }

            @Override
            public Iterator< T > getNextDataPart( int nRowCount ) {
                return null;
            }

            @Override
            public void close() throws RSException {
                try {
                    if( rs != null && ! rs.isClosed() )
                        rs.close ();
                } catch ( SQLException ex ) {
                    throw new RSException ( Tags.PRODUCT_LABEL + "Error on close resultSet", ex );
                }
            }
        };
    }

    /** */
    static public <T> IDataReader<T> createResultSetDataReader( ResultSet rs, IRowMapper<T> mapper ) {
        return new IDataReader<T> (){

            private final Iterator<T> iterator = RSUtils.createIterator (rs, mapper );

            @Override
            public boolean isEOF() {
                return !iterator.hasNext();
            }

            @Override
            public List< T > getNextPage( int nRowCount ) {

                List<T> list = new ArrayList<> (nRowCount == -1 ? 50 : nRowCount);

                for( int i = 0; (i < nRowCount || nRowCount == -1) && iterator.hasNext (); i++)
                    list.add (iterator.next ());

                return list;
            }

            @Override
            public Iterator< T > getNextDataPart( int nRowCount ) {
                return null;
            }

            @Override
            public void close() throws RSException {
                try {
                    if( rs != null && ! rs.isClosed() )
                        rs.close ();
                } catch ( SQLException ex ) {
                    throw new RSException ( Tags.PRODUCT_LABEL + "Error on close resultSet", ex );
                }
            }
        };
    }

}
