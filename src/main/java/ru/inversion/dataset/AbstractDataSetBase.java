package ru.inversion.dataset;

import ru.inversion.utils.S;
import ru.inversion.utils.U;
import ru.inversion.utils.lstn.IListenerManEvent;
import ru.inversion.utils.lstn.ListenerManFactory;

import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Базовый класс с общей, для многих видов DataSet, функциональностью.
 * <p>
 * @author sulimoff
 */
public abstract class AbstractDataSetBase<P> extends AbstractDSBase<P> implements IDataSet<P> {

	/**
     * Фабрика для создания коллекций хранения записей внутри DataSet.
     * <P>
     * Необходима если будет потребность сменить тип коллекции для хранения записей.
     * (пока такой потребности нет)
     */
	static private IDataSetListFactory g_listFactory = new IDataSetListFactory() {

		@Override
		public <T> List<T> createDataSetList() {
			//return new LinkedList<>(); //ArrayList<T>();
			//return new PageList<>(50); //ArrayList<T>();
			return new ArrayList<>( );
			//return Collections.synchronizedList( new ArrayList<>( ) );
		}
		@Override
		public <T> List<T> createDataSetList(List<T> copyFrom) {
			return new ArrayList<>( copyFrom );
		}

        @Override
        public <T> List<T> createDataSetList(int size) {
			return new ArrayList<>(size);
        }
	};

	public static void setDataSetListFactory( IDataSetListFactory listFactory ) {
		g_listFactory = listFactory;
	}

	public static IDataSetListFactory getDataSetListFactory( ) {
		return g_listFactory;
	}

    /**
     * Тип записи содержащихся в DataSet.
     * <p>
     * @see #setRowClass
     * @see #getRowClass
     */
	//private	Class<? super T> rowClass;

    /**
     * Коллекция записей содержащихся в DataSet.
     */
	final private List<P> rows;

    /**
     * Текущий номер записи в DataSet.
     */
	private int currentRowNum = -1;

    /**
     * Коллекция слушателей событий самого DataSet.
     * <p>
     * @see IDataSetListener
     * @see DataSetEvent
     */
    final private IListenerManEvent<IDataSetListener,   DataSetEvent> dataSetListeners =
            ListenerManFactory.createListenerManEvent( IDataSetListener::dataSetChanged );

    /**
     * Коллекция слушателей событий связанных с записями DataSet
     * <p>
     * @see IDataSetRowListener
     * @see DataSetRowEvent
     */
    final private IListenerManEvent<IDataSetRowListener< P >,DataSetRowEvent< P >> dataSetRowListeners =
            ListenerManFactory.<IDataSetRowListener< P >,DataSetRowEvent< P >>createListenerManEvent( IDataSetRowListener< P >::rowOperation );

    /**
     * Коллекция слушателей событий связанных с событиями навигации DataSet
     * @see IDataSetNavigationListener
     * @see DataSetNavigationEvent
     */
    final private IListenerManEvent<IDataSetNavigationListener< P >,DataSetNavigationEvent< P >> navigationListeners =
            ListenerManFactory.createListenerManEvent( IDataSetNavigationListener< P >::navigated );

    /**
     * Создает DataSet с типом строки и начальной коллекцией записей.
     *
     * @param rowClass
     *        тип записи DataSet
     *
     * @param rows
     *        Первоначальная коллекция с записями
     *
     * @param doCopy
     *        Признак копировать данные из переданной коллекции или использовать переданную коллекцию в качестве внутренней.
     *        {@code true } - копировать
     *        {@code false} - использовать
     */
    @SuppressWarnings("unchecked")
	public AbstractDataSetBase( Class<? extends P > rowClass, List<P> rows, boolean doCopy )
	{
        super( rowClass );

        if( doCopy )
            this.rows = rows == null ? getDataSetListFactory().createDataSetList()
                    : getDataSetListFactory().createDataSetList(rows);
        else
            this.rows = rows != null ? rows : getDataSetListFactory().createDataSetList();

        if( this.rowClass == null && !this.rows.isEmpty() )
            this.rowClass = U.<P,Class>callIfNotNull(U.firstNotNull(this.rows), P::getClass);
	}

    /** */
	public AbstractDataSetBase( List<P> rows, boolean doCopy ) {
        this( null, rows, doCopy );
	}

