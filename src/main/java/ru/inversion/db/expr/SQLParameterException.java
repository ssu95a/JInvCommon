package ru.inversion.db.expr;

import ru.inversion.utils.IExceptionInfo;
import ru.inversion.utils.S;
import ru.inversion.utils.Tags;

/**
 *
 * @author ssu
 */
public class SQLParameterException extends SQLExpressionException implements IExceptionInfo {
    
    /** */
    public SQLParameterException( ) {
    }

    /** */
    public SQLParameterException( String message ) {
        super(message);
    }

    /** */
    public SQLParameterException( String message, Throwable cause ) {
        super(message, cause);
    }

    /** */
    public SQLParameterException( Throwable cause ) {
        super(cause);
    }
    
    /** */
    public static void onSet( String parameterName, Object value, Throwable cause ) throws SQLParameterException {
        throw new SQLParameterException( Tags.PRODUCT_LABEL + "Error when setting parameter  '" + parameterName + "' value: " + S.nvl( value ) + ", paramClass = " + ( value == null ? "<null>" : value.getClass().getName() ), cause );
    }
    
    /** */
    public static void onSet( int parameterIndex, Object value, Throwable cause ) throws SQLParameterException {
        throw new SQLParameterException( Tags.PRODUCT_LABEL + "Error when setting parameter '" + parameterIndex + "', value: " + S.nvl( value ) + ", paramClass = " + ( value == null ? "<null>" : value.getClass().getName() ), cause );
    }

    /** */
    public static void onSetInStatement( int parameterIndex, Object value, Throwable cause ) throws SQLParameterException {
        throw new SQLParameterException( Tags.PRODUCT_LABEL + "Error when setting parameter " + parameterIndex + " in CallableStatemet, value: " + S.nvl( value ) + ", paramClass = " + ( value == null ? "<null>" : value.getClass().getName() ), cause );
    }
    
    /** */
    public static void onGet( String parameterName, Throwable cause ) throws SQLParameterException {
        throw new SQLParameterException( Tags.PRODUCT_LABEL + "Error when getting parameter value '" + parameterName, cause );
    }

    /** */
    public static void onGet( int parameterIndex, Throwable cause ) throws SQLParameterException {
        throw new SQLParameterException( Tags.PRODUCT_LABEL + "Error when getting parameter value '" + parameterIndex + "'", cause );
    }
    
    /** */
    public static void onLoadMeta( Throwable cause ) throws SQLParameterException {
        throw new SQLParameterException( Tags.PRODUCT_LABEL + "Error loading information about a parameter", cause );
    }
}
