package ru.inversion.tds;

/** */
public class TreeDataSetException extends RuntimeException {

    public TreeDataSetException() {
    }

    public TreeDataSetException(String message) {
        super(message);
    }

    public TreeDataSetException(String message, Throwable cause) {
        super(message, cause);
    }

    public TreeDataSetException(Throwable cause) {
        super(cause);
    }
}
