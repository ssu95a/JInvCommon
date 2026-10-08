package ru.inversion.tds;

import java.util.EventListener;

/**
 * Слушатель событий связанных с навигацией по TreeDataSet.
 * <p>
 * Вызывает в ответ на переходы по записям внутри TreeDataSet.
 * 
 * @param <P>
 *          Тип записи в DataSet
 * 
 * @see TreeDataSetNavigationEvent
 * 
 * @author Sulimoff
 */
public interface ITreeDataSetNavigationListener<P> extends EventListener {
	/**
     * @param e
     * 	   Данные события навигации из TreeDataSet
     */
	void navigated( TreeDataSetNavigationEvent<P> e );
}
