package ru.inversion.dataset;

import java.util.EventListener;

/**
 * Слушатель событий связанных с навигацией по DataSet.
 * <p>
 * Вызывает в ответ на переходы по записям внутри DataSet.
 * 
 * @param <T>
 *          Тип записи в DataSet
 * 
 * @see DataSetNavigationEvent
 * 
 * @author Sulimoff
 */
public interface IDataSetNavigationListener<T> extends EventListener {
	/**
     * @param e
     *         Данные события навигации из DataSet
     */
	void navigated( DataSetNavigationEvent<T> e );
}
