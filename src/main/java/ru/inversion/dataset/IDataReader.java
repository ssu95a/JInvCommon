package ru.inversion.dataset;

import ru.inversion.utils.U;

import java.util.Iterator;
import java.util.List;

/**
 * Базовый интерфейс для поставщика, источника данных для DataSet.
 * <p>
 * @param <T>
 *          тип данных записи получаемой из источника данных
 * 
 * @author sulimoff
 */
public interface IDataReader<T> extends AutoCloseable {
	
    /** 
     * Признак что достигнут конец источника данных.
     * <p>
     * @return 
     * {@code true } - больше данных нет
     * {@code false} - данные еще есть
     */
	boolean isEOF( );
	
    /**
     * Возвращает следующую страницу с данными.
     * <p>
     * @param nRowCount
     *          кол-во записей в странице с данными
     * @return 
     *          Iterator с записями
     */
	default List<T> getNextPage( int nRowCount )
    {
        return U.toList( getNextDataPart( nRowCount ) );
    }

    /** */
    default Iterator<T> getNextDataPart( int nRowCount ) { throw new UnsupportedOperationException("getNextDataPart");}

    /**
     * 
     */
    void close( ) throws DataSetException;

    /** */
    static <T> Iterator<T> iterator( IDataReader<T> dataReader, int pageSize ) {

        if( pageSize == -1 )
            return dataReader.getNextPage(pageSize).iterator();

        return new Iterator<T >() {

            Iterator<T> itr = dataReader.getNextPage(pageSize).iterator();

            private T currentValue;

            {
                if( itr.hasNext() )
                    currentValue = itr.next();
                else
                    itr = null;
            }

            @Override
            public boolean hasNext() {
                return itr != null;
            }

            @Override
            public T next( ) {

                T retValue = currentValue;

                if( itr.hasNext() )
                    currentValue = itr.next();
                else {

                    itr = dataReader.getNextPage(pageSize).iterator();

                    if(!itr.hasNext() )
                        itr = null;
                    else
                        currentValue = itr.next();
                }

                return retValue;
            }
        };
    }
}
