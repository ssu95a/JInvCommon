package ru.inversion.tds;

import java.util.EventListener;

/**
 * Слушатель событий действий над элементами внутри TreeDataSet.
 * <p>
 * @see TreeDataSetItemEvent
 * 
 * @author Sulimoff
 */
public interface ITreeDataSetItemListener<P> extends EventListener {
	/** */
	void itemChanged(TreeDataSetItemEvent<P> event );
}
