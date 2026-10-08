package ru.inversion.dataset;

import java.util.EventListener;

/**
 * Слушатель событий связанных с пометкой.
 * <p>
 * @see DataSetMarkEvent
 * 
 * @author Sulimoff
 */
public interface IDataSetMarkListener<T> extends EventListener {
    void markAction( DataSetMarkEvent<T> event );
}
