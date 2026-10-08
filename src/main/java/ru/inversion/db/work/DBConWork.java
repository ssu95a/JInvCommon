package ru.inversion.db.work;

import java.sql.Connection;
import java.sql.SQLException;

/**
 *
 * @author ssu
 */
public interface DBConWork  extends AutoCloseable {

    Connection getConnection();
    
    void execute( ) throws SQLException;

    @Override
    default void close() throws Exception {}
}