    /** */
	public AbstractDataSetBase( Class<? extends P >  rowClass ) {
        super(rowClass);
		this.rows = getDataSetListFactory().createDataSetList();
	}

    /** */
	public AbstractDataSetBase( ) {
        this(null);
	}

    /** */
	protected List<P> rows( ) {
		return rows;
	}

    /** {@inheritDoc} */
	@Override
	public List<P> getRows( ) {
		return rows();
	}

    /**
     * Возвращает имя DataSet.
     * <p>
     * Если имя явно не задано методом {@code setName} возвращает имя класса записи на которой построен DataSet, если он задан.
     *
     * @see IDataSet#setName
     * @see #setName
     */
	@Override
	public String getName( ) {
		return U.nvl( name, U.<Class,String>callIfNotNull( getRowClass(), Class::getName ) );
	}

    /**
     * Удаление текущей записи из DataSet.
     * <p>
     * При успешном удалении генерит событие {@code DataSetRowEvent.DELETE}
     * Метод перемещает позицию с удаляемой записи внутри DataSet на следующую.
     *
     * @return удаленную запись, если удаление не произошло то {@code null}
     */
    @Override
	public P removeCurrentRow( ) {
        final int oldIndex = currentRowNum;
        if( oldIndex < 0 || oldIndex >= rows.size() )
            return null;

        final P oldRow = rows.remove(oldIndex);
        currentRowNum = rows.isEmpty() ? -1 : Math.min(oldIndex, rows.size() - 1);
        final P newRow = currentRowNum < 0 ? null : rows.get(currentRowNum);

        fireRowEvent(DataSetRowEvent.RowOperationEnum.DELETE, oldRow, null, oldIndex, 1);
        fireNavigationEvent(oldRow, newRow, oldIndex, currentRowNum);
        return oldRow;
	}

	/**
     * Обновление текущей записи в DataSet.
     * <p>
     * После обновления генерит событие {@code DataSetRowEvent.UPDATE}
     *
     * @param newRow
     *        новая запись
     *
     * @return старую запись, которая была до обновления, если обновление не произошло то {@code null}
     */
    @Override
	public P updateCurrentRow( P newRow ) {
        final int index = currentRowNum;
        if( index < 0 || index >= rows.size() )
            return null;

        final P oldRow = rows.set(index, newRow);
        fireRowEvent(DataSetRowEvent.RowOperationEnum.UPDATE, oldRow, newRow, index, 1);
        return oldRow;
	}

	/** {@inheritDoc}  */
	@Override
	public void insertRow( P row, InsertRowModeEnum insertMode, boolean doSetToCurrent ) {
		insertRows( Arrays.asList(row), insertMode, doSetToCurrent );
	}

	/**
     * Групповая вставка новых записи в DataSet.
     * <p>
     * После добавления записей генериться событие {@code DataSetRowEvent.INSERT}
	 *
     * @param newRows
     *        список записей на вставку, если передан {@code null} или пустая коллекция, то ничего не происходит
     *
     * @param insertMode
     *        режим вставки см. {@link InsertRowModeEnum}
     *
     * @param doSetToCurrent
     *        признак - установить одну из вставленных (как правило первую) строк как текущую
     */
    @Override
	public void insertRows( List< P > newRows, InsertRowModeEnum insertMode, boolean doSetToCurrent ) {
        if( newRows == null || newRows.isEmpty() )
            return;

        final int oldIndex = currentRowNum;
        final P oldRow = oldIndex >= 0 && oldIndex < rows.size() ? rows.get(oldIndex) : null;
        final int position;

        if( rows.isEmpty() ) {
            position = 0;
        }
        else {
            switch( insertMode ) {
                case FIRST:
                    position = 0;
                    break;
                case LAST:
                    position = rows.size();
                    break;
                case BEFORE_CURRENT:
                    position = oldIndex >= 0 ? oldIndex : 0;
                    break;
                case AFTER_CURRENT:
                    position = oldIndex >= 0 ? oldIndex + 1 : 0;
                    break;
                default:
                    throw new IllegalArgumentException("Unknown insert mode: " + insertMode);
            }
        }

        rows.addAll(position, newRows);

        if( doSetToCurrent )
            currentRowNum = position;
        else if( oldIndex >= 0 && position <= oldIndex )
            currentRowNum = oldIndex + newRows.size();

        fireRowEvent(DataSetRowEvent.RowOperationEnum.INSERT, null, newRows.get(0), position, newRows.size());

        if( doSetToCurrent && (oldIndex != position || oldRow != rows.get(position)) )
            fireNavigationEvent(oldRow, rows.get(position), oldIndex, position);
	}

