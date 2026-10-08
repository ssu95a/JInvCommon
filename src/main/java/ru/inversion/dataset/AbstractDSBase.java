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
    public Class<P> getRowClass() {
        return (Class<P>)rowClass;
    }

    /**
    * Устанавливает тип записи для TreeDataSet.
    * <p>
    * @param rowClass
    *        класс записи
    *
    * @throws IllegalStateException
    *         если происходит изменить тип записи TreeDataSet, с уже загруженными записями
    */
    public void setRowClass( Class<? extends P> rowClass ) {

        if( this.rowClass != rowClass ) {

            if( !isEmpty() )
                throw new IllegalStateException( Tags.PRODUCT_LABEL + "Can't change the type of record, because it is already loaded other type of entry" );

            this.rowClass = (Class)rowClass;
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
    public <T> T getProperty(String propertyName) {
        return properties == null ? null : (T)properties.get(propertyName);
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

    /** */
    public void setParameter( String name, Object value ) {
        if( dataSetParameters == null )
            dataSetParameters = new ParametersValues();
        this.dataSetParameters.set(name, value);
    }

    /** */
    public void setParameter( int index,  Object value ) {
        if( dataSetParameters == null )
            dataSetParameters = new ParametersValues();
        this.dataSetParameters.set(index, value);
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
     * Возвращает значение индексированного параметра.
     * <p>
     * SQLDataSet различает ситуацию когда параметр установлен в {@code null} и когда параметр отсутствует.
     * При отсутствии параметра выбрасывается исключение.
     *
     * @param index
     *        индекс параметра
     *
     * @return
     *        значение параметра
     *
     * @throws
     *          DataSetParameterException если параметр с таким индексом отсутствует
     */
    public Object getParameterValue( int index ) throws DataSetParameterException {

        Object value = NO_PARAMETER_VALUE;

        IParameters cp = getCallbackParameters();

        if( cp != null && cp.hasParameter(index) )
            value = cp.getParameter(index);

        if( value == NO_PARAMETER_VALUE && hasParameter(index) )
            value = getParameter(index);

        if( value == NO_PARAMETER_VALUE )
            DataSetParameterException.throwNoSetParameterValue(index);

        return value;
    }

    /**
     * Возвращает значение именованного параметра.
     * <p>
     * SQLDataSet различает ситуацию когда параметр установлен в {@code null} и когда параметр отсутствует.
     * При отсутствии параметра выбрасывается исключение.
     *
     * @param name
     *        имя параметра
     *
     * @return значение параметра
     *
     * @throws
     *         DataSetParameterException если параметр с таким именем отсутствует
     */
    public Object getParameterValue( String name ) throws DataSetParameterException {

        Object value = NO_PARAMETER_VALUE;

        IParameters cp = getCallbackParameters();

        if( cp != null && cp.hasParameter(name) )
            value = cp.getParameter(name);

        if( value == NO_PARAMETER_VALUE && hasParameter(name) )
            value = getParameter(name);

        if( value == NO_PARAMETER_VALUE )
            DataSetParameterException.throwNoSetParameterValue(name);

        return value;
    }

}
