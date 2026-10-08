package ru.inversion.db.work;

import ru.inversion.dataset.IParameters;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * @author ssu
 */
public interface DBConParametersWork extends DBConWork, IParameters {
    /** */
    public static DBConParametersWork EMPTY() {
        return new DBConParametersWork() {
            @Override
            public Connection getConnection() {
                return null;
            }
            @Override
            public void execute() throws SQLException {

            }
            @Override
            public Object getParameter( String parameterName ) {
                return null;
            }
            @Override
            public Object getParameter( int parameterIndex ) {
                return null;
            }
        };
    }
}
