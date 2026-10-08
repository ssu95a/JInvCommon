package ru.inversion.dataset;

import java.util.EventObject;

/**
 * Событие DataSet связанное с действиями навигации, перехода по записям, внутри DataSet.  
 * <p>
 * Переход по записи связан с понятием текущая запись в DataSet, которая мб только одна (если DataSet не пустой)
 * В момент когда текущей записью, становиться другая и формируется это событие.
 * @param <T>
 *          тип записи DataSet
 * 
 * @see IDataSetNavigationListener
 * 
 * @author sulimoff
 */
public class DataSetNavigationEvent<T> extends EventObject {
    
    /** 
     * Предыдущая запись которая была текущей.
     */
	final private T oldRow; 
    
    /** 
     * Новая запись которая стала текущей.
     */
    final private T newRow;
    
    /** 
     * Индекс предыдущей записи, которая была текущей.
     */
	final private int oldRowIndex;
    
    /** 
     * Индекс новой записи, которая стала текущей.
     */
    final private int newRowIndex;  
	
	public DataSetNavigationEvent( Object source, T oldRow, T newRow, int oldRowIndex, int newRowIndex ) {
		super(source);
		this.oldRow = oldRow;
		this.newRow = newRow;
		this.oldRowIndex = oldRowIndex; 
		this.newRowIndex = newRowIndex;		
	}
    
	/**
     * @return  
     *      предыдущую запись, которая была текущей
     */
	public T getOldRow( ) {
		return oldRow;
	}
    
	/**
     * @return  
     *      новую запись, которая стала текущей
     */
	public T getNewRow( ) {
		return newRow;
	}
    
	/**
     * @return  
     *      индекс предыдущей записи, которая была текущей
     */
	public int getOldRowIndex() {
		return oldRowIndex;
	}
    
	/**
     * @return  
     *      индекс новой записи, которая стала текущей
     */
	public int getNewRowIndex() {
		return newRowIndex;
	}
}
