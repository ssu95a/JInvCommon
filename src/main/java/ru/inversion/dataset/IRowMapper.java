package ru.inversion.dataset;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Интерфейс для Rel->ОО отображения, маппинга, данных полученных из JDBC ResultSet в Java (pojo) представление.
 * <p>
 * @param <T>
 *          тип данных Java в который необходимо осуществить преобразование.
 * 
 * @author sulimoff
 */
@FunctionalInterface
public interface IRowMapper<T> {

	final static IRowMapper EMPTY_MAPPER = new IRowMapper() {
		@Override
		public Object mapRow( ResultSet rs, int rowNum ) throws SQLException {
			return null;
		}
	};

	/**
     * Преобразует текущюю запись из ResultSet в Java представление.
     * <p>
     * @param rs
     *          JDBC ResultSet из которого нужно осуществить преобразование.
     * @param rowNum
     *          номер записи 
     * @return 
     *          Java объект (pojo) с данными из ResultSet
     * 
     * @throws java.sql.SQLException 
     *          если были ошибки при преобразовании
     */
	T mapRow( ResultSet rs, int rowNum ) throws SQLException;
}
