package ru.inversion.utils.converter;

import ru.inversion.utils.IExceptionInfo;
import ru.inversion.utils.Tags;

/**
 *
 * @author ssu @
 */
public class TypeConverterException extends RuntimeException implements IExceptionInfo {

    /** */
    public TypeConverterException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public String getCategory() {
        return "TypeConverter";
    }
    
    /** */
    @Override
    public String getHeaderText() {
        return "The data conversion error";
    }
    
    /** */
    public static void fromValueToClass( Object value, Class clazz, Throwable cause, String conveter ) {
        throw new TypeConverterException(Tags.PRODUCT_LABEL + "Error converting value '" + String.valueOf(value) + "' in the data type '" + clazz.getName() + "'. Converter: " + conveter, cause );
    }

    /** */
    public static void fromClassToClass( Class fromClass, Class toClass, Throwable cause, String conveter ) {
        throw new TypeConverterException( Tags.PRODUCT_LABEL + "Error converting a value of type '" + fromClass.getName() + "' in the data type '" + toClass.getName() + "'. Converter: " + conveter, cause );
    }
}
