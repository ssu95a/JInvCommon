package ru.inversion.utils.lstn;

/**
 *
 * @author ssu
 */
public interface IListenerMan<L> {
    
    /** */
    void addListener( L listener );
    
    /** */
    void removeListener( L listener );
    
    /** */
    boolean isEmpty();
}
