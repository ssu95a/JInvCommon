package ru.inversion.datacall;


import ru.inversion.dataset.IParameters;

import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Базовый интерфейс для выполнения хранимого блока кода на языке SQL, PL/SQL
 * <p>
 * Наподобие IDataSet, который строится вокруг SQL выражения выборки - select,
 * DataCall инкапсулирует вызов хранимого кода и предоставляет доступ к параметрам
 * устанавливаемым до выполнения кода и получаемым после выполнения
 *
 * Все методы в случае ошибки кидают CallException
 *
 * @author Sulimoff
 * @version beta 1.0/16.09.2023
 *
 * @see ru.inversion.dataset.IParameters
 * @see CallException
 * */
public interface IDataCall extends IParameters, AutoCloseable {

    /**
     * Наименование
     * <p>
     * Имя задаваемое программистом.
     * Применяется для ведение и сохранения различного вида настроек связанных с DataCall
     *
     * @return имя dataCall заданное через {@link  #setName} или {@code null} если имя не было задано
     */
    String getName( ) throws CallException;

    /**
     * Установка наименования dataCall
     * <p>
     * @param name
     *        наименование DataCall, {@code null} если требуется чтобы очистить наименование.
     */
    void setName( String name ) throws CallException;

    /**
     * Установить свойство DataCall.
     * <p>
     * Для использования разработчиками в своих приложениях

     * @param propertyName
     *          наименование свойства 
     * @param value
     *          значение свойства
     *
     * @see #getProperty
     */
    void setProperty( String propertyName, Object value ) throws CallException;

    /**
     * Получить свойство DataCall установленное через {@code setProperty}.
     * <p>
     * Для использования разработчиками в своих приложениях

     * @param propertyName
     *          наименование свойства 
     *
     * @return значение свойства, либо {@null} если свойство с таким именем не было установлено.
     *
     * @see #setProperty
     */
    Object getProperty( String propertyName ) throws CallException;

    /** Выполнение хранимого кода
      */
    <C extends IDataCall> C execute( ) throws CallException;

    /** Установка входного параметра для хранимого кода, через supplier
     * вызывается для параметра, перед каждым выполнением кода
     *
     * @param id - идентификатор параметра, м.б., как строка так и целое число
     * @param supplier - объект предоставления значения параметра
     *
     * @throws CallException
     * */
    <V> void setValueCallback( Object id, Supplier<V> supplier ) throws CallException;

    /** Установка слущателя на значение выходного параметра
     * <p>
     *  Вызывается после каждого выполнения кода
     *
     * @param id - идентификатор параметра, м.б. как строка так и целое число
     * @param consumer - слушатель для значения параметра
     *
     * @throws CallException
     * */
    <V> void setValueListener( Object id, BiConsumer<Object,V> consumer ) throws CallException;

    /** Установка единого слушателя на значение всех OUT параметров
     * <p>
     *  Вызывается после каждого выполнения кода
     *
     * @param consumer - слушатель для значения параметров
     *
     * @throws CallException
     * */
    void setValueListener( BiConsumer<Object,Object> consumer ) throws CallException;

    /** fluent style */
    default <V> IDataCall valueCallback( Object id, Supplier<V> supplier ) throws CallException { setValueCallback(id, supplier); return this;}

    /** fluent style */
    default <V> IDataCall valueListener( Object id, BiConsumer<Object,V> consumer ) throws CallException { setValueListener(id, consumer); return this; };

    /** fluent style */
    default IDataCall valueListener( BiConsumer<Object,Object> consumer ) throws CallException { setValueListener(consumer); return this; };

    /** */
    void addDataCallListener( IDataCallListener l ) throws CallException;

    /** */
    void removeDataCallListener( IDataCallListener l ) throws CallException;

    /** Возвращает описание параметра */
    ParameterMetadata<?> getParameterMetadata( Object id ) throws CallException;

    /** Список параметров */
    Iterable<ParameterMetadata<?>> getParameterIter();

    @Override
    default
        IDataCall set( String parameterName, Object value ) {
            setParameter( parameterName, value );
            return this;
        }

    @Override
    default
        IDataCall set( int parameterIndex, Object value ) {
            setParameter( parameterIndex, value );
            return this;
        }

    default <T> T get( String parameterName ) {
        return (T)getParameter(parameterName);
    }
    default <T> T get( int parameterIndex )   { return (T)getParameter(parameterIndex);}

    default <T> T getReturnValue( )           { throw new UnsupportedOperationException("getReturnValue"); }

    //<T> T getReturnValue( );

    void close( );
}
