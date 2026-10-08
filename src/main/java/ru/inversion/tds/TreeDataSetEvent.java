package ru.inversion.tds;

import ru.inversion.dataset.DataSetEvent;

/**
 * Событие связанное с внутренними операциями и состоянием DataSet.
 * <p>
 * События внутри DataSet имеет 2-х фазное формирование: до самого действия, и после него.
 *
 * @author sulimoff
 */
public class TreeDataSetEvent<P> extends TreeDataSetEventBase<P> {

    /**
     * Тип события
     */
    final private DataSetEvent.DataSetEventType eventType;

    /**
     * Создает новый объект события DataSet.
     * <p>
     * @param source
     *        DataSet формирующий событие
     *
     * @param eventType
     *        тип события {@link DataSetEvent.DataSetEventType}
     *
     * @param before
     *        признак перед действие сформировано событие или после него
     *        {@code true } перед
     *        {@code false} после
     */
    public TreeDataSetEvent(AbstractTreeDataSet<P> source, DataSetEvent.DataSetEventType eventType, boolean before ) {
        super(source,before);
        this.eventType = eventType;
    }

    /**
     * @return тип события
     *
     * @see DataSetEvent.DataSetEventType
     */
    public DataSetEvent.DataSetEventType getEventType( ) {
        return eventType;
    }

    /**
     * for debug & log.
     */
    @Override
    public String toString() {
        return "DataSetEvent {" + ( isBefore() ? "before " : "after " ) + "eventType= " + eventType + '}';
    }

}