    /** {@inheritDoc} */
    @Override
    public <A> int removeRows( BiPredicate<P, A> predicat, A arg2 ) {
        if( predicat == null || rows.isEmpty() )
            return 0;

        // Вычисляем набор удаляемых позиций до первого изменения коллекции.
        final int size = rows.size();
        final boolean[] remove = new boolean[size];
        int removeCount = 0;
        for( int i = 0; i < size; i++ ) {
            if( predicat.test(rows.get(i), arg2) ) {
                remove[i] = true;
                removeCount++;
            }
        }

        if( removeCount == 0 )
            return 0;
        if( removeCount == size ) {
            clear();
            return removeCount;
        }

        final int oldIndex = currentRowNum;
        final P oldRow = oldIndex >= 0 && oldIndex < size ? rows.get(oldIndex) : null;
        int selectedIndex = -1;
        if( oldIndex >= 0 && oldIndex < size ) {
            if( !remove[oldIndex] ) {
                selectedIndex = oldIndex;
            }
            else {
                // Если текущая строка удалена, выбираем следующую, иначе предыдущую.
                for( int i = oldIndex + 1; i < size && selectedIndex < 0; i++ )
                    if( !remove[i] ) selectedIndex = i;
                for( int i = oldIndex - 1; i >= 0 && selectedIndex < 0; i-- )
                    if( !remove[i] ) selectedIndex = i;
            }
        }

        // Удаляем непрерывными диапазонами с конца, не используя equals().
        // События DELETE содержат действительные индексы и размеры диапазонов.
        currentRowNum = selectedIndex;
        for( int end = size - 1; end >= 0; ) {
            if( !remove[end] ) {
                end--;
                continue;
            }
            int start = end;
            while( start > 0 && remove[start - 1] )
                start--;

            final int count = end - start + 1;
            final P removedRow = count == 1 ? rows.get(start) : null;
            rows.subList(start, end + 1).clear();
            if( currentRowNum > end )
                currentRowNum -= count;
            fireRowEvent(DataSetRowEvent.RowOperationEnum.DELETE, removedRow, null, start, count);
            end = start - 1;
        }

        final P newRow = currentRowNum < 0 ? null : rows.get(currentRowNum);
        if( oldIndex >= 0 && (oldIndex != currentRowNum || oldRow != newRow) )
            fireNavigationEvent(oldRow, newRow, oldIndex, currentRowNum);
        return removeCount;
    }

    /*
    +------------------------------+
    | РАБОТА С ПАРАМЕТРАМИ DATASET |
    +------------------------------+
    */

	/** {@inheritDoc} */
	@Override
	public void setCallbackParameters( IParameters parameters ) {
		this.callbackParameters = parameters;
	}

	/** {@inheritDoc}  */
	@Override
	public IParameters getCallbackParameters( ) {
		return  callbackParameters;
	}

	/**
     * Версия метода setCallbackParameters в стиле "Fluent interface".
     * <p>
     * @param parameters
     *          внешний набор параметров для DataSet, {@code null} для очистки внешнего набора внутри DataSet
     *
     * @return this
     * @see #setCallbackParameters
     */
    public AbstractDataSetBase callbackParameters( IParameters parameters ) {
        setCallbackParameters(parameters);
        return this;
    }

	/** {@inheritDoc}
    @Override
	public Object getParameter( int parameterIndex ) {
		return parameterIndexMap == null ? null : parameterIndexMap.get(parameterIndex);
	}
	*/

	/** {@inheritDoc}
    @Override
	public void setParameter  ( int parameterIndex, Object value ) {
		if( parameterIndexMap == null )
			parameterIndexMap = new TreeMap<>();
		parameterIndexMap.put( parameterIndex, value );
	}
	*/

	/** {@inheritDoc}
	@Override
	public Object getParameter( String parameterName ) {
		return U.containsNull( parameterName, parameterNamedMap ) ? null : parameterNamedMap.get(parameterName.toUpperCase());
	}
	*/

