package ru.inversion.db;

import java.util.Collection;

/**
 *
 * @author ssu
 */
public class DbNoDataFoundException extends JInvDbException {
    
    static final private String ERR_MSG = "Not found record in the database";
    
    private final String contentText;
    
    /** */
    public DbNoDataFoundException() {
        super( ERR_MSG );
        contentText = null;
    }
    /** */
    public DbNoDataFoundException( String message ) {
        super( ERR_MSG );
        contentText = message;
    }
    /** */
    public DbNoDataFoundException( String message, Throwable cause ) {
        super( ERR_MSG, cause );
        contentText = message;
    }
    /** */
    public DbNoDataFoundException( Throwable cause ) {
        super( ERR_MSG, cause );
        contentText = null;
    }
    /** */
    public DbNoDataFoundException( Throwable cause, String sqlExpression, Collection<?> sqlParameters ) {
        super( ERR_MSG, cause, sqlExpression, sqlParameters);
        contentText = null;
    }
    /** */
    public DbNoDataFoundException(String sqlExpression, Collection<?> sqlParameters) {
        super( ERR_MSG, (Throwable)null, sqlExpression, sqlParameters );
        contentText = null;
    }
    /** */
    public DbNoDataFoundException(Throwable cause, String sqlExpression) {
        super( cause, sqlExpression);
        contentText = null;
    }
    /** */
    public DbNoDataFoundException(String message, String sqlExpression, Collection<?> sqlParameters) {
        super( ERR_MSG, sqlExpression, sqlParameters);
        contentText = message;
    }
    /** */
    public DbNoDataFoundException(String message, Throwable cause, String sqlExpression, Collection<?> sqlParameters) {
        super( ERR_MSG, cause, sqlExpression, sqlParameters);
        contentText = message;
    }
    /** */
    @Override
    public String getContentText() {
        return contentText;
    }
}
