package ru.inversion.tds;

import java.util.EventListener;

/**
 * Слушатель событий связанных с пометкой.
 * <p>
 * @see TreeDataSetMarkEvent
 * 
 * @author Sulimoff
 */
public interface ITreeDataSetMarkListener<P> extends EventListener {
    void markAction( TreeDataSetMarkEvent<P> event );
}
