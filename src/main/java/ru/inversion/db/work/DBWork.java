package ru.inversion.db.work;

import java.sql.Connection;
import java.sql.SQLException;

/**
 *
 * @author ssu
 */
public interface DBWork extends AutoCloseable {
    
    void execute( Connection connection ) throws SQLException;
    
    @Override
    default void close() throws Exception {}
}
