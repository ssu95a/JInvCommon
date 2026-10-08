package ru.inversion.csv;

import ru.inversion.utils.IExceptionInfo;

/** */
public class CsvException extends RuntimeException implements IExceptionInfo {
    /** */
    public CsvException( String message ) {
        super(message);
    }
    public CsvException( String message, Throwable cause ) {
        super(message, cause);
    }
    /** */
    public CsvException( Throwable cause ) {
        super(cause);
    }
    /** */
    @Override
    public String getCategory() {
        return "CSV";
    }
}
