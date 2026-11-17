package ru.inversion.datacall;

import ru.inversion.dataset.IParameters;
import ru.inversion.tc.TaskContext;
import ru.inversion.utils.Tags;

import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Compatibility facade for the original PostgreSQL anonymous-call prototype.
 *
 * @deprecated use {@link SQLCallBuilder}; PostgreSQL anonymous blocks are
 *             executed by {@link JsonSqlCall}.
 */
@Deprecated
public class PGAnonymousSqlCall implements IDataCall {

    private final JsonSqlCall delegate;

    /** */
    public PGAnonymousSqlCall(
            TaskContext tc,
            CallStatement callStatement,
            IParameters callBackParameters,
            String name
    ) {
        try {
            delegate = new JsonSqlCall(tc, callStatement, callBackParameters, name);
        }
        catch( CallException e ) {
            throw new IllegalStateException(
                    Tags.PRODUCT_LABEL + "Error on create PostgreSQL anonymous call",
                    e
            );
        }
    }

    @Override
    public String getName() throws CallException {
        return delegate.getName();
    }

    @Override
    public void setName( String name ) throws CallException {
        delegate.setName(name);
    }

    @Override
    public void setProperty( String propertyName, Object value ) throws CallException {
        delegate.setProperty(propertyName, value);
    }

    @Override
    public Object getProperty( String propertyName ) throws CallException {
        return delegate.getProperty(propertyName);
    }

    @Override
    public <V> void setValueCallback( Object id, Supplier<V> supplier ) throws CallException {
        delegate.setValueCallback(id, supplier);
    }

    @Override
    public <V> void setValueListener( Object id, BiConsumer<Object,V> listener ) throws CallException {
        delegate.setValueListener(id, listener);
    }

    @Override
    public void setValueListener( BiConsumer<Object,Object> listener ) throws CallException {
        delegate.setValueListener(listener);
    }

    @Override
    public void addDataCallListener( IDataCallListener listener ) throws CallException {
        delegate.addDataCallListener(listener);
    }

    @Override
    public void removeDataCallListener( IDataCallListener listener ) throws CallException {
        delegate.removeDataCallListener(listener);
    }

    @Override
    public ParameterMetadata<?> getParameterMetadata( Object id ) throws CallException {
        return delegate.getParameterMetadata(id);
    }

    @Override
    public Iterable<ParameterMetadata<?>> getParameterIter() {
        return delegate.getParameterIter();
    }

    @Override
    public Object getParameter( String parameterName ) {
        return delegate.getParameter(parameterName);
    }

    @Override
    public Object getParameter( int parameterIndex ) {
        return delegate.getParameter(parameterIndex);
    }

    @Override
    public void setParameter( String parameterName, Object value ) {
        delegate.setParameter(parameterName, value);
    }

    @Override
    public void setParameter( int parameterIndex, Object value ) {
        delegate.setParameter(parameterIndex, value);
    }

    @Override
    public boolean hasParameter( String parameterName ) {
        return delegate.hasParameter(parameterName);
    }

    @Override
    public boolean hasParameter( int parameterIndex ) {
        return delegate.hasParameter(parameterIndex);
    }

    @Override
    public <T> T getReturnValue() {
        return delegate.getReturnValue();
    }

    @Override
    public PGAnonymousSqlCall execute() throws CallException {
        delegate.execute();
        return this;
    }

    @Override
    public void close() {
        delegate.close();
    }
}
