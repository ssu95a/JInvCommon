package ru.inversion.dataset;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Базовый интерфейс для всех возможных DataSet.
 * <p>
 * DataSet - набор данных - объект содержащий коллекцию записей, определенного типа, как правило POJO,
 *           который предоставляет методы для манипуляции этим набором (добавление, редактирования, удаления элементов)
 *           и реализует концепцию курсора, т.е.понятие текущей записи в наборе и методов навигации, т.е. перемещения текущей записи, внутри набора.
 *           Имеет набор событий связанных с методами работы с данными и навигации которыми может уведомлять объекты использующие DatSet.
 *
 * @param <P>
 *          тип записи хранящейся в DataSet
 *
 * @author sulimoff
 * @version 2.0.1
 */
public interface IDataSet<P> extends IDSBase<P> {

	/**
     * Способы добавления новых записей в DataSet.
     * <p>
     * В методе {@link #insertRow }
     */
	public enum InsertRowModeEnum {

        /**
         * Вставить как самую первую запись
         */
        FIRST,

        /**
         * Вставить как самую последнюю запись
         */
		LAST,

        /**
         * Вставить перед текущей записью
         */
		BEFORE_CURRENT,

        /**
         * Вставить после текущей записи
         */
        AFTER_CURRENT
	}

	/**
	 * Режим запроса данных
	 */
	enum DataQueryModeEnum {

		/** Штатный режим, с подкачкой, если необходимо*/
		STANDARD,
		/** Сразу прочесть все записи */
		QUERY_ALL_ROWS,
		/** DataSet состоящий из дной записи */
		SINGLE_ROW
	}

	/**
	 * Возвращает режим чтения данных из источника данных.
	 * <p>
	 * @return установленный режим.
	 */
	default DataQueryModeEnum getDataQueryMode() { return DataQueryModeEnum.STANDARD; }

	/**
     * Получить список записей содержащихся в DataSet.
     * <p>
     * @return список записей.
     */
	List< P > getRows( );

	/**
     * Сделать текущей строку по ее номеру.
     * <p>
     * Текущая строка та, на которой стоит внутренний указатель DataSet, и c которой возможно применение методов связанны с {@code ...currentRow}
     *
     * @param rowNum
     *          номер строки которую нужно сделать текущей, начиная с 0
     *
     * @return  {@code true } если успешно установлено
     *           {@code false} если не возможно установить текущую строку с таким номером
     *
     * @see #getCurrentRow
     * @see #getCurrentRowNum
     * @see #setCurrentRow
     * @see #updateCurrentRow
     * @see #removeCurrentRow
	 */
	boolean setCurrentRowNum( int rowNum );

	/**
     * Сделать текущей строку по ссылке на объект записи.
     * <p>
     * Запись {@code row} <b>должна быть внутри DataSet</b>
     * @param row
     *          объект записи {@code row} по которой осуществляется поиск
     *
     * @return  {@code true } если успешно установлено
     *           {@code false} если не возможно установить текущую строку по {@code row}
     *
     * @see #getCurrentRow
     * @see #setCurrentRowNum
     * @see #updateCurrentRow
     * @see #removeCurrentRow
    */
	boolean setCurrentRow( P row );

	/**
     * Поиск записи по условию в наборе записей внутри DataSet.
     * <p>
     * В случае нахождения делает найденную запись текущей.
	 *
     * @param predicat
     *          условие поиска записей {@code BiPredicate.test(T t, U u);},
     *          первый аргумент t - запись из {@code DataSet}
     *          второй аргумент u - параметр поиска  - {@code arg2}
     *
     * @param arg2
     *          параметр поиска
     *
     * @param <A>
     *          тип параметра поиска
     *
     * @return  {@code true } запись успешно найдена и установлена как текущая,
     *           {@code false} запись не найдена
     *
     * @see #setCurrentRow
     * @see #getCurrentRow
     * @see BiPredicate
	 */
    public < A > boolean findRow ( BiPredicate< P, A > predicat, A arg2 );

	/**
	 * Поиск записи по условию в наборе записей внутри DataSet.
	 * <p>
	 * В случае нахождения делает найденную запись текущей.
	 *
	 * @param startIndex индекс, с которого следует начинать поиск
	 * @param predicate предикат
	 *
	 * @return  {@code true } запись успешно найдена и установлена как текущая,
	 *           {@code false} запись не найдена
	 *
	 * @see #setCurrentRow
	 * @see #getCurrentRow
	 * @see Predicate
	 */
    default boolean findRow ( int startIndex, Predicate< P > predicate ) {
    	throw new UnsupportedOperationException("Not implemented yet.");
	}

