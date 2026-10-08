package ru.inversion.utils.lstn;

import java.util.EventObject;

/**
 *
 * @author ssu
 */
public interface IListenerManEvent<L,E extends EventObject> extends IListenerMan<L> {
    /**  */
    void fire( E event );
}
