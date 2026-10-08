package ru.inversion.dataset;

import java.util.EventObject;

/**
 * Событие DataSet формируемое при действиях c записями DataSet.
 * <p>
 * Действия могут как групповые, так и для одной записи.
 *
 * @param <T>
 *          тип записи DataSet
 *
 * @see IDataSetRowListener
 *
 * @author sulimoff
 */
public class DataSetRowEvent<T> extends EventObject {

    /**
     * Типы действий над записями внутри DataSet.
     */
	public enum RowOperationEnum {

        /**
         * Вставка, добавление новой записи.
         */
        INSERT,

		/**
         * Обновление записи.
         */
        UPDATE,

		/**
         * Удаление записи.
         */
        DELETE,

        /**
         * Обновление данных записи.
         * <p>
         * В отличие от UPDATE, где запись полностью меняется на другую, здесь происходит обновление данных самой записи.
         * @see ISQLDataSet#refreshCurrentRowFromDB()
         */
        REFRESH
	}

	@Override
	public IDataSet<T> getSource() {
		if ( super.getSource() instanceof IDataSet ){
			return (IDataSet<T>) super.getSource();
		}
		return null;
	}

	/**
     * Тип действия над записями.
     */
	private final RowOperationEnum rowOperation;

    /**
     * Запись до действия.
     * <p>
     * Используется при действиях: UPDATE, REFRESH, DELETE
     */
	private final T	  oldRow;

    /**
     * Данные записи после действия.
     * <p>
     * Используется при действиях: INSERT, UPDATE, REFRESH
	 * При множественном INSERT (первая добавленная из группы)
     */
	private final T	  newRow;

    /**
     * Индекс записи над которой производится действие.
     */
	private final int rowIndex;

    /**
     * Кол-во записей которое затрагивает это действие.
     */
	private final int rowCount;

	/**
     * Создает событие о действии над записью внутри DataSet.
     *
     * @param source
     *          DataSet сформировавший событие
     * @param rowOperation
     *          тип действия
     * @param oldRow
     *          запись до действия
     * @param newRow
     *          запись после действия
     * @param rowIndex
     *          индекс записи
     * @param rowCount
     *          кол-во записей
     */
	public DataSetRowEvent(Object source, RowOperationEnum rowOperation, T oldRow, T newRow, int rowIndex, int rowCount ) {
		super(source);
		this.rowOperation = rowOperation;
		this.oldRow		  = oldRow;
		this.newRow		  = newRow;
		this.rowIndex	  = rowIndex;
		this.rowCount	  = rowCount;
	}

	/**
     * @return
     *       тип действия над записью
     *
     * @see RowOperationEnum
     */
	public RowOperationEnum getRowOperation() {
		return rowOperation;
	}

	/**
     * @return
     *      запись до действия
     *
     * @see #oldRow
     */
	public T getOldRow( ) {
		return oldRow;
	}

	/**
     * @return
     *      запись после действия
     *
     * @see #newRow
     */
	public T getNewRow( ) {
		return newRow;
	}

	/**
     * @return
     *      индекс записи
     *
     * @see #rowIndex
     */
	public int getRowIndex( ) {
		return rowIndex;
	}

	/**
     * @return
     *      кол-во записей затрагиваемых действием
     *
     * @see #rowCount
     */
	public int getRowCount( ) {
		return rowCount;
	}
}
