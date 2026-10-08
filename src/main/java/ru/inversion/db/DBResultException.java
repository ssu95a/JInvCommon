package ru.inversion.db;

/** */
public class DBResultException extends Exception {

    public DBResultException() {

    }

    public DBResultException( String message ) {
        super(message);
    }

    public DBResultException( String message, Throwable cause ) {
        super(message, cause);
    }

    public DBResultException( Throwable cause ) {
        super(cause);
    }
}
