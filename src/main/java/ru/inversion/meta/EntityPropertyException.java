package ru.inversion.meta;

import ru.inversion.utils.IExceptionInfo;
import ru.inversion.utils.S;
import ru.inversion.utils.Tags;

/**
 *
 * @author ssu
 */
public class EntityPropertyException extends RuntimeException implements IExceptionInfo {

    /** */
    public EntityPropertyException(String message, Throwable cause) {
        super(message, cause);
    }

    /** */
    private static Throwable unwrapReflectionException(Throwable th) {
        if( th instanceof java.lang.reflect.InvocationTargetException && th.getCause() != null)
            return th.getCause();
        return th;
    }

    /** */
    public static void throwOnInvokeSetter( String propertyName, Class<?> propertyType, String setterName, Object value, Throwable th ) {

        Throwable cause = unwrapReflectionException(th);

        if( cause instanceof IllegalAccessException )
        {
            throw new EntityPropertyException (
                Tags.PRODUCT_LABEL
                    + "Invalid POJO declaration for property '"
                    + propertyName
                    + "'. Setter '"
                    + setterName
                    + "' is not accessible from metadata layer. "
                    + "Describe POJO correctly: entity class and accessor methods must be public.",
                cause
            );
        }

        final StringBuilder sb = new StringBuilder();
        sb.append( "Error when calling set " )
          .append( setterName == null ? S.EMPTY_STRING : ("(" + setterName + ") " ) )
          .append( " for property '")
          .append( propertyName )
          .append( ", type '")
          .append( propertyType.getName() )
          .append("', parameter type - ")
          .append( value == null ? "null" : value.getClass().getName() );
        
        throw new EntityPropertyException( Tags.PRODUCT_LABEL + sb, th );
    }

    /** */
    public static void throwOnInvokeGetter( String propertyName, String getterName, Throwable th ) {

        Throwable cause = unwrapReflectionException(th);

        if( cause instanceof IllegalAccessException ) {
            throw new EntityPropertyException (
                    Tags.PRODUCT_LABEL
                            + "Invalid POJO declaration for property '"
                            + propertyName
                            + "'. Getter '"
                            + getterName
                            + "' is not accessible from metadata layer. "
                            + "Describe POJO correctly: entity class and accessor methods must be public.",
                    cause
            );
        }

        throw new EntityPropertyException( Tags.PRODUCT_LABEL + "Error when calling  get " + ( getterName == null ? S.EMPTY_STRING : ("(" + getterName + ") " ) ) + " for property " + propertyName, th );
    }

    /** */
    @Override
    public String getCategory( ) {
        return "meta";
    }
}
