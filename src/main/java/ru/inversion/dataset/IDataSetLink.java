package ru.inversion.dataset;

import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 *
 * Связь master-detail двух DataSet.
 * <p>
 * Предполагается, что данные в одном DataSet (destination) зависят от данных в другом (source)
 * И при изменении source, также изменяются и в destination.
 * 
 * @param <T>
 *          тип записи в source DataSet
 * 
 * @author sulimoff
 */
public interface IDataSetLink<T> extends IDataLink<IDataSet,IDataSet>, AutoCloseable {
	
    /**
     * Возвращает ссылку на source DataSet.
     * @return  source DataSet
     */
	IDataSet<T> getSource( );

    /**
     * Возвращает ссылку на destination DataSet.
     * @return  destination DataSet
     */
	IDataSet getDestination( );
	
    /** 
     * Признак автообновление destination DataSet, если изменились параметры source DataSet.
     * 
     * @return 
     *          {@code true } автообновление включено
     *          {@code false} выключено
     * 
     * @see #setAutoRequery(boolean) 
     */
    boolean isAutoRequery( );
    
    /** 
     * Устанавливает признак автообновление destination DataSet, если изменились параметры source DataSet.
     * 
     * @param autoRequery
     *          {@code true } автообновление включено
     *          {@code false} выключено
     * 
     * @see #isAutoRequery 
     */
    void setAutoRequery( boolean autoRequery );

    /** */
    void setAutoRequery( Supplier<Boolean> requeryIf );

    /**
     * Инициализация значений записи в destination DataSet
     * значениями полей из текущей записи source DataSet
     *
     * @param initializer
     *        метод инициализации,
     *              первый параметр - Object - значение из поля текущей записи source DataSet
     *              второй          - String - имя поля в destination DataSet
     *
     * Пример использования:
     * @{code
     *  ...
     *  IDataSetLink link = ...
     *
     *  final T pojo = new T();
     *
     *  link.initDestinationRow( ( value, columnName )-> {
     *          switch( columnName ) {
     *              case "C1":
     *                  pojo.setC1( value );
     *              break;
     *              case "C2":
     *                  pojo.setC2( value );
     *              break;
     *          }
     *      }
     *  );
     *
     * }
     */
    void initDestinationRow( BiConsumer<Object,String> initializer );
}
