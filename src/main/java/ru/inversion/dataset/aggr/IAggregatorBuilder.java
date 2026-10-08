package ru.inversion.dataset.aggr;

import ru.inversion.dataset.DataSetException;
import ru.inversion.dataset.IDataSet;

/**
 * Интерфейс описывающий поведение объекта создающего аггрегаторы.
 * <p>
 * Аггрегаторы всегда создаются на основе данных какого-то DataSet.
 * 
 * @param <T>
 *          Тип записи DataSet, на котором создаются аггрегаторы. 
 * 
 * @author Sulimoff
 */
public interface
IAggregatorBuilder<T> {

    /**
     * Возвращает DataSet на котором будут создаваться аггрегаторы.
     * <p>
     * @return 
     *          DataSet
     */
    IDataSet<T> getDataSet( );
    
    /**
     * Добавляет одно выражение для значения аггрегатора
     * <p>
     * @param columnExpr 
     *          столбец из DataSet или выражение, которое допустимо передать в аггрегирующую ф-цию.
     * @param func 
     *          аггрегирующая ф-ция, вычисляющая одно значение аггрегатора, {@link AggrFuncEnum}
     * @return  
     *          this
     */
    default IAggregatorBuilder<T> add( String columnExpr, AggrFuncEnum func ) {
        return add( columnExpr, func, null );
    }

    /** */
    IAggregatorBuilder<T> add( String columnExpr, AggrFuncEnum func, String alias );

    /**
     * Предикат, который может усилить предикат DataSet, для которого создаются аггрегаторы.
     * <p>
     * @param wherePredicate
     *        выражение предиката
     * @return  
     *        this
     */
    IAggregatorBuilder<T> addWherePredicate( String wherePredicate );
    
    /** 
     * Создает аггрегатор на основе установленных параметров.
     * <p>
     * @return 
     *          созданный аггрегатор
     * 
     * @throws DataSetException
     *          если были ошибки при создании 
     * 
     * @see IAggregator
     */
    public IAggregator<T> build( ) throws DataSetException;

    /** 
     * Создает аггрегатор в виде DataSet.
     * <p>
     * Объект подготавливающий аггрегирующие данные реализует интерфейс {@code IDataSet}
     * 
     * @return 
     *          созданный DataSet.
     * 
     * @throws DataSetException
     *          Если были ошибки при создании
     */
    public IDataSet<T> buildDataSet( ) throws DataSetException;
}