	/** {@inheritDoc}
	@Override
	public void setParameter( String parameterName, Object value ) {

        Objects.requireNonNull( parameterName, Tags.PRODUCT_LABEL + "parameterName is null");

        if( parameterNamedMap == null ) {
			parameterNamedMap = new HashMap<>();
		}
		parameterNamedMap.put( parameterName.toUpperCase(), value );
	}
	*/

    /**
     * Проверка наличия именованного параметра во внутренней коллекции DataSet.
     * <p>
     * По умолчанию DataSet разделяет ситуацию, когда параметр отсутствует и когда его значение равно {@code null}.
     * Поэтому перед тем как получить значение какого-либо параметра необходимо проверить его наличие данным методом.
     *
     * @param parameterName
     *          наименование параметра  который проверяется
     *
     * @return {@code true} если параметр присутствует, {@code false} если нет.
     *
     * @see IParameters#hasParameter(java.lang.String)
    @Override
    public boolean hasParameter( String parameterName ) {
        return parameterNamedMap != null && parameterNamedMap.containsKey(parameterName.toUpperCase());
    }
	 */

    /**
     * Проверка наличия индексированного параметра во внутренней коллекции DataSet.
     * <p>
     * По умолчанию DataSet разделяет ситуацию, когда параметр отсутствует и когда его значение равно {@code null}.
     * Поэтому перед тем как получить значение какого-либо параметра необходимо проверить его наличие данным методом.
     *
     * @param parameterIndex
     *          индекс параметра который проверяется
     *
     * @return {@code true} если параметр присутствует, {@code false} если нет.
     *
     * @see IParameters#hasParameter(int)
    @Override
    public boolean hasParameter( int parameterIndex ) {
        return parameterIndexMap != null && parameterIndexMap.containsKey(parameterIndex);
    }
	 */


	/** {@inheritDoc} */
	@Override
	public int getTotalRowCount( ) {
		return getLoadedRowCount( );
	}

	/** {@inheritDoc} */
	@Override
	public int getLoadedRowCount( ) {
		return rows( ).size( );
	}

	/** {@inheritDoc} */
	@Override
	public void executeQuery( ) throws DataSetException {
		executeQuery( true );
	}

	/**
     * Очищает все записи внутри DataSet.
     * <p>
     * Удаляются все записи из dataSet.
     * Номер текущей записи становиться -1
     * При запросе текущей записи возвращается {@code null}
     *
     * В процессе очищения записей формируется событие {@code DataSetRowEvent} с eventType - DELETE.
     *
     * @see DataSetRowEvent
     */
	@Override
	public void clear( ) {

        int rowCount = this.rows( ).size( );

		if( rowCount > 0 ) {

			int	oldRowNum = getCurrentRowNum( );
			P oldRow	  = getCurrentRow( );

			rows.clear();

			currentRowNum = -1;

			if( oldRow != null )
				fireNavigationEvent( oldRow, null, oldRowNum, -1 );

			fireRowEvent( DataSetRowEvent.RowOperationEnum.DELETE, null, null, -1, rowCount );
		}
	}

	/** {@inheritDoc}  */
	@Override
	public P getRow( int rowNum ) {
		return rows().get(rowNum);
	}

    /**
     * Возвращает первую запись в DataSet.
     * <p>
     * @return первую запись
     */
    public P getFirstRow() {
		return isEmpty() ? null : rows().get(0);
    }

    /**
     * Возвращает последнюю запись в DataSet.
     * <p>
     * если DataSet пустой, то возвращает {@code null}
     *
     * @return последнюю запись
     */
    public P getLastRow() {
		return isEmpty() ? null : rows().get( rows().size() - 1 );
    }

	/** {@inheritDoc}  */
	@Override
	public P getCurrentRow( ) {
		//return currentRow;
        return ( isEmpty() || currentRowNum == - 1 ) ? null : getRow(currentRowNum);
	}

	/** {@inheritDoc}  */
	@Override
	public int getCurrentRowNum( ) {
		return currentRowNum;
	}

	/** {@inheritDoc}  */
    @Override
	public < A > boolean findRow ( BiPredicate< P, A > predicat, A arg2 ) {

		if( predicat != null && !isEmpty() )
		{
			int index = 0;
			for( P row : rows() ) {
				if( predicat.test( row, arg2 ) ) {
					return setCurrentRowNum(index);
				}
				index++;
			}
		}
		return false;
	}

