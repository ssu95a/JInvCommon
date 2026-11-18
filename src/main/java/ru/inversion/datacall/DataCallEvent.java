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

    /** Результат EXECUTE after; null, если результат не был передан источником события. */
    final private Boolean executionSuccess;

    /** */
    public DataCallEvent( Object source, Type eventType, boolean before ) {
        this(source, eventType, before, null);
    }

    /** Событие EXECUTE after с подтверждённым результатом выполнения. */
    public DataCallEvent( Object source, Type eventType, boolean before, boolean executionSuccess ) {
        this(source, eventType, before, Boolean.valueOf(executionSuccess));
    }

    private DataCallEvent( Object source, Type eventType, boolean before, Boolean executionSuccess ) {
        super( source );
        this.eventType = eventType;
        this.before    = before;
        this.executionSuccess = executionSuccess;
    }

    /**
     * @return true, если этапы execute завершились без ошибки (это не означает commit);
     *         false после ошибки; null для событий без подтверждённого результата,
     *         включая старые реализации IDataCall.
     */
    public Boolean getExecutionSuccess() {
        return executionSuccess;
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
