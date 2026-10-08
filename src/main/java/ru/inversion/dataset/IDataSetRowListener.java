package ru.inversion.dataset;

import java.util.EventListener;

/**
 * Слушатель событий действий над записями внутри DataSet.
 * <p>
 * 
 * @see DataSetRowEvent
 * 
 * @author Sulimoff
 */
public interface IDataSetRowListener<T> extends EventListener {
	/** */
	void rowOperation( DataSetRowEvent<T> event );
}
