package ru.inversion.utils;

import java.util.function.BiConsumer;
import java.util.function.Function;

/** */
public interface IAttrSet extends BiConsumer<String,Object>, Function<String,Object> {

    // CASE_INSENTIVE_MODE добавить флаг/режим

    default void accept( String attribute, Object value ) { set(attribute,value); }
    default Object apply( String attribute ) { return get(attribute); }

    public void  set( String attribute, Object value );
    public void  put( String attribute, Object value);
    public <T> T get( String attribute );

    /** установка значения индексируемого атрибута по его названию */
    public void  set( String attribute, int index, Object value );
    /** получение значения индексируемого атрибута по его названию */
    public <T> T get( String attribute, int index );

    /** установка значения индексируемого строкой по его названию */
    public void  set( String attribute, String identifier, Object value );
    /** получение значения атрибута по его названию и индексу */
    public <T> T get( String attribute, String identifier );

    /** установка значения индексируемого строкой по его названию */
    public <E extends Enum<E>> void set( String attribute, E identifier, Object value );
    /** получение значения атрибута по его названию и индексу */
    //public <T>, <E extends Enum<E>> T get( String attribute, E identifier );

    /** добавление в конец индексируемого атрибута по его названию
     *  возвращает индекс добавленного элемента */
    public int add( String attribute, Object value );

}
