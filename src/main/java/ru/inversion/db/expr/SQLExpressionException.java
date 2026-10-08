package ru.inversion.db.expr;

import java.sql.SQLException;
import ru.inversion.utils.IExceptionInfo;
import ru.inversion.utils.Tags;

/**
 *
 * @author ssu
 */
public class SQLExpressionException extends Exception implements IExceptionInfo {

    final private String content, detail;
    
    /** */
    public SQLExpressionException( ) {
        content = null;
        detail  = null;  
    }

    /** */
    public SQLExpressionException( String message ) {
        super(message);
        content = null;
        detail  = null;  
    }

    /** */
    public SQLExpressionException( String message, Throwable cause ) {
        super(message, cause);
        content = null;
        detail  = null;  
    }

    /** */
    public SQLExpressionException( Throwable cause ) {
        super(cause);
        content = null;
        detail  = null;  
    }

    /** */
    public SQLExpressionException( String title, String content, String detail, Throwable cause ) {
        super(title, cause);
        this.content = content;
        this.detail  = detail;  
    }
    /** */
    public SQLExpressionException( String title, String content, String detail ) {
        super(title);
        this.content = content;
        this.detail  = detail;  
    }

    /** */
    @Override
    public String getCategory() {
        return "PL/SQL Expression";
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
    public static void throwOnPrepareJDBCCallable( String sqlText, SQLException ex ) 
        throws SQLExpressionException 
    {
        throw new SQLExpressionException( ex ) {
            
            @Override
            public String getTitle() {
                return Tags.PRODUCT_LABEL + "Incorrect SQL expression";
            }
            @Override
            public String getContentText() {
                return "Call prepareCall(...) returned error.";
            }
            @Override
            public String getDetailedMessage() {
                return sqlText;
            }
        };
    }
}
