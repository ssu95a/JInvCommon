package ru.inversion.dataset;

import java.util.List;

/**
 * "Фабрика" по создания коллекций (List) для хранения записей внутри DataSet.
 * <p>
 * @author sulimoff
 */
public interface IDataSetListFactory {
	/** 
     * Создает новый пустой List для хранения данных.
     * <p>
     * @param <T>
     *          тип записи в DataSet
     * @return 
     *          пустой List
     */
	<T> List<T> createDataSetList( );

    /** 
     * Создает новый пустой List для хранения данных.
     * <p>
     * @param <T>
     *          тип записи в DataSet
     * @param size
     *          размер создаваемой коллекции
     * @return 
     *          пустой List
     */
	<T> List<T> createDataSetList( int size );
    
	/** 
     * Создает новый List для хранения данных заполненный из переданной коллекции с данными.
     * <p>
     * @param <T>
     *          тип записи в DataSet
     * @param copyFrom
     *          коллекция из которой необходимо скопировать данные в новую коллекцию
     * @return 
     *          List с данными
     */
	<T> List<T> createDataSetList( List<T> copyFrom );
}