	@Override
	public boolean findRow( final int startIndex, final Predicate< P > predicate ) {

		if( predicate != null && startIndex < rows().size() )
		{
			int index = Math.max( 0, startIndex );

			for( int i = index; i < rows.size(); i++ )
			{
				if( predicate.test( rows.get( i ) ) )
					return setCurrentRowNum( index );

				index++;
			}
		}
		return false;
	}

	/** {@inheritDoc}  */
	@Override
	public boolean setCurrentRow( P row ) {

		if( row != null ) {

			if( row != getCurrentRow( ) ) {
				int rowNum = rows().indexOf(row);
				if( rowNum != -1 )
					return setCurrentRowNum( rowNum );
				return false;
			}
			return true;
		}
		return false;
	}

	/**
     * {@inheritDoc}
     * <p>
     * В случае успеха формируется событие навигации.
     */
	@Override
	public boolean setCurrentRowNum( int rowNum ) {

		if( !rows().isEmpty() && rowNum >= 0 && rows().size() > rowNum ) {

			if( currentRowNum != rowNum ) {

				P oldCurrentRow	 = getCurrentRow();
				int	oldCurrentRowNum = currentRowNum;

				P currentRow		 = rows().get(rowNum);

				currentRowNum        = rowNum;

				fireNavigationEvent( oldCurrentRow, currentRow, oldCurrentRowNum, currentRowNum );
			}
			return true;
		}
		return false;
	}

    /*

    +------------------------------+
    | СЛУШАТЕЛИ И СОБЫТИЯ DATASET  |
    +------------------------------+

    */

	/** {@inheritDoc} */
	@Override
	public void addNavigationListener( IDataSetNavigationListener< P > navigationListener ) {
		navigationListeners.addListener(navigationListener);
	}

	/** {@inheritDoc} */
	@Override
	public void removeNavigationListener( IDataSetNavigationListener< P > navigationListener ) {
        navigationListeners.removeListener( navigationListener );
	}

	/** {@inheritDoc} */
	@Override
	public void addDataSetListener( IDataSetListener dataSetListener ) {
		dataSetListeners.addListener(dataSetListener);
	}

	/** {@inheritDoc} */
	@Override
	public void removeDataSetListener( IDataSetListener dataSetListener ) {
		dataSetListeners.removeListener(dataSetListener);
	}

	/** {@inheritDoc} */
	@Override
	public void addRowListener( IDataSetRowListener< P > rowListener ) {
		dataSetRowListeners.addListener(rowListener);
	}

	/**
	 * Формирование события связанного с поведением DataSet.
	 * <p>
	 * @param eventType
	 *          тип событие DataSet см. {@link DataSetEvent.DataSetEventType}
	 * @param before
	 *          признак запуска
	 *          {@code true } - перед событием
	 *          {@code false} - после
	 *
	 * @see DataSetEvent
	 * @see IDataSetListener
	 */
	protected void fireDataSetEvent( DataSetEvent.DataSetEventType eventType, boolean before ) {
		dataSetListeners.fire( new DataSetEvent( this, eventType, before ) );
	}

	/** {@inheritDoc} */
	@Override
	public void removeRowListener( IDataSetRowListener< P > rowListener ) {
        dataSetRowListeners.removeListener(rowListener);
	}

	/**
     * Формирование события связанного с навигацией по записям внутри DataSet.
     * <p>
     * Формируется когда происходит смена текущей записи и уведомляются слушатели.
     *
     * @param oldRow
     *          запись которая была текущей до события
     * @param newRow
     *          запись которая стала текущей
     * @param oldRowIndex
     *          номер записи, индекс, который была текущим до события, с 0
     * @param newRowIndex
     *          номер записи, индекс, который стала текущей, с 0
     *
     * @see DataSetNavigationEvent
     * @see IDataSetNavigationListener
     */
	protected void fireNavigationEvent( P oldRow, P newRow, int oldRowIndex, int newRowIndex ) {
		navigationListeners.fire( new DataSetNavigationEvent<>( this, oldRow, newRow, oldRowIndex, newRowIndex ) );
	}

