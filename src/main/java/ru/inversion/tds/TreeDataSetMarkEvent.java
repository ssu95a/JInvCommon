package ru.inversion.tds;

import ru.inversion.dataset.DataSetMarkEvent;
import ru.inversion.utils.U;

import static ru.inversion.dataset.DataSetMarkEvent.MarkActionEnum.*;

/**
 * Событие DataSet формируемое при действиях c пометкой.
 * <p>
 * Пометка может быть как групповой, так и по одной записи.
 * 
 * @see ITreeDataSetMarkListener
 * 
 * @author Sulimoff
 */
public class TreeDataSetMarkEvent<P> extends TreeDataSetEventBase<P> {

    /**
     * Тип действия
     */
    final private DataSetMarkEvent.MarkActionEnum markAction;

    /**
     * Узел для которого установлена или снята пометка.
     * Для групповых операций {@code null}
     */
    final private ITreeDataSetItem<P> item;

    /**
     * Кол-во помеченных записей до или после операции
     * */
    final private int markedCount;

    /**
     * Признак что обработан только листовой(е) элемент
     * */

    final private boolean isLeaf;

    /**
     * Создает событие для группового действия пометки.
     * <p>
     * @param source
     *          DataSet сформировавший событие
     * @param markAction
     * @param markedCount
     */
    public TreeDataSetMarkEvent( Object source, DataSetMarkEvent.MarkActionEnum markAction, boolean before, int markedCount, boolean isLeaf ) {
        super(source, before);

        if( U.notIn(markAction, MARK_ALL, UNMARK_ALL, REFRESH) )
            throw new IllegalArgumentException( "markAction must be MARK_ALL, UNMARK_ALL or REFRESH");

        this.markAction = markAction;
        this.item       = null;
        this.markedCount= markedCount;
        this.isLeaf     = isLeaf;
    }

    /**
     * Создает событие для действия пометки по одной записи.
     * <p>
     * @param source
     *        DataSet сформировавший событие
     * @param markAction
     *        Действие пометки, должно быть: MARK_ROW, UNMARK_ROW
     * @param item
     * @param markedCount
     */
    public TreeDataSetMarkEvent( Object source, DataSetMarkEvent.MarkActionEnum markAction, ITreeDataSetItem< P > item, boolean before, int markedCount ) {
        super(source, before);

        if( U.notIn( markAction, MARK_ROW, UNMARK_ROW ) )
            throw new IllegalArgumentException( "markAction must be MARK_ROW or UNMARK_ROW");
        
        this.markAction  = markAction;
        this.item        = item;
        this.markedCount = markedCount;
        this.isLeaf      = item.isLeaf();
    }
    
   /**
    * @return
    *      тип действия пометки
    */
    public DataSetMarkEvent.MarkActionEnum getMarkAction() {
        return markAction;
    }

   /**
    * @return
    *      помечаемая/разпомечаемая запись, {@code null} для групповых
    */
    public ITreeDataSetItem<P> getItem() {
        return item;
    }

    /** */
    public int getMarkedCount() {
        return markedCount;
    }

    /** */
    public boolean isLeaf() { return isLeaf; }
}
