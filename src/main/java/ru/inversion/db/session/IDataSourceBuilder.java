package ru.inversion.db.session;

import javax.sql.DataSource;
import java.sql.SQLException;

/** */
public interface IDataSourceBuilder {
    DataSource build( ) throws SQLException;
}
