package ru.inversion.datacall;

import java.util.EventListener;

/** */
public interface IBatchListener<T> extends EventListener {
    /** */
    void batchOperation( BatchEvent<T> e );
}
