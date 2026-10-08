package ru.inversion.meta;

/**
 *
 * @author ssu
 */
public class EntityMetaDataException extends RuntimeException {
    
    /** */
    public EntityMetaDataException() {
    }

    /** */
    public EntityMetaDataException(String message) {
        super(message);
    }

    /** */
    public EntityMetaDataException(String message, Throwable cause) {
        super(message, cause);
    }

    /** */
    public EntityMetaDataException(Throwable cause) {
        super(cause);
    }
}
