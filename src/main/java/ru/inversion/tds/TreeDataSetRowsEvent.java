package ru.inversion.tds;

import ru.inversion.dataset.DataSetRowEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** */
public class TreeDataSetRowsEvent<P> extends TreeDataSetEventBase<P> {

    final DataSetRowEvent.RowOperationEnum itemOperation;

    /**
     * <h6>Запись до действия.</h6>
     * <p>
     * Используется при действиях: UPDATE, REFRESH, DELETE
     */
    private final List<ITreeDataSetItem<P>> items;

    /**
     * Индекс записи над которой производится действие.
     */
    private final int itemIndex;

    /** */
    public TreeDataSetRowsEvent(Object source, boolean before, DataSetRowEvent.RowOperationEnum itemOperation, List<ITreeDataSetItem<P>> items, int itemIndex)
    {
        super(source, before);
        this.itemOperation = itemOperation;
        this.items = items == null ? null : Collections.unmodifiableList( new ArrayList<>(items) );
        this.itemIndex     = itemIndex;
    }

    /** */
    public DataSetRowEvent.RowOperationEnum getItemOperation( ) {
        return itemOperation;
    }

    /** */
    public List<ITreeDataSetItem<P>> getItems() {
        return items;
    }

    /** */
    public int getItemIndex() {
        return itemIndex;
    }

    /**  Признак, который показывает что событие касается всех элементов в TreeDataSet */
    public boolean isAllItems()
    {
        return items == null;
    }
}
