package ru.inversion.db;

import ru.inversion.utils.IExceptionInfo;
import ru.inversion.utils.Tags;

import javax.persistence.PersistenceException;
import java.util.Collection;

/**
 *
 * @author ssu @
 */
public class JInvDbException extends PersistenceException implements IExceptionInfo {

    static private String prepareDetail( String sqlExpression, Collection<?> sqlParameters) {

        StringBuilder sb = new StringBuilder();

        if( sqlExpression != null ) 
        {
            sb.append("SQL:").append('\n').append("----\n").append(sqlExpression).append('\n');

            if( sqlParameters != null && !sqlParameters.isEmpty() )
            {
                sb.append("\nparameters:");
                int i = 1;
                for( Object o : sqlParameters ) {
                     sb.append('\n').append(i++).append(":  ").append(o);
                }
            }//end if
        }
        return sb.toString();
    }

    final private String detailMessage;
    
    /** */
    public JInvDbException() {
        this( Tags.PRODUCT_LABEL+ "Database error when executing SQL" );
    }
    
    /** */
    public JInvDbException( String message ) {
        this( message, null );
    }

    /** */
    public JInvDbException( Throwable cause ) {
        this( null, cause );
    }
    
    /** */
    public JInvDbException( String message, Throwable cause ) {
        super(message, cause);
        detailMessage = null;
    }
    
    /** */
    public JInvDbException( Throwable cause, String sqlExpression, Collection<?> sqlParameters ) {
        super( Tags.PRODUCT_LABEL + "Database error when executing SQL", cause );
        detailMessage = prepareDetail( sqlExpression, sqlParameters );
    }
    
    /** */
    public JInvDbException( Throwable cause, String sqlExpression ) {
        this( cause, sqlExpression, (Collection<?>)null );
    }

    /** */
    public JInvDbException( String message, String sqlExpression, Collection<?> sqlParameters ) {
        super( message );
        detailMessage = prepareDetail( sqlExpression, sqlParameters );
    }
    
    /** */
    public JInvDbException( String message, Throwable cause, String sqlExpression, Collection<?> sqlParameters ) {
        super( message, cause );
        detailMessage = prepareDetail( sqlExpression, sqlParameters );
    }
    
    /** */
    @Override
    public String getDetailedMessage() {
        return detailMessage;
    }
    
    /** */
    @Override
    public String getCategory() { 
        return "dbError"; 
    }

    /** */
    public static void throwNoDataFound( String sqlExpression, Collection<?> sqlParameters ) {
        throw new DbNoDataFoundException( Tags.PRODUCT_LABEL + "No record found with the given parameters", sqlExpression, sqlParameters );
    }
}
