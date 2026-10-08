package ru.inversion.dataset;

import java.util.List;

/**
 * DataSet построенный на коллекции (Array) данных.
 * Предназначен только для заполнения собственными методами, без источника данных.
 *
 * @author sulimoff
 */
public class ArrayDataSet<T> extends AbstractDataSetBase<T> {

    public ArrayDataSet() {
    }

    public ArrayDataSet( Class<? extends T>  rowClass ) {
        super(rowClass);
    }

    public ArrayDataSet( List<T> rows, boolean doCopy ) {
        super( rows, doCopy );
    }

    public ArrayDataSet( Class<? extends T> rowClass, List<T> rows, boolean doCopy ) {
        super( rowClass, rows, doCopy );
    }

    /** */
	@Override
	public void executeQuery( boolean readAllRecords ) throws DataSetException {
        if( !isEmpty() )
             setCurrentRowNum( 0 );
	}

    /** */
	@Override
	public boolean swappingData( ) throws DataSetException {
        return false;
	}

    @Override
    public void close() throws Exception {
        rows().clear();
    }
}