	/**
	 * Поиск записи по условию в наборе записей внутри DataSet.
	 * <p>
	 * В случае нахождения делает найденную запись текущей.
	 *
	 * @param predicate предикат
	 *
	 * @return  {@code true } запись успешно найдена и установлена как текущая,
	 *           {@code false} запись не найдена
	 *
	 * @see #setCurrentRow
	 * @see #getCurrentRow
	 * @see Predicate
	 */
    default boolean findRow ( Predicate< P > predicate ) {
    	return findRow( 0, predicate );
	}

	/**
     * Возвращает текущую запись.
     * <p>
     * На которой установлен внутренний указатель.
     *
     * Если текущая запись не установлена то {@code null}
     *
     * @return текущую запись, {@code null} если не установлена.
     */
	P getCurrentRow( );

    /**
     * Возвращает запись из DataSet по ее номеру.
     * <p>
     * Если нет такой строки то {@code NoSuchElementException}
     *
     * @param rowNum
     *          номер записи, начиная с 0
     *
     * @return Экземпляр записи
     *
     * @throws NoSuchElementException
     *          если в DataSet нет записи с номером {@code rowNum}
	 */
	P getRow( int rowNum );

    /**
     * Возвращает номер текущей записи.
     * <p>
     * @return номер текущей записи или -1, если номер не установлен
     */
	int getCurrentRowNum( );

	/**
     * Очищает все записи внутри DataSet.
     * <p>
     * Удаляются все записи из dataSet.
     */
	void clear( );

    /**
     * Выполнить запрос и получить данные.
     * <p>
     * Для DataSet которые построены на внешнем источнике данных (БД)
     * выполняется запрос  и копирование данных из источника во внутреннюю коллекцию DataSet.
     *
     * Предполагается что в момент выполнения
     * будет сформировано событие {@code DataSetEvent} с eventType - EXECUTE
     *
     * @throws DataSetException
     *          если при получении данных были ошибки
     *
     * @see DataSetEvent
     */
	void executeQuery( ) throws DataSetException;

    /**
	 * +------------------------------------------------+
	 * | @Deprecated от 03.04.2019						|
	 * | Метод с параметром заменен на свойство DataSet |
	 * | readAllRows см. {@link SQLDataSet}				|
	 * +------------------------------------------------+
	 * <p>
     * Выполнить запрос и получить все данные.
     * <p>
     * Для DataSet которые построены на внешнем источнике данных (БД)
     * выполняется запрос и копирование данных из источника во внутреннюю коллекцию DataSet.
     *
     * Предполагается что в момент выполнения
     * будет сформировано событие {@code DataSetEvent} с eventType - EXECUTE
     *
     * @param readAllRecords
     *        признак, что необходимо из источника закачать сразу все данные
     *
     * @throws DataSetException
     *         если при получении данных были ошибки
     *
     * @see DataSetEvent
     */
    @Deprecated
	void executeQuery( boolean readAllRecords ) throws DataSetException;

	/**
     * Возвращает количество загруженных записей.
     * <p>
     * Если DataSet построен на внешнем источнике данных,
     * то данный метод возвращает кол-во записей загруженных на данный момент.
     * Иначе кол-во записей внутри DataSet.
     *
     * @return кол-во записей внутри DataSet. 0 если он пустой.
     */
	int getLoadedRowCount( );

	/**
     * Возвращает предполагаемое кол-во записей.
     * <p>
     * Если DataSet построен на внешнем источнике данных,
     * то данный метод возвращает общее кол-во записей, которые находятся в источнике данных,
     * если это возможно определить иначе -1.
     *
	 *  @return кол-во записей в источнике данных или -1 если это недоступно невозможно узнать.
	 */
	int getTotalRowCount ( );


	// слушатели DataSet

	/**
     * Установить слушателя для событий связанными со строками.
     * <p>
     * Для строк допустимы события вставки, обновление, удаление и т.д. см. {@link DataSetRowEvent}
     *
     * @param rowListener
     *          слушатель строковых событий
     *
     * @see IDataSetRowListener
     * @see DataSetRowEvent
     */
	void addRowListener ( IDataSetRowListener< P > rowListener  );

	/**
     * Удаление слушателя для событий связанных со строками установленного методом {@code addRowListener}.
     * <p>
     * @param rowListener
     *          слушатель ранее добавленный методом {@code addRowListener}
     *
     * @see #addRowListener
     */
	void removeRowListener ( IDataSetRowListener< P > rowListener  );

