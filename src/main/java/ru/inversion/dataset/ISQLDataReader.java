package ru.inversion.dataset;

import java.util.List;

/**
 * Интерфейс для поставщика, источника данных для DataSet построенный на SQL выражении.
 * <p>
 * @param <T>
 *          тип данных записи получаемой из источника данных
 * 
 * @author sulimoff
 */
public interface ISQLDataReader<T> extends IDataReader<T> {
    /**
     * Legacy execution entry point where the reader still resolves SQL parameters.
     * <p>
     * New DataSet code must resolve values before the JDBC boundary and call
     * {@link #executeQuery(List)} instead.
     *
     * @deprecated resolve parameters at DataSet/builder level.
     */
    @Deprecated
    void executeQuery( IParameters callbackParameters ) throws DataSetException;

    /**
     * Выполняет SQL с уже разрешёнными значениями в физическом JDBC-порядке.
     * <p>
     * Default оставлен для source compatibility с внешними реализациями
     * {@code ISQLDataReader}; JDBCDataReader реализует этот путь.
     */
    default void executeQuery( List<Object> jdbcValues ) throws DataSetException {
        throw new UnsupportedOperationException(
                "executeQuery(List<Object>)");
    }
}
