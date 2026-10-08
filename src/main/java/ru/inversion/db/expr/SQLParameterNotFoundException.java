package ru.inversion.db.expr;


/**
 *
 * @author ssu
 */
public class SQLParameterNotFoundException extends SQLParameterException {

    final String parameter;

    /** */
    public SQLParameterNotFoundException( String parameter ) {
        super("Parameter '" + parameter + "' is not found");
        this.parameter = parameter;
    }
    
    /** */
    public SQLParameterNotFoundException( Integer parameter ) {
        this( parameter == null ? (String)null : parameter.toString() );
    }

    /** */
    public SQLParameterNotFoundException( String parameter, Throwable cause ) {
        super("Parameter '" + parameter + "' is not found", cause);
        this.parameter = parameter;
    }

    /** */
    public SQLParameterNotFoundException( Integer parameter, Throwable cause ) {
        this( parameter == null ? (String)null : parameter.toString(), cause );
    }
    
    /** */
    public String getParameter() {
        return parameter;
    }
}
