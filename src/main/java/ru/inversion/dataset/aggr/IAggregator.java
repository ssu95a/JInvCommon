package ru.inversion.dataset.aggr;

import ru.inversion.dataset.DataSetException;
import ru.inversion.dataset.IDataSet;
import ru.inversion.dataset.ISQLDataSet;

import java.util.Map;

/**
 * Интерфейс Агрегатор - механизм позволяющий, 
 * получить агрегированные данные из набора данных определенных в DataSet.
 * <p>
 * Строиться поверх DataSet
 * 
 * @param <T>
 *          тип записи в DataSet, поверх которого создан аггрегатор
 * 
 * @author Sulimoff
 */
public interface IAggregator<T> extends AutoCloseable {
    
    /**
     * Возвращает DataSet на котором построен аггрегатор.
     * <p>
     * @return
     *          DataSet
     * 
     * @see IDataSet 
     */
    IDataSet<T> getDataSet( );
    
    /** 
     * Возвращает текущие значения которые содержаться в аггрегаторе.
     * <p>
     * Аггрегатор может быть создан для вычисления сразу нескольких значений.
     * Которые вычисляются в момент вызова {@code execute}.
     * 
     * @return список значений
     */
    Map<String,Object> getValues();    
    
    /** 
     * Вызывает расчет значений аггрегатора.
     * <p>
     * Может быть вызван явно, из кода. 
     * Либо вызывается автоматически после каждого перезапроса в DataSet. 
     * см. {@link ISQLDataSet#createAggregatorBuilder(boolean, boolean)} {@code refreshOnExecute}
     * 
     * @throws 
     *          DataSetException если были ошибки при расчетах
     * 
     * @see ISQLDataSet#createAggregatorBuilder
     */
    void execute( ) throws DataSetException;
    
    /** 
     * Регистрирует слушателя событий аггрегатора.
     * <p>
     * @param 
     *          listener слушатель событий
     * 
     * @see IAggregatorListener
     * @see #removeListener
     */
    void addListener( IAggregatorListener<T> listener );
    
    /** 
     * Удаляет слушателя событий аггрегатора.
     * <p>
     * @param 
     *          listener слушатель событий
     * 
     * @see IAggregatorListener
     * @see #addListener
     */
    void removeListener( IAggregatorListener<T> listener );
}
