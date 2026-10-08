package ru.inversion.dataset;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Интерфейс для Rel->ОО отображения, маппинга, данных полученных из JDBC ResultSet в Java (pojo) представление.
 * <p>
 * Для реализации, в которой предполагается владение ссылкой на ResultSet, из которого нужно выбирать данные.
 * @param <P>
 *         тип данных Java в который необходимо осуществить преобразование.
 * 
 * @author sulimoff
 */
public interface IRSRowMapper<P> extends IRowMapper<P> {
    /**
     * @return 
     *  ResultSet из которого выбираются данные 
     */
    ResultSet getResultSet( );
	
	/**
     * Преобразует текущюю запись из ResultSet в Java представление.
     * <p>
     * @param rowNum
     *          номер записи
     * @return 
     *          Java объект (pojo) с данными из ResultSet
     * 
     * @throws java.sql.SQLException
     *         Если была ошибка при преобразовании
     * 
     * @see IRowMapper#mapRow(java.sql.ResultSet, int) 
     */
	P mapRow( int rowNum ) throws SQLException;
    
    /** 
     * Перегруженный метод из базового интерфейса.
     * <p>
     * @param rs
     *          не используется
     */
    @Override
    default P mapRow( ResultSet rs, int rowNum ) throws SQLException { return mapRow(rowNum); }
}
