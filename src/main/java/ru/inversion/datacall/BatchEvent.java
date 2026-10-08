package ru.inversion.datacall;

import java.util.EventObject;

public class BatchEvent<T> extends EventObject {

    public enum Type {

        EXECUTE,

        CLOSE,

        PREPARE_ITEM,

        PROCESS_BATCH
    }

    /**
     * Тип события
     */
    final private Type eventType;

    /**
     * Признак событие сформировано до действия или после.
     */
    final private boolean before;

    /** */
    final private int dataIndex;

    /** */
    final private T item;

    /** */
    final private Throwable throwable;

    /**
     * Constructs a prototypical Event.
     *
     * @param source The object on which the Event initially occurred.
     * @param eventType
     * @param before
     * @param item
     * @param throwable
     * @throws IllegalArgumentException if source is null.
    */
    public BatchEvent( Object source, Type eventType, boolean before, int dataIndex, T item, Throwable throwable ) {
        super(source);
        this.eventType = eventType;
        this.before    = before;
        this.dataIndex = dataIndex;
        this.item      = item;
        this.throwable = throwable;
    }

    public Type getEventType() {
        return eventType;
    }

    public boolean isBefore() {
        return before;
    }
    public boolean isAfter() {
        return !before;
    }

    public int getDataIndex() {
        return dataIndex;
    }

    public T getItem() {
        return item;
    }

    public Throwable getThrowable() {
        return throwable;
    }
}
