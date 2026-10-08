package ru.inversion.dataset;

import ru.inversion.utils.IExceptionInfo;

/**
 * Базовый класс исключения для ошибочных ситуаций при работе с DataSet.
 * <p>
 * @see IExceptionInfo
 * 
 * @author sulimoff
 */
public class DataSetException extends Exception implements IExceptionInfo {
    /** */
	public DataSetException( String message ) {
		super(message);
	}
    /** */
	public DataSetException( String message, Throwable cause ) {
		super(message, cause);
	}
    /** */
	public DataSetException( Throwable cause ) {
		super(cause);
	}
    /** */
    @Override
    public String getCategory( ) {
        return "DataSet";
    }
}
