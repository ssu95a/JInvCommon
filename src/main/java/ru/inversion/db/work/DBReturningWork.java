package ru.inversion.db.work;

import java.sql.Connection;
import java.sql.SQLException;

/**
 *
 * @author ssu
 */
public interface DBReturningWork<T> extends AutoCloseable {
    
    public T execute(Connection connection) throws SQLException;

    @Override
    default void close() throws Exception {}
}
