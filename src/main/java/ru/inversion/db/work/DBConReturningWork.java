package ru.inversion.db.work;

import java.sql.Connection;
import java.sql.SQLException;

/**
 *
 * @author ssu
 */
public interface DBConReturningWork<T> extends AutoCloseable {
    
    /** */
    Connection getConnection();
    
    /** */
    T execute( ) throws SQLException;
    
    @Override
    default void close() throws Exception {}
}
