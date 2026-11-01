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
     * Выполняет SQL запрос к БД для получения данных.
     * <p>
     * Которые можно затем получить методом {@link #getNextPage(int) }
     * @param callbackParameters
     *          параметры для SQL выражения
     * @throws DataSetException 
     *          если были ошибки при выполнении запроса
     */
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
