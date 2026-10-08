package ru.inversion.meta;

import ru.inversion.utils.IExceptionInfo;
import ru.inversion.utils.Tags;

import javax.persistence.PersistenceException;

/**
 *
 * @author ssu
 */
public class DMLWorkParameterException extends DMLWorkException {

    public DMLWorkParameterException(String message) {
        super(message);
    }

    public DMLWorkParameterException(String message, Throwable cause) {
        super(message, cause);
    }

    public DMLWorkParameterException(Throwable cause) {
        super(cause);
    }

    public static void throwOnSetParameterValue(int idx, String propertyName, String parameterName, Throwable cause) {
        throw new DMLWorkParameterException(
                Tags.PRODUCT_LABEL +
                        "Error setting parameter #" + idx +
                        ", property '" + propertyName + "'" +
                        ", parameter '" + parameterName + "'",
                cause
        );
    }

    public static void throwOnGetOutParameterValue(int idx, String propertyName, String parameterName, Throwable cause) {
        throw new DMLWorkParameterException(
                Tags.PRODUCT_LABEL +
                        "Error getting OUT parameter #" + idx +
                        ", property '" + propertyName + "'" +
                        ", parameter '" + parameterName + "'",
                cause
        );
    }
}
