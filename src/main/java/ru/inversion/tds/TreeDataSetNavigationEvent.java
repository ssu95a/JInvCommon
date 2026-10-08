package ru.inversion.tds;


import java.util.EventObject;

/**
 * Событие TreeDataSet связанное с действиями навигации, перехода по узлам, внутри TreeDataSet.
 * <p>
 * Переход по записи связан с понятием текущий узел в TreeDataSet, который мб только один (если TreeDataSet не пустой)
 * В момент когда текущим узлом, становится другой и формируется это событие.
 *
 * @param <P>
 *        тип записи TreeDataSet
 *
 * @see ITreeDataSetNavigationListener
 *
 * @author Sulimoff
 */
public class TreeDataSetNavigationEvent<P> extends EventObject {

    /**
     * Предыдущая запись которая была текущей.
     */
    final private ITreeDataSetItem<P> oldItem;

    /**
     * Новая запись которая стала текущей.
     */
    final private ITreeDataSetItem<P> newItem;

    /**
     * Индекс предыдущей записи, которая была текущей.
     */
    final private int oldRowIndex;

    /**
     * Индекс новой записи, которая стала текущей.
     */
    final private int newRowIndex;

    /** */
    public TreeDataSetNavigationEvent( Object treeDataSet, ITreeDataSetItem<P> oldItem, ITreeDataSetItem<P> newItem /*, int oldRowIndex, int newRowIndex*/ ) {
        super(treeDataSet);
        this.oldItem     = oldItem;
        this.newItem     = newItem;
        this.oldRowIndex = -1; //oldRowIndex;
        this.newRowIndex = -1; //newRowIndex;
    }

    /**
     * @return
     *     предыдущую запись, которая была текущей
     */
    public ITreeDataSetItem<P> getOldItem( ) {
        return oldItem;
    }

    /**
     * @return
     *     новую запись, которая стала текущей
     */
    public ITreeDataSetItem<P> getNewItem( ) {
        return newItem;
    }

    /**
     * @return
     *     индекс предыдущей записи, в родительском наборе, которая была текущей
     */
    public int getOldRowIndex() {
        return oldRowIndex;
    }

    /**
     * @return
     *      индекс новой записи, в родительском наборе, которая стала текущей
     */
    public int getNewRowIndex() {
        return newRowIndex;
    }
}
