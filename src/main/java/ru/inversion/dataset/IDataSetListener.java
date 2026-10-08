package ru.inversion.dataset;

import java.util.EventListener;

/**
 * Слушатель событий DataSet.
 * <p>
 * Вызывается из DataSet при наступлении событий.
 * Может быть зарегистрирован/отменен в DataSet через 
 * {@link IDataSet#addDataSetListener(ru.inversion.dataset.IDataSetListener)}/{@link IDataSet#removeDataSetListener(ru.inversion.dataset.IDataSetListener)}
 *
 * При добавлении одного и того же экземпляра слушателя несколько раз, будет зарегистрирован только один раз.
 * 
 * @see IDataSet
 * @see DataSetEvent
 * 
 * @author sulimoff
 */
public interface IDataSetListener extends EventListener {
	
    /** 
     * Метод который вызывается при наступлении событий в DataSet.
     * <p>
     * @param e
     *          Событие из DataSet
     * 
     * @see DataSetEvent
     */
	void dataSetChanged( DataSetEvent e );
}