	/**
     * Установить слушателя для событий связанных с DataSet.
     * см. {@link DataSetEvent}
     * <p>
     * @param dataSetListener
     *          слушателей событий DataSet
     *
     * @see IDataSetListener
     * @see DataSetEvent
     */
	void addDataSetListener		 ( IDataSetListener dataSetListener );

	/**
     * Удаление слушателя для событий связанных с DataSet установленного методом {@code addDataSetListener}.
     * <p>
     * @param dataSetListener
     *          слушатель ранее добавленный методом {@code addDataSetListener}
     *
     * @see #addDataSetListener
     */
	void removeDataSetListener	 ( IDataSetListener dataSetListener );

	/**
     * Установить слушателя связанных с событием навигации внутри DataSet.
     * События навигации формируются в момент перемещения позиции "текущей записи".
     * см. {@link DataSetNavigationEvent}
     * <p>
     * @param navigationListener
     *          слушатель событий навигации
     *
     * @see IDataSetNavigationListener
     * @see DataSetNavigationEvent
     */
	void addNavigationListener	 ( IDataSetNavigationListener< P > navigationListener );

	/**
     * Удаление слушателя связанных с событием навигации добавленного методом {@code addNavigationListener}.
     * <p>
     * @param navigationListener
     *          слушатель ранее добавленный методом {@code addNavigationListener}
     *
     * @see #addNavigationListener
     */
	void removeNavigationListener( IDataSetNavigationListener< P > navigationListener );


	/**
     * Вставка новой записи в DataSet.
     * <p>
     * @param row
     *          новая запись
     *
     * @param insertMode
     *          режим вставки см. {@link InsertRowModeEnum}
     *
     * @param doSetToCurrent
     *          признак - установить вставленную строку как текущую
     */
	void insertRow( P row, InsertRowModeEnum insertMode, boolean doSetToCurrent );

	/**
     * Групповая вставка новых записи в DataSet.
     * <p>
     * @param newRows
     *          список записей на вставку
     *
     * @param insertMode
     *          режим вставки см. {@link InsertRowModeEnum}
     *
     * @param doSetToCurrent
     *          признак - установить одну из вставленных (как правило первую) строк как текущую
     */
	void insertRows( List< P > newRows, InsertRowModeEnum insertMode, boolean doSetToCurrent );

	/**
     * Групповое удаление записей из DataSet.
     * <p>
     * @param predicat
     *          условие отбора записей на удаление, если {@code null} то происходит возврат из ф-ции
     *
     * @param arg2
     *          уточняющий аргумент для ф-ции отбора записей на удаление, {@code null} если не используется
     *
     * @param A тип уточняющего параметра для ф-ции отбора записей, если не нужен то можно использовать {@code Void}
     *
     * @return кол-во удаленных записей, если 0 то ничего не подошло под условие удаления
     */
    <A> int removeRows( BiPredicate< P, A > predicat, A arg2 );

	/**
     * Обновление текущей записи в DataSet.
     * <p>
     * @param newRow
     *          новая запись
     *
     * @return старую запись, которая была до обновления, если обновление не произошло то {@code null}
     */
	P updateCurrentRow( P newRow );

    /**
     * Удаление текущей записи из DataSet.
     * <p>
     * @return удаленную запись, если удаление не произошло то {@code null}
     */
	P removeCurrentRow( );

	/**
     * Возвращает Iterator по записям внутри DataSet.
     * <p>
     * @param predicate
     *          условие отбора записей, если нужны все записи то {@code null}
     *
     * @return Итератор с записями
     *
     * @see java.util.function.Predicate
     */
	Iterator<P> getRowIterator( Predicate< P > predicate );

    /**
     * Проход по всем записям внутри DataSet с обработкой каждой записи.
     * <p>
     * @param consumer
     *          код для обработки записи
     *
     * @param predicate
     *          условие отбора записей, если нужны все записи то {@code null}
     *
     * @see java.util.function.Consumer
     * @see java.util.function.Predicate
     */
    void forEach( Consumer< P > consumer, Predicate< P > predicate );

	/**
     * Подкачка данных если возможно.
     * <p>
     * Для DataSet которые построены на внешнем источнике данных (БД)
     * выполняется подкачку и копирование данных из источника во внутреннюю коллекцию DataSet.
     *
	 * @return {@code true} - если есть еще данные для подкачки, {@code false} - данных больше нет
     *
     * @throws DataSetException
     *          если во врем подкачки данных были ошибки
	 */
	boolean swappingData( ) throws DataSetException;
}