	/**
     * Формирование события связанного с операциями над записями внутри DataSet.
     * <p>
     * Формируется, когда происходит операция вставки, обновления, удаления и т.д. записей
     *
     * @param rowOperation
     *          тип операции см. {@link DataSetRowEvent}
     * @param oldRow
     *          запись до операции
     * @param newRow
     *          запись после операции
     * @param rowIndex
     *          индекс записи над которой произошла операция, с 0
     * @param rowCount
     *          кол-во записей над которыми произошла операция
     *
     * @see DataSetRowEvent
     * @see IDataSetRowListener
     */
	protected void fireRowEvent( DataSetRowEvent.RowOperationEnum rowOperation, P oldRow, P newRow, int rowIndex, int rowCount ) {
		dataSetRowListeners.fire( new DataSetRowEvent<>( this, rowOperation, oldRow, newRow, rowIndex, rowCount ) );
	}


    /*
        +----------------------------------+
        | РАБОТА С ЗАПИСЯМИ ВНУТРИ DATASET |
        +----------------------------------+
    */

	/** {@inheritDoc} */
	@Override
	public Iterator< P > getRowIterator( Predicate< P > predicate ) {
		return predicate == null ? rows().iterator() : rows().stream().filter(predicate).iterator();
	}

	/** {@inheritDoc} */
	@Override
	public boolean isEmpty() {
		return rows().isEmpty();
	}

    /**
     * Сортирует строки внутри DataSet, сохраняя текущую запись.
     * <p>
     * Если текущая запись сместилась, формируется событие навигации с прежним
     * и новым индексами. Сама запись при этом не меняется.
     *
     * @param comparator
     *        объект сравнения; {@code null} означает отсутствие сортировки
     */
    public void sort( Comparator< P > comparator ) {
        if( comparator == null )
            return;

        final List<P> items = rows();
        final int oldIndex = currentRowNum;

        if( oldIndex < 0 || oldIndex >= items.size() ) {
            items.sort(comparator);
            return;
        }

        final P currentRow = items.get(oldIndex);

        // Одна и та же ссылка может присутствовать в списке несколько раз.
        // List.sort() стабилен: сохраняем номер вхождения этой ссылки.
        int occurrence = 0;
        for( int i = 0; i <= oldIndex; i++ ) {
            if( items.get(i) == currentRow )
                occurrence++;
        }

        int newIndex = -1;

        try {

            items.sort(comparator);
        }
        finally {
            // Даже если comparator бросил исключение, курсор должен указывать
            // на выбранный экземпляр (List.sort может частично переставить строки).
            int found = 0;

            for( int i = 0; i < items.size(); i++ ) {
                if( items.get(i) == currentRow && ++found == occurrence ) {
                    newIndex = i;
                    break;
                }
            }
            currentRowNum = newIndex;
        }

        if( newIndex != oldIndex )
            fireNavigationEvent(currentRow, newIndex < 0 ? null : currentRow, oldIndex, newIndex);
    }

	/** {@inheritDoc} */
    @Override
    public void forEach( Consumer< P > consumer, Predicate<P> predicate) {

        if( predicate == null )
            rows().forEach(consumer);
        else
            rows().stream().filter(predicate).forEach(consumer);
    }

    /** */
    public void dump( Map<String,Object> d ) {

        if( d == null )
            return;

        d.put("ds.rowClass", 	   getRowClass() != null ? getRowClass().getName() : "<null>"  );
        d.put("ds.name",     	   S.nvl ( getName() ) );
		d.put("ds.loadedRowCount", getLoadedRowCount() );

        if( properties != null ) {
            final StringBuilder sb = new StringBuilder();
            properties.forEach( (Object t, Object u) -> {
                sb.append(t).append(" = ").append( S.nvl(u) ).append('\n');
            });

            d.put("ds.properties", sb.toString() );
        }

		if( dataSetParameters != null )
			dataSetParameters.dump( d );


		/*
        if( parameterIndexMap != null ) {
            final StringBuilder sb = new StringBuilder();
            parameterIndexMap.forEach((Integer t, Object u) -> {
                sb.append(t).append(" = ")
                            .append( S.nvl(u) )
                            .append( u != null ? " (" + u.getClass().getSimpleName() + ")" : ""  )
                            .append('\n');
            });
            d.put("ds.parameterIndexMap", sb.toString() );
        }

        if( parameterNamedMap != null ) {
            final StringBuilder sb = new StringBuilder();
            parameterNamedMap.forEach((String t, Object u) -> {
                sb.append(t).append(" = ")
                            .append( S.nvl(u) )
                            .append( u != null ? " (" + u.getClass().getSimpleName() + ")" : ""  )
                            .append('\n');
            });
            d.put("ds.parameterNamedMap", sb.toString() );
        }
		*/
    }
}
