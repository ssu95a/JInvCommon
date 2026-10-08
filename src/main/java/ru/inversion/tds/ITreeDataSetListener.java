package ru.inversion.tds;

import java.util.EventListener;

/**
 * Слушатель событий TreeDataSet.
 * <p>
 * Вызывается из TreeDataSet при наступлении событий.
 * Может быть зарегистрирован/отменен в TreeDataSet
 *
 * При добавлении одного и того же экземпляра слушателя несколько раз, будет зарегистрирован только один раз.
 * 
 * @see ITreeDataSet
 * @see TreeDataSetEvent
 * 
 * @author Sulimoff
 */
public interface ITreeDataSetListener<P> extends EventListener {
	
    /** 
     * Метод который вызывается при наступлении событий в TreeDataSet.
     * <p>
     * @param e
     *        Событие из TreeDataSet
     * 
     * @see TreeDataSetEvent
     */
	void dataSetChanged( TreeDataSetEvent<P> e );
}
