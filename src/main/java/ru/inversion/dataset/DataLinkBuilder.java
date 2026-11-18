package ru.inversion.dataset;

import ru.inversion.utils.Tags;
import ru.inversion.utils.Triplet;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Конструирует объект связи {@link IDataSetLink} между двумя DataSet.
 * <p>
 * Предполагается что такой объект будет реагировать на события из ведущего DataSet и обновлять данные в ведомом.
 * Ведущий DataSet м.б. любым реализующим интерфейс IDataSet.
 * Ведомый реализующим интерфейс ISQLDataSet.
 * Зависимость ведущего от ведомого DataSet осуществляется посредством усиления предиката SQL выражения, на котором построен ведомый DataSet.
 * <p>
 * Для организации связи нужно определить поле(поля) которые будут участвовать в усилении предиката в ведущем DataSet 
 * и метод(lambda) получения значения для этого поля из текшей записи ведущего DataSet.
 * <p>
 * <ul>События в ведущем DataSet:
 * <li>навигации {@link DataSetNavigationEvent} - полностью обновляется ведомый DataSet</li>
 * <li>очистки данных {@link DataSetEvent} CLEAR - полностью очищает ведомый DataSet</li>
 * <li>обновления текущей записи {@link DataSetRowEvent} REFRESH - полностью очищает ведомый DataSet</li>
 * </ul>
 * 
 * @version 1.0.1
 * 
 * @author sulimoff
 */
public class DataLinkBuilder<T> {
	
    // Данные DataSetLinkBuilder
    
	private IDataSet<? extends T> source;
	private ISQLDataSet<?> destination;
	private final List<Triplet<Function<? extends T,Object>,String, Boolean>> pairsColumn = new ArrayList<>();
	private Supplier<Boolean> requeryIf;

	/** */
	public DataLinkBuilder( ) {
	}
    
	/**
     * Установить ведущий DataSet.
     * <p>
     * @param source
     *          ведущий DataSet
     * @return
     *          this
     */
	public DataLinkBuilder<T> source( IDataSet<? extends T> source ) {
		this.source = source;
		return this;
	}
    
	/**
     * Установить ведомый DataSet.
     * <p>
     * @param destination
     *          ведомый DataSet
     * @return
     *          this
     */
	public DataLinkBuilder<T> destination( ISQLDataSet destination ) {
		this.destination = destination;
		return this;
	}
    
	/** 
     * Параметры связи между колонками ведущего и ведомого DataSet.
     * <p>
     * @param sourceValue
     *          lambda возвращающая значение поля из текущей записи ведущего DataSet.
     * @param destinationColumn
     *          название колонки в SQL выражении ведомого DataSet, на которое будет наложено условие. 
     * @return  
     *          this
     */
	public DataLinkBuilder<T> joinColumn( Function<T,Object> sourceValue, String destinationColumn ) {
		return joinColumn( sourceValue, destinationColumn, true );
	}
	public DataLinkBuilder<T> joinColumn( Function<T,Object> sourceValue, String destinationColumn, boolean useInFilter ) {
		pairsColumn.add( Triplet.makeTriplet( sourceValue, destinationColumn, useInFilter ));
		return this;
	}

	/** 
     * Параметры связи между 2 парами колонок ведущего и ведомого DataSet.
     * <p>
     * @param sourceValue1
     *          lambda возвращающая 1 значение поля из текущей записи ведущего DataSet.
     * @param sourceValue2
     *          lambda возвращающая 2 значение поля из текущей записи ведущего DataSet.
     * @param destinationColumn1
     *          название колонки 1 в SQL выражении ведомого DataSet, на которое будет наложено условие. 
     * @param destinationColumn2
     *          название колонки 2 в SQL выражении ведомого DataSet, на которое будет наложено условие. 
     * @return  
     *          this
     */
	public DataLinkBuilder<T> joinColumn2( Function<T,Object> sourceValue1, Function<T,Object> sourceValue2, String destinationColumn1, String destinationColumn2 ) {
		//pairsColumn.add( Pair.makePair( sourceValue1, destinationColumn1 ));
		//pairsColumn.add( Pair.makePair( sourceValue2, destinationColumn2 ));
		this.joinColumn(sourceValue1, destinationColumn1);
		this.joinColumn(sourceValue2, destinationColumn2);
		return this;
	}
    
	/**
     * Признак автообноления. 
     * <p>
     * @param autoRequery
     *          
     * @return 
     *          this
     * 
     * @see IDataSetLink#isAutoRequery() 
     */
	public DataLinkBuilder<T> autoRequery( boolean autoRequery ) {
		autoRequery( ()->autoRequery );
		return this;
	}

