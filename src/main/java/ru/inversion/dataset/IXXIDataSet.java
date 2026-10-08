package ru.inversion.dataset;

import ru.inversion.dataset.aggr.IAggregatorBuilder;
import ru.inversion.dataset.mark.MarkModeEnum;

import java.util.Iterator;
import java.util.function.Predicate;

/**
 * DataSet с поддержкой функционала используемого при разработке приложений для ЦАБС "XXI Век" фирмы Инверсия.
 * <p>
 * Сюда относится работа с пометкой - флаг, поле, в спец таблице БД XXI, куда ставится признак,что запись из DataSet отмечена.
 * Применяется при групповой обработке записей.
 * 
 * @author sulimoff
 */
public interface IXXIDataSet< P > extends ISQLDataSet<P> {
	
    /**
     * Признак, что для XXIDataSet установлен режим автофильтра.
     * <p>
     * Автофильтр - фильтр который устанавливается автоматически устанавливается на DataSet,в момент его создания.
     * Усиливает предикат SQL выражения DataSet.
     * Не может быть снят.
     * 
     * @return 
     *          {@code true } - автофильтр установлен
     *          {@code false} - не установлен
     */
	boolean isEnableAutoFilter( );
	
    /** 
     * Возвращает режим пометки для DataSet.
     * <p>
     * 
     * @return 
     *          установленный режим
     * 
     * @see MarkModeEnum
     */
    MarkModeEnum getMarkMode();
    
    /** 
     * Возвращает ID маркера используемый для пометки записей в XXIDataSet.
     * <p>
     * Если markerID не задан из вне, считается возможным,что {@code markerID} может быть сформирован внутри XXIDataSet, 
     * как только он будет необходим для работы XXIDataSet.
     * До этого момента markerID равен {@code null}
     * 
     * @return 
     *          ID маркера, {@code null} если ID маркера пока не установлен
     * 
     * @see #setMarkerID(java.lang.Long) 
     */
	Long getMarkerID( );
	
    /**
     * Устанавливает ID маркера для пометки записей.
     * <p>
     * 
     * @param markerID
     *          ID маркера
     * 
     * @see #getMarkerID() 
     */
    void setMarkerID( Long markerID );
    
    /** 
     * Пометить все записи в XXIDataSet.
     * <p>
     * Помечаются все записи которые возможно получить на основании SQL выражения.
     * Пометка сохраняется в БД.
     * 
     * @param savePrevMarkedRows
     *          Сохранить предыдущие помеченные записи и добавить записи к уже помеченным
     *          {@code true } - добавить
     *          {@code false} - очистить все уже помеченные записи
     *          актуально когда на DataSet меняются устанавливаются фильтры
     * 
     * @throws DataSetException
     *          если были ошибки про пометке.
     */
	void markAllRows( boolean savePrevMarkedRows ) throws DataSetException;

    /** 
     * Снять пометку со всех записей XXIDataSet.
     * <p>
     * @param savePrevMarkedRows
     *          Сохранить предыдущие помеченные записи, очистить только записи которые помечены на текущем наборе
     *          {@code true } - сохранить
     *          {@code false} - очистить все уже помеченные записи
     *          актуально когда на DataSet меняются устанавливаются фильтры
     * 
     * @throws DataSetException
     *          если были ошибки про снятии пометки.
     */
    void unMarkAllRows( boolean savePrevMarkedRows ) throws DataSetException;
	
    /** */
    default void unMarkAllRows( ) throws DataSetException {
        unMarkAllRows(false);
    }
    
    /** 
     * Очистить пометку.
     * <p>
     * Очищает все данные связанные с пометкой.
     * 
     * @throws DataSetException
     *          если были ошибки про снятии пометки.
     */
    void clearMark( ) throws DataSetException;
    
    /** 
     * Пометить текущую строку в XXIDataSet.
     * <p>
     * Если DataSet пустой, то ничего не происходит.
     * 
     * @throws DataSetException
     *          если были ошибки
     */
	void markCurrentRow( ) throws DataSetException;
    
    /** 
     * Снять пометку с текущей строки в XXIDataSet.
     * <p>
     * Если DataSet пустой, то ничего не происходит.
     * 
     * @throws DataSetException
     *          если были ошибки
     */
	void unMarkCurrentRow( ) throws DataSetException;
    
    /** 
     * Возвращает признак помечена ли текущая запись.
     * <p>
     * @return 
     *          {@code true } - помечена
     *          {@code false} - нет
     */
    boolean isMarkCurrentRow( );

	/** 
     * Установить слушателя связанных с событием пометки внутри DataSet.
     * <p>
     * События пометки формируются в момент отметки как помеченной "текущей записи" или всех записей внутри XXIDataSet
     * 
     * @param dataSetMarkListener
     *          слушатель событий пометки
     * 
     * @see IDataSetMarkListener
     * @see DataSetMarkEvent
     */
	void addMarkDataSetListener( IDataSetMarkListener< P > dataSetMarkListener );
    
    /** 
     * Удаление слушателя связанных с событием пометки внутри DataSet.
     * <p>
     * Установленного методом {@code addMarkDataSetListener}
     * 
     * @param dataSetMarkListener
     *          слушатель событий пометки
     * 
     * @see IDataSetMarkListener
     * @see DataSetMarkEvent
     */
	void removeMarkDataSetListener( IDataSetMarkListener< P > dataSetMarkListener );
    
	/** 
     * Возвращает {@code Iterator} по загруженным и помеченным записям внутри XXIDataSet.
     * <p>
     * 
     * @param predicate
     *          фильтр на отбор записей, {@code null} если нужны все помеченные записи.
     * @return
     *        {@code Iterator} с помеченными записями  
     */
	Iterator< P > getMarkedRowIterator( Predicate< P > predicate );

	/** 
     * Создает {@code Iterator} по всем отмеченным записям которые могут быть получен на основании SQL выражения внутри XXIDataSet.
     * <p>
     * Предполагается что созданный итератор не влияет на набор данных внутри XXIDataSet и на SQL выражение.
     * Т.е. создается копия данных на основе SQL выражения внутри XXIDataSet.
     * @param ignoreFilter
     *          признак учитывать/не учитывать установленные фильтры внутри XXIDataSet.
     *          {@code true } - не учитывать
     *          {@code false} - учитывать
     * @return
     *        {@code Iterator} с помеченными записями  
     * @throws 
     *          DataSetException если была ошибка при создании {@code Iterator}
     *          
     */
	Iterator< P > createMarkedRSIterator( boolean ignoreFilter ) throws DataSetException;
    
    /** 
     * Создает билдер маркированных агреггаторов.
     * <p>
     * @param refreshOnExecute
     *          обновлять данные агреггатора при обновлении источника данных
     * @param aggrTable
     *          имя таблицы с аггреггированными данными
     */
    IAggregatorBuilder<P> createMarkedAggregatorBuilder( boolean refreshOnExecute, String aggrTable );
    
    /** 
     * Создает билдер маркированных агреггаторов.
     */
    default IAggregatorBuilder<P> createMarkedAggregatorBuilder( boolean refreshOnExecute ) {
        return createMarkedAggregatorBuilder( refreshOnExecute, null );
    }
}
