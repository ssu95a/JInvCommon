package ru.inversion.tds;

import java.util.EventListener;

/**
 * Слушатель событий действий над записями внутри TreeDataSet.
 * <p>
 * 
 * @see TreeDataSetRowsEvent
 * 
 * @author Sulimoff
 */
public interface ITreeDataSetRowsListener<P> extends EventListener {
	/** */
	void rowsOperation(TreeDataSetRowsEvent<P> event );
}