	/** */
	public DataLinkBuilder<T> autoRequery( Supplier<Boolean> requeryIf ) {
		this.requeryIf = requeryIf;
		return this;
	}

	/**
     * Создает объект связи между 2-мя DataSet.
     * <p>
     * @return  
     *          {@link  IDataSetLink}
     * @throws 
     *          IllegalStateException если не все параметры корректно установлены.
     */ 
	public IDataSetLink<T> build( ) {
		
		if( source == null || destination == null )
			throw new IllegalStateException( Tags.PRODUCT_LABEL + "DataLinkBuilder: source or destination is null" );

		if( pairsColumn.isEmpty() )
			throw new IllegalStateException( Tags.PRODUCT_LABEL + "DataLinkBuilder: no join column specified" );
		
		DLBuilder<IDataSet<? extends T>, ISQLDataSet<?>, T, Object> builder = new DLBuilder<>();
		builder.source(source).destination(destination).requeryIf(requeryIf);
		for (Triplet<Function<? extends T,Object>,String,Boolean> column : pairsColumn)
			builder.joinColumn((Function<T,Object>)column.first, column.second, column.third);
		return (IDataSetLink<T>)(Object)builder.link();
	}
    
    /** 
     * Очищает данные ведомого DataSet.
     * <p>
     * После вызова данной операции возможно заново установить параметры ведомого DataSet,
     * для повторного использования.
     */
    public void clearDestinationData( ) {
        destination = null;
		requeryIf	= null;
        pairsColumn.clear();
    }
    
	/**
     * Создание связи между 2-мя DataSet, по 1-м столбцу из каждого.
     * <p>
     * Статический метод для простых случаев.
     * @param <T>
     *          Тип записи ведущего DataSet
     * @param source
     *          Ведущий DataSet
     * @param destination
     *          Ведомый DataSet 
     * @param sourceValue
     *          lamba получения значения поля ведущего DataSet
     * @param destinationColumn
     *          имя поля для связи в ведомом DataSet
     * @return  
     *          {@link  IDataSetLink}
     * 
     * @see DataLinkBuilder
     */
	public static <T> IDataSetLink<T> linkDataSet( IDataSet<? extends T> source, ISQLDataSet destination, Function<T,Object> sourceValue, String destinationColumn ) {
		return (new DataLinkBuilder<T>().source(source).destination(destination).joinColumn( sourceValue, destinationColumn )).build();
	}
    
	/**
     * Создание связи между 2-мя DataSet, по 1-м столбцу из каждого.
     * <p>
     * Статический метод для простых случаев.
     * @param <T>
     *          Тип записи ведущего DataSet
     * @param source
     *          Ведущий DataSet
     * @param destination
     *          Ведомый DataSet 
     * @param sourceValue
     *          lamba получения значения поля ведущего DataSet
     * @param destinationColumn
     *          имя поля для связи в ведомом DataSet
     * @param autoRequery
     *          признак автообновления
     * @return  
     *          {@link  IDataSetLink}
     * 
     * @see DataLinkBuilder
     */
	public static <T> IDataSetLink<T> linkDataSet( IDataSet<? extends T> source, ISQLDataSet destination, Function<T,Object> sourceValue, String destinationColumn, boolean autoRequery ) {
		return (new DataLinkBuilder<T>().source(source).destination(destination).joinColumn(sourceValue, destinationColumn).autoRequery(autoRequery)).build();
	}
	public static <T> IDataSetLink<T> linkDataSet( IDataSet<? extends T> source, ISQLDataSet destination, Function<T,Object> sourceValue, String destinationColumn, Supplier<Boolean> requeryIf ) {
		return (new DataLinkBuilder<T>().source(source).destination(destination).joinColumn(sourceValue, destinationColumn).autoRequery(requeryIf)).build();
	}

    /** 
     * UP.
     */
    public static <T> IDataSetLink<T> linkDataSet ( 
            IDataSet<? extends T> source, 
            ISQLDataSet destination, 
            Function<T,Object> sourceValue, 
            Function<T,Object> sourceValue2, 
            String destinationColumn, 
            String destinationColumn2, 
            boolean autoRequery ) 
    {
		return (new DataLinkBuilder<T>()
                .source(source)
                .destination(destination)
                .joinColumn( sourceValue, destinationColumn )
                .joinColumn( sourceValue2, destinationColumn2)
                .autoRequery(autoRequery))
                .build();
    }
}
