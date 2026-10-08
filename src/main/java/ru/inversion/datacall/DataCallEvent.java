package ru.inversion.datacall;

import java.util.EventObject;

/**
 * События связанные с объектом DataCall
 * */
public class DataCallEvent extends EventObject {

    public enum Type {

        EXECUTE,

        CLOSE
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
    public DataCallEvent( Object source, Type eventType, boolean before ) {
        super( source );
        this.eventType = eventType;
        this.before    = before;
    }
    /**
     * @return тип события
     *
     * @see Type
     */
    public Type getEventType( ) {
        return eventType;
    }

    /**
     * @return
     *      Признак что событие сформировано до действия
     */
    public boolean isBefore() {
        return before;
    }

    /**
     * @return
     *      Признак что событие сформировано после действия
     */
    public boolean isAfter() {
        return !before;
    }

    /**
     * for debug & log.
     */
    @Override
    public String toString() {
        return "DataCallEvent {" + ( before ? "before " : "after " ) + "eventType= " + eventType + '}';
    }
}
