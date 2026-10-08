package ru.inversion.db;

import ru.inversion.dataset.IParameters;
import ru.inversion.dataset.IRowMapper;
import ru.inversion.tc.TaskContext;
import ru.inversion.utils.ParametersValues;
import ru.inversion.utils.S;
import ru.inversion.utils.Tags;
import ru.inversion.utils.U;

import java.util.Objects;

/**
 * 
 * @author ssu
 */
public abstract class AbstractDBResult<R,V,C extends AbstractDBResult> implements IParameters, AutoCloseable {

    public enum ModeEnum {
        QUERY,
        STORED,
        TABLE
    }
    final protected TaskContext taskContext;

    protected ParametersValues parametersValues;
    protected IParameters callbackParameters;

    protected Class <V> valueClass;
    protected Class <R> runClass;

    protected String query;
    protected IRowMapper<V> mapper;

    protected boolean throwOnNoDataFound = false;

    protected ModeEnum mode = ModeEnum.QUERY;

    protected String where, orderBy;

    /** */
    protected AbstractDBResult( TaskContext tc)  {
        this.taskContext = Objects.requireNonNull(tc,"'tc' is null");
    }

    /** */
    protected Class getRunClass() {
        return U.nvl( runClass, valueClass );
    }

    /** */
    public C valueClass( Class<V> vClass ) throws DBResultException {
        this.valueClass = vClass;
        return (C)this;
    }
    /** */
    public C runClass( Class<R> rClass ) throws DBResultException {
        this.runClass = rClass;
        return (C)this;
    }
    /** */
    public C mode( ModeEnum m ) throws DBResultException {
        this.mode = m;
        return (C)this;
    }
    /** */
    public C queryName( String name ) throws DBResultException {
        this.query = name;
        return (C)this;
    }
    /** */
    public C sql( String v ) throws DBResultException {
        this.query = v;
        return (C)this;
    }

    /** */
    public C mapper( IRowMapper<V> mapper ) throws DBResultException {
        this.mapper = mapper;
        return (C)this;
    }
    /** */
    public C throwOnNoDataFound( boolean doThrow ) throws DBResultException {
        this.throwOnNoDataFound = doThrow;
        return (C)this;
    }
    /** */
    public C parameters( IParameters parameters ) throws DBResultException {
        this.callbackParameters = parameters;
        return (C)this;
    }
    /** */
    public C where( String where ) throws DBResultException {
        this.where = where;
        return (C)this;
    }
    /** */
    public C orderBy( String orderBy ) throws DBResultException {
        this.orderBy = orderBy;
        return (C)this;
    }

    /** */
    private Object internalGetParameter( String parameterName ) {
        return parametersValues == null ? null : parametersValues.get( parameterName );
    }

    /** */
    private Object internalGetParameter( int parameterIndex ) {
        return parametersValues == null ? null : parametersValues.get( parameterIndex );
    }

    /** */
    @Override
    public Object getParameter( int index ) {

        Object value = NO_PARAMETER_VALUE;

        if( callbackParameters != null && callbackParameters.hasParameter(index) )
            value = callbackParameters.getParameter(index);

        if( value == NO_PARAMETER_VALUE && hasParameter(index) )
            value = internalGetParameter(index);

        if( value == NO_PARAMETER_VALUE )
            throw new IllegalStateException( Tags.PRODUCT_LABEL + "No value for parameter '" + index + "'" );

        return value;
    }

    /** */
    @Override
    public Object getParameter( String name ) {

        Object value = NO_PARAMETER_VALUE;

        if( callbackParameters != null && callbackParameters.hasParameter(name) )
            value = callbackParameters.getParameter(name);

        if( value == NO_PARAMETER_VALUE && hasParameter(name) )
            value = internalGetParameter(name);

        if( value == NO_PARAMETER_VALUE )
            throw new IllegalStateException( Tags.PRODUCT_LABEL + "No value for parameter '" + S.nvl(name) + "'" );

        return value;
    }

    /** */
    protected Object[] collectParameters() {

        if( parametersValues == null && callbackParameters == null )
            return new Object[0];

        int maxIndex = 0;

        for( int i = 1; hasParameter(i); i++ ) {
            maxIndex = i;
        }

        Object[] params = new Object[maxIndex];
        for (int i = 0; i < maxIndex; i++) {
            params[i] = getParameter(i + 1);
        }

        return params;
    }

    /** */
    protected void check4Exec( ) throws DBResultException {

        try {

            Objects.requireNonNull( taskContext, "'taskContext' is null" );

            if( valueClass == null && runClass == null )
                Objects.requireNonNull( null, "'valueClass' and 'runClass' is null" );

        } catch( Throwable th ) {
            throw new DBResultException( Tags.PRODUCT_LABEL + "Object " + getClass().getSimpleName() + " is not configured correctly", th );
        }

    }

    /** */
    @Override
    public void setParameter( String parameterName, Object value ) {
        parametersValues.setParameter( parameterName, value );
    }

    @Override
    public void setParameter( int parameterIndex, Object value ) {
        parametersValues.setParameter( parameterIndex, value );
    }

//    /** */
//    @Override
//    public C set( String parameterName, Object value ) {
//        setParameter( parameterName, value );
//        return (C)this;
//    }
//
//    @Override
//    public C set( int parameterIndex, Object value ) {
//        setParameter( parameterIndex, value );
//        return (C)this;
//    }

    /** */
    @Override
    public void close() {
    }

}

