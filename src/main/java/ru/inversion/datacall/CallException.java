package ru.inversion.datacall;

import ru.inversion.utils.IExceptionInfo;
import ru.inversion.utils.Tags;

import javax.persistence.PersistenceException;

/**
 *
 * @author ssu
 */
public class CallException extends PersistenceException implements IExceptionInfo {

    final private String content, detail;

    /** */
    public CallException( ) {
        content = null;
        detail  = null;
    }

    /** */
    public CallException( String message ) {
        super(message);
        content = null;
        detail  = null;
    }

    /** */
    public CallException( String message, Throwable cause ) {
        super(message, cause);
        content = null;
        detail  = null;
    }

    /** */
    public CallException( Throwable cause ) {
        super(cause);
        content = null;
        detail  = null;
    }

    /** */
    public CallException( String title, String content, String detail, Throwable cause ) {
        super(title, cause);
        this.content = content;
        this.detail  = detail;
    }
    /** */
    public CallException( String title, String content, String detail ) {
        super(title);
        this.content = content;
        this.detail  = detail;  
    }

    /** */
    @Override
    public String getCategory() {
        return "PL/xxx statement";
    }

    /** */
    @Override
    public String getContentText() {
        return content;
    }

    @Override
    public String getDetailedMessage() {
        return detail;
    }
    
    /** */
    public static void throwOnPrepareJDBCCallable( String sqlText, Throwable th )
        throws CallException
    {
        throw new CallException( th ) {
            @Override
            public String getMessage() {
                return Tags.PRODUCT_LABEL + "Error on create and init JDBC CallableStatement";
            }
            @Override
            public String getContentText() {
                return "Errors occurred when calling the methods of the Connection and CallableStatement JDBC objects";
            }
            @Override
            public String getDetailedMessage() {
                return sqlText;
            }
        };
    }
}
