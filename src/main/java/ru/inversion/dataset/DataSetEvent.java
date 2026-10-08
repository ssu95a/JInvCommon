package ru.inversion.dataset;

import java.util.EventObject;

/**
 * Событие связанное с внутренними операциями и состоянием DataSet.
 * <p>
 * События внутри DataSet имеет 2-х фазное формирование: до самого действия, и после него.
 * 
 * @author sulimoff
 */
public class DataSetEvent extends EventObject {
    
	/** 
     * Тип события DataSet.
     */
	public enum DataSetEventType {
        
        /**
         * Выполняется запрос из источника данных.
         */
        EXECUTE,

        /**
         * Выполняется очистка DataSet.
         * Все данные удаляются.
         */
        // отменено, вместо - используется RowOperationEnum.CLEAR
		// CLEAR


        /**
          * На закрытие dataSet
          * Возможно использовать в связанных ресурсах
          */
        CLOSE,

        /**
         * Запрос к БД выполняется первый раз
         */
        FIRST_TIME_EXECUTE
	}
	
    /** 
     * Тип события
     */
	final private DataSetEventType eventType;
    
	/** 
     * Признак событие сформировано до действия или после.
     */
    final private boolean before;
    
    /** 
     * Создает новый объект события DataSet.
     * <p>
     * @param source
     *          DataSet формирующий событие
     * 
     * @param eventType
     *          тип события {@link DataSetEventType}
     * 
     * @param before
     *          признак перед действие сформировано событие или после него
     *          {@code true } перед
     *          {@code false} после
     */
	public DataSetEvent( IDataSet source, DataSetEventType eventType, boolean before ) {
		super(source);
		this.eventType = eventType;
      this.before    = before;
	}
    
    /**
     * @return тип события
     * 
     * @see DataSetEventType
     */
	public DataSetEventType getEventType( ) {
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
    final public boolean isAfter() {
        return !before;
    }

    /** 
     * for debug & log.
     */
    @Override
    public String toString() {
        return "DataSetEvent {" + ( before ? "before " : "after " ) + "eventType= " + eventType + '}';
    }
    
}
