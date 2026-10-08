package ru.inversion.db.expr;

import ru.inversion.utils.Tags;


/**
 *
 * @author ssu
 */
public class SQLParameterNotSetException extends SQLParameterException {

    final String parameter;

    /** */
    public SQLParameterNotSetException( String parameter ) {
        super( Tags.PRODUCT_LABEL + "For parameter '" + parameter + "' value is not set" );
        this.parameter = parameter;
    }
    
    /** */
    public SQLParameterNotSetException( Integer parameter ) {
        this( parameter == null ? (String)null : parameter.toString() );
    }

    /** */
    public SQLParameterNotSetException( String parameter, Throwable cause ) {
        super( Tags.PRODUCT_LABEL + "For parameter '" + parameter + "' value is not set", cause);
        this.parameter = parameter;
    }

    /** */
    public SQLParameterNotSetException( Integer parameter, Throwable cause ) {
        this( parameter == null ? (String)null : parameter.toString(), cause );
    }
    
    /** */
    public String getParameter() {
        return parameter;
    }
}
