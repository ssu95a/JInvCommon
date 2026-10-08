package ru.inversion.db.rs;

import ru.inversion.utils.IExceptionInfo;
import ru.inversion.utils.Tags;

/**
 *
 * @author ssu
 */
public class RSException extends RuntimeException implements IExceptionInfo {

    private final String sqlExpression;
    /** */
    public RSException() {
        this.sqlExpression = null;
    }
    /** */
    public RSException(String message) {
        super(message);
        this.sqlExpression = null;
    }
    /** */
    public RSException(String message, Throwable cause) {
        super(message, cause);
        this.sqlExpression = null;
    }
    /** */
    public RSException(Throwable cause) {
        super(cause);
        this.sqlExpression = null;
    }
    /** */
    public RSException( String message, String sqlExpression, Throwable cause ) {
        super(message, cause);
        this.sqlExpression = sqlExpression;
    }
    /** */
    @Override
    public String getDetailedMessage() {
        return sqlExpression;
    }
    /** */
    @Override
    public String getTitle() {
        return null;
    }
    /** */
    @Override
    public String getCategory( ) {
        return "RS";
    }

    /** */
    public static void throwOnCreateIterableOnSQL(String sqlExpression, Class clazz, Throwable cause) {
        throw new RSException( Tags.PRODUCT_LABEL + "Error when creating an 'Iterable' to an SQL expression. For class " + clazz.getName(), sqlExpression, cause );
    }
    /** */
    public static void throwOnCreateIterableOnEntity( Class clazz, Throwable cause ) {
        throw new RSException( Tags.PRODUCT_LABEL + "Error when creating an 'Iterable' for class " + clazz.getName(), cause );
    }
    /** */
    public static void throwOnCreateIterableOnResultSet( Class clazz, Throwable cause ) {
        throw new RSException( Tags.PRODUCT_LABEL + "Error when creating an 'Iterable' to an ResultSet. For class " + clazz.getName(), cause );
    }
}
