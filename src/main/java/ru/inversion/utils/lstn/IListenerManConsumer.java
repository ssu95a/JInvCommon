package ru.inversion.utils.lstn;

import java.util.function.Consumer;

/**
 *
 * @author ssu
 */
public interface IListenerManConsumer<L> extends IListenerMan<L> {
    /** */
    void fire( Consumer<L> consumer );
}
