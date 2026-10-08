package ru.inversion.dataset;

import ru.inversion.utils.Tags;

/**
  * Интерфейс представляющий одно выражение на языке SQL.
  * Считается что такое выражение есть совокупность текста скрипта SQL и параметров
  * 
  * является интерфейсом-утилитой, используется для нужд dataSet 
  * 
  * @see IDataSet, IParameters
  * 
  * @author sulimoff
  * @version 1.0.0
 */
public interface ISQLExpression extends IParameters {
	
    /**
     * Возвращает текст SQL скрипта.
     * 
     * @return  SQL скрипт
     * */
	String getSQL( );

	/**
     * Вовзращает текст SQL с вставленными значениями параметров.
     * <p/>
     * */
    default String getCompletedSQL() {
        throw new UnsupportedOperationException( Tags.PRODUCT_LABEL + "getCompletedSQL" );
    }
}
