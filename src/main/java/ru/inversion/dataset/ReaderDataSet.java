package ru.inversion.dataset;


import ru.inversion.utils.Tags;

import java.util.List;
import java.util.function.Function;

import static ru.inversion.dataset.DataSetRowEvent.RowOperationEnum.INSERT;

public class ReaderDataSet<T> extends AbstractDataSetBase<T> {

    private Function<IDataSet<T>,IDataReader<T>> readerSupplier;
    private IDataReader<T> dataReader;

    private int pageSize = 50;

    public ReaderDataSet( ) {
    }

    public ReaderDataSet( Class< ? extends T > rowClass, Function< IDataSet< T >, IDataReader< T > > readerSupplier ) {
        super(rowClass);
        this.readerSupplier = readerSupplier;
    }

    public ReaderDataSet( Function< IDataSet< T >, IDataReader< T > > readerSupplier ) {
        this.readerSupplier = readerSupplier;
    }

    public void setDataReaderSupplier( Function<IDataSet<T>,IDataReader<T>> readerSupplier ) {
        this.readerSupplier = readerSupplier;
    }

    public Function<IDataSet<T>,IDataReader<T>> getDataReaderSupplier( ) {
        return this.readerSupplier;
    }

    /**
     * Устанавливает размер страницы данных при подкачке.
     * Сколько данных за один раз будет подкачено из источника данных.
     * <p>
     * @param pageSize
     *          размер страницы, должен быть > 0
     *
     * @throws IllegalArgumentException
     *          если значение меньше или равно 0
     */
    public void setPageSize( int pageSize ) {

//        if( pageSize <= 0 )
//            throw new IllegalArgumentException( Tags.PRODUCT_LABEL + "pageSize must be > 0" );

        this.pageSize = Math.max( pageSize, -1 );
    }

    /**
     * Возвращает размер страницы установленный методом {@link #setPageSize(int) }.
     * <p>
     * @return размер страницы.
     */
    public int getPageSize() {
        return pageSize;
    }

    /**
     * Версия метода setPageSize в стиле "Fluent interface".
     * <p>
     * @param pageSize
     *          размер страницы, должен быть > 0
     * @return
     *          this
     *
     * @see #setPageSize
     */
    public ReaderDataSet<T> pageSize( int pageSize ) {
        setPageSize(pageSize); return this;
    }

    /** {@inheritDoc} */
    @Override
    public void executeQuery( ) throws DataSetException {
        executeQuery( false );
    }

    @Override
    public void executeQuery( boolean readAllRecords ) throws DataSetException {
        try {

            fireDataSetEvent( DataSetEvent.DataSetEventType.EXECUTE, true );

            if( dataReader != null ) {
                dataReader.close();
                dataReader = null;
            }

            clear( );

            Function<IDataSet<T>,IDataReader<T>> rs = getDataReaderSupplier();

            if( rs != null ) {

                dataReader = rs.apply(this);

                swappingData( readAllRecords ? -1 : getPageSize() );

                if( getLoadedRowCount() > 0 )
                    setCurrentRowNum( 0 );

            }

            fireDataSetEvent( DataSetEvent.DataSetEventType.EXECUTE, false );

        }
        catch( Throwable th ) {
            throw new DataSetException( Tags.PRODUCT_LABEL + "Error in dataSet on executeQuery", th );
        }

    }

    @Override
    public boolean swappingData() throws DataSetException {
        return swappingData( getPageSize() );
    }

    protected boolean swappingData( int nRowCount ) throws DataSetException {

        try {

            if( dataReader != null && !dataReader.isEOF() ) {

                int realRowCount = 0;
                int rowIndex     = Math.max(  rows().size() - 1, 0 );

                List<T> data = dataReader.getNextPage( nRowCount );

                realRowCount = data.size();

                if( realRowCount > 0 ) {

                    rows().addAll(data);

                    fireRowEvent(INSERT, null, rows().get(rowIndex), rowIndex, realRowCount);
                }

                return true;
            }
        } catch( Throwable ex) {
            throw new DataSetException( Tags.PRODUCT_LABEL + "Error on swap data. dataSetName = " + getName(), ex );
        }
        return false;
    }

    @Override
    public void close() throws Exception {

    }
}
