package ru.inversion.dataset;

import java.util.EventObject;
import static ru.inversion.dataset.DataSetMarkEvent.MarkActionEnum.*;
import ru.inversion.utils.S;
import ru.inversion.utils.U;

/**
 * Событие DataSet формируемое при действиях c пометкой.
 * <p>
 * Пометка может быть как групповой, так и по одной записи.
 * 
 * @see IDataSetMarkListener
 * 
 * @author sulimoff
 */
public class DataSetMarkEvent<T> extends EventObject {

    /** 
     * Тип действия по пометке.
     */
    public enum MarkActionEnum { 
        
        /**
         * Пометить одну запись.
         */
        MARK_ROW, 
        
        /** 
         * Снять пометку с одной записи.
         */
        UNMARK_ROW, 
        
        /** 
         * Пометить все записи.
         */
        MARK_ALL, 
        
        /** 
         * Снять пометку со всех записей.
         */
        UNMARK_ALL, 
        
        /** 
         * Обновить данные пометки.
         */
        REFRESH 
    };
    
    /**
     * Тип действия
     */
    final private MarkActionEnum markAction;
    
    /** 
     * Индекс записи для которой снимается, устанавливаемся пометка.
     * Для групповых операций -1.
     */
    final private int index;
    
    /** 
     * Количество всего помеченных записей в DataSet после последнего действия связанного с пометкой.
        final private int  totalMark;
     */
    
    /**
     * Запись для которой установлена или снята пометка.
     * Для групповых операций {@code null}
     */
    final private T    row;
    
    /** 
     * Создает событие для группового действия пометки.
     * <p>
     * @param source
     *          DataSet сформировавший событие
     * @param markAction
     *          Действие пометки, должно быть: MARK_ALL, UNMARK_ALL, REFRESH
     * @param totalMark
     *          кол-во всего записей помеченных в DataSet после этого действия
     */
    public DataSetMarkEvent( Object source, MarkActionEnum markAction /*, int totalMark*/ ) {
        super(source);
        
        if( U.notIn(markAction, MARK_ALL, UNMARK_ALL, REFRESH) )
            throw new IllegalArgumentException( "markAction must be MARK_ALL, UNMARK_ALL or REFRESH");
        
        this.markAction = markAction;
        this.index      = -1;
//        this.totalMark  = totalMark;
        this.row        = null;
    }
    
    /**
     * Создает событие для действия пометки по одной записи.
     * <p>
     * @param source
     *          DataSet сформировавший событие
     * @param markAction
     *          Действие пометки, должно быть: MARK_ROW, UNMARK_ROW
     * @param index
     *          Индекс обрабатываемой записи внутри DataSet
     * @param totalMark
     *          Кол-во всего записей помеченных в DataSet после этого действия
     * @param row 
     *          Запись помечаемая/разпомечаемая
     */
    public DataSetMarkEvent( Object source, MarkActionEnum markAction, int index, /*int totalMark,*/ T row ) {
        super(source);
        
        if( U.notIn( markAction, MARK_ROW, UNMARK_ROW ) )
            throw new IllegalArgumentException( "markAction must be MARK_ROW or UNMARK_ROW");
        
        this.markAction = markAction;
        this.index      = index;
//        this.totalMark  = totalMark;
        this.row        = row;
    }
    
    /**
     * @return  
     *      тип действия пометки
     */
    public MarkActionEnum getMarkAction() {
        return markAction;
    }
    
    /** 
     * @return 
     *      индекс обрабатываемой записи, -1 для групповых 
     */
    public int getIndex() {
        return index;
    }
    
    /** 
     * @return 
     *      всего помеченных записей внутри DataSet, после этого действия пометки
    public int getTotalMark() {
        return totalMark;
    }
    */
    
    /**
     * @return 
     *      помечаемая/разпомечаемая запись, {@code null} для групповых
     */
    public T getRow() {
        return row;
    }

    /** 
     * for debug & log.
     */
    @Override
    public String toString() {
        return "DataSetMarkEvent {" + "markAction=" + markAction + ", index=" + index + ", row=" + S.nvl(row) + '}';
    }
    
}
