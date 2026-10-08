package ru.inversion.dataset.aggr;

/**
 * Слушатель событий внутри аггрегатора.
 * <p>
 * Вызывается после обновления значений в аггрегаторе.
 * 
 * @param <T>
 *          Тип записи DataSet на котором построен аггрегатор.
 * 
 * @author Sulimoff
 */
public interface IAggregatorListener<T> {
    
    /**
     * Обработчик события.
     * <p>
     * @param source 9
     *          аггрегатор который вызвал это событие
     */
    void change( IAggregator<T> source );
}
