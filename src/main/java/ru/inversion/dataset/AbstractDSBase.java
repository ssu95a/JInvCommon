package ru.inversion.dataset;

import ru.inversion.utils.ParametersValues;
import ru.inversion.utils.Tags;

import java.util.HashMap;
import java.util.Map;

/** */
public abstract class AbstractDSBase<P> implements IDSBase<P> {

    /**
     * Тип записи содержащихся в DataSet.
     * <p>
     * @see #setRowClass
     * @see #getRowClass
     */
    protected Class<? extends P> rowClass;

    /**
     * Параметры хранящиеся внутри DataSet
     */
    protected ParametersValues dataSetParameters;

    /**
     * Набор параметров для DataSet установленных из вне.
     * @see #setCallbackParameters
     */
    protected IParameters callbackParameters;

    /**
     * Наименование DataSet.
     */
    protected String name;

    /**
     * Дополнительные пользовательские свойства DataSet.
     */
    protected Map<String,Object> properties;

    /** */
    public AbstractDSBase( Class< ? extends P > rowClass ) {
        this.rowClass = rowClass;
    }

    /** */
    @Override
    @SuppressWarnings("unchecked")
    public Class<P> getRowClass() {
        return (Class<P>) rowClass;
    }

    /**
     * Меняет тип записи только у пустого DataSet.
     * Повторная установка того же Class допускается и для непустого DataSet.
     *
     * @throws IllegalStateException если тип изменяется при наличии записей
     */
    public void setRowClass( Class<? extends P> rowClass ) {

        if( this.rowClass != rowClass ) {

            if( !isEmpty() )
                throw new IllegalStateException( Tags.PRODUCT_LABEL + "Can't change the type of record, because it is already loaded other type of entry" );

            this.rowClass = rowClass;
        }
    }

    /** */
    @Override
    public String getName() {
        return name;
    }

    /** */
    @Override
    public void setName(String name) {
        this.name = name;
    }

    /** {@inheritDoc} */
    @Override
    @SuppressWarnings("unchecked")
    public <T> T getProperty(String propertyName) {
        return properties == null ? null : (T) properties.get(propertyName);
    }

    /** {@inheritDoc} */
    @Override
    public void setProperty(String propertyName, Object value) {
        if( properties == null )
            properties = new HashMap<>();
        properties.put( propertyName, value );
    }

    /** {@inheritDoc} */
    @Override
    public void setCallbackParameters(IParameters parameters) {
        this.callbackParameters = parameters;
    }

    /** {@inheritDoc}  */
    @Override
    public IParameters getCallbackParameters() {
        return callbackParameters;
    }

    /** Создаёт внутреннее хранилище только при первой установке параметра. */
    private ParametersValues parametersForWrite() {
        if (dataSetParameters == null)
            dataSetParameters = new ParametersValues();
        return dataSetParameters;
    }

    /** */
    @Override
    public void setParameter(String name, Object value) {
        parametersForWrite().set(name, value);
    }

    /** */
    @Override
    public void setParameter(int index, Object value) {
        parametersForWrite().set(index, value);
    }

    /** */
    @Override
    public Object getParameter( String parameterName ) {
        return dataSetParameters == null ? null : dataSetParameters.getParameter(parameterName);
    }

    /** */
    @Override
    public Object getParameter( int parameterIndex ) {
        return dataSetParameters == null ? null : dataSetParameters.getParameter(parameterIndex);
    }

    /**
     * Проверка наличия именованного параметра во внутренней коллекции DataSet.
     * <p>
     * По умолчанию DataSet разделяет ситуацию, когда параметр отсутствует и когда его значение равно {@code null}.
     * Поэтому перед тем как получить значение какого-либо параметра необходимо проверить его наличие данным методом.
     *
     * @param parameterName
     *          наименование параметра, который проверяется
     *
     * @return {@code true} если параметр присутствует, {@code false} если нет.
     *
     * @see IParameters#hasParameter(java.lang.String)
     */
    @Override
    public boolean hasParameter( String parameterName ) {
        return dataSetParameters != null && dataSetParameters.hasParameter(parameterName);
    }

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
     */
    @Override
    public boolean hasParameter( int parameterIndex ) {
        return dataSetParameters != null && dataSetParameters.hasParameter(parameterIndex);
    }

    /**
     * Возвращает значение параметра: сначала callback, затем собственные параметры.
     * Значение {@code null} считается заданным; {@link IParameters#NO_PARAMETER_VALUE}
     * означает отсутствие значения и допускает fallback.
     *
     * @throws DataSetParameterException если параметр не задан
     */
    public Object getParameterValue( int index ) throws DataSetParameterException {

        Object value = NO_PARAMETER_VALUE;

        IParameters callback = getCallbackParameters();

        if (callback != null && callback.hasParameter(index))
            value = callback.getParameter(index);

        // null считается заданным значением. Только sentinel разрешает fallback.
        if (value == NO_PARAMETER_VALUE && hasParameter(index))
            value = getParameter(index);

        if (value == NO_PARAMETER_VALUE)
            DataSetParameterException.throwNoSetParameterValue(index);

        return value;
    }

    /**
     * Возвращает именованный параметр с тем же приоритетом источников,
     * что и {@link #getParameterValue(int)}.
     *
     * @throws DataSetParameterException если параметр не задан
     */
    public Object getParameterValue( String name ) throws DataSetParameterException {

        Object value = NO_PARAMETER_VALUE;

        IParameters callback = getCallbackParameters();

        if (callback != null && callback.hasParameter(name))
            value = callback.getParameter(name);

        // null считается заданным значением. Только sentinel разрешает fallback.
        if (value == NO_PARAMETER_VALUE && hasParameter(name))
            value = getParameter(name);

        if (value == NO_PARAMETER_VALUE)
            DataSetParameterException.throwNoSetParameterValue(name);

        return value;
    }

}
