package ru.inversion.db.session;

import java.sql.Connection;
import java.sql.SQLException;

public interface IDataSourceListener {

    /** */
    void afterCreateConnection( Connection c ) throws SQLException;

    /** */
    void beforeCloseConnection( Connection c ) throws SQLException;
}
