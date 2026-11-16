package ru.inversion.dataset;

import ru.inversion.utils.AutoCloseableIterator;
import ru.inversion.utils.Tags;
import ru.inversion.utils.U;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

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
    static <T> Iterator<T> iterator(
            IDataReader<T> dataReader,
            int pageSize
    ) {
        return closeableIterator(dataReader, pageSize);
    }

    /**
     * Paged iterator that owns the supplied reader.
     *
     * The reader is closed automatically at exhaustion or after a paging
     * failure. Callers that stop iteration early can close the returned
     * iterator explicitly.
     */
    static <T> AutoCloseableIterator<T> closeableIterator(
            IDataReader<T> dataReader,
            int pageSize
    ) {
        Objects.requireNonNull(dataReader, "dataReader");

        return new AutoCloseableIterator<T>() {
            private Iterator<T> page;
            private T currentValue;
            private boolean hasCurrent;
            private boolean closed;

            {
                try {
                    page = dataReader.getNextPage(pageSize).iterator();

                    if( page.hasNext() ) {
                        currentValue = page.next();
                        hasCurrent = true;
                    }
                    else
                        finish();
                }
                catch( RuntimeException | Error ex ) {
                    closeAfterFailure(ex);
                    throw ex;
                }
            }

            private void advance() {
                if( page != null && page.hasNext() ) {
                    currentValue = page.next();
                    return;
                }

                if( pageSize == -1 ) {
                    finish();
                    return;
                }

                page = dataReader.getNextPage(pageSize).iterator();

                if( page.hasNext() )
                    currentValue = page.next();
                else
                    finish();
            }

            private void finish() {
                currentValue = null;
                hasCurrent = false;
                page = null;

                if( closed )
                    return;

                try {
                    close();
                }
                catch( DataSetException ex ) {
                    throw new RuntimeException(
                            Tags.PRODUCT_LABEL
                                    + "Error closing dataReader iterator",
                            ex
                    );
                }
            }

            private void closeAfterFailure( Throwable failure ) {
                currentValue = null;
                hasCurrent = false;
                page = null;

                if( closed )
                    return;

                closed = true;

                try {
                    dataReader.close();
                }
                catch( Throwable closeError ) {
                    if( failure != closeError )
                        failure.addSuppressed(closeError);
                }
            }

            @Override
            public boolean hasNext() {
                return hasCurrent;
            }

            @Override
            public T next() {
                if( !hasCurrent )
                    throw new NoSuchElementException();

                T result = currentValue;

                try {
                    advance();
                }
                catch( RuntimeException | Error ex ) {
                    closeAfterFailure(ex);
                    throw ex;
                }

                return result;
            }

            @Override
            public void close() throws DataSetException {
                if( closed )
                    return;

                closed = true;
                currentValue = null;
                hasCurrent = false;
                page = null;
                dataReader.close();
            }
        };
    }}
