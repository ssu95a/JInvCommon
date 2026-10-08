package ru.inversion.dataset;

import ru.inversion.utils.IDumpable;

/** */
public interface IDSBase<P> extends IParameters, IDumpable, AutoCloseable {

   /**
    * Возвращает тип записи
    * <p>
    * Если не установлена то {@code null}
    *
    * @return {@code class} записи в DataSet
    */
   Class<P> getRowClass( );

    /**
     * Наименование DataSet.
     * <p>
     * Имя задаваемое программистом или автоматически для DataSet.
     * Применяется для ведения и сохранения различного вида настроек связанных с DataSet
     *
     * @return имя DataSet заданное через {@link  #setName} или {@code null} если имя не было задано
     */
    String getName( );

    /**
     * Установка наименования DataSet.
     * <p>
     * @param name
     *        наименование DataSet, {@code null} если требуется очистить наименование.
     *
     * @see #getName()
     */
    void setName(String name);

    /**
     * Установить свойство DataSet.
     * <p>
     * Для использования разработчиками в своих приложениях

     * @param propertyName
     *        наименование свойства
     * @param value
     *        значение свойства
     *
     * @see #getProperty
     */
    void setProperty( String propertyName, Object value );

    /**
     * Получить свойство DataSet установленное через {@code setProperty}.
     * <p>
     * Для использования разработчиками в своих приложениях

     * @param propertyName
     *        наименование свойства
     *
     * @return значение свойства, либо {@null} если свойство с таким именем не было установлено.
     *
     * @see #setProperty
     */
    public <T> T getProperty(String propertyName);

    /**
     * Очищает все записи внутри DataSet.
     * <p>
     * Удаляются все записи из dataSet.
     */
    void clear( );

    /** Возвращает признак имеются ли в dataSet записи или нет*/
    boolean isEmpty();

   /**
    * Устанавливает внешний набор параметров для DataSet.
    * <p>
    * {@code IDataSet} extend'ед интерфейс {@code IParameters} и в его имплементации предполагается поддержка работы с параметрами.
    * Также возможна дополнительная установка набора параметров из вне.
    *
    * @param parameters
    *          внешний набор параметров для DataSet, {@code null} для очистки внешнего набора внутри DataSet
    *
    * @see IParameters
    */
   void setCallbackParameters( IParameters parameters );

  /**
   * Возвращает внешний набор параметров установленный методом {@code setCallbackParameters}.
   * <p>
   * @return набор параметров или {@code null} если он не был установлен.
   *
   * @see IParameters
   */
   IParameters getCallbackParameters(  );

    /** */
    default boolean isEnableMark() {
        return false;
    }

    /**
     * Возвращает признак, что DataSet поддерживает режим пометки записей.
     * <p>
     * В базовой реализации определено, что пометка не поддерживается.
     * Производные классы должны этот метод перекрыть если будут поддерживать пометку.
     *
     * @return признак поддержки пометки
     */
    default boolean isSupportMark( ) {
        return false;
    }
}
