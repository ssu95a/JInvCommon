package ru.inversion.datacall;

import ru.inversion.dataset.IParameters;
import ru.inversion.tc.TaskContext;

import java.sql.PreparedStatement;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

public class PGAnonymousSqlCall implements IDataCall{

    final private TaskContext tc;
    final private CallStatement callStatement;
    final private IParameters callBackParameters;
    final private String name;

    /** */
    public PGAnonymousSqlCall( TaskContext tc, CallStatement callStatement, IParameters callBackParameters, String name ) {
        this.tc = tc;
        this.callStatement      = callStatement;
        this.callBackParameters = callBackParameters;
        this.name               = name;
    }

//    /** */
//    private String createStatement_Set( ) throws CallException {
//
//
//
//    }

    @Override
    public String getName() throws CallException {
        return name;
    }
    @Override
    public void setName( String name ) throws CallException { }

    @Override
    public void setProperty( String propertyName, Object value ) throws CallException {
    }
    @Override
    public Object getProperty( String propertyName ) throws CallException {
        return null;
    }

    @Override
    public < V > void setValueCallback( Object id, Supplier< V > supplier ) throws CallException {
    }
    @Override
    public < V > void setValueListener( Object id, BiConsumer< Object, V > consumer ) throws CallException {
    }

    @Override
    public void setValueListener( BiConsumer< Object, Object > consumer ) throws CallException {
    }
    @Override
    public void addDataCallListener( IDataCallListener l ) throws CallException {
    }

    @Override
    public void removeDataCallListener( IDataCallListener l ) throws CallException {
    }

    @Override
    public ParameterMetadata getParameterMetadata( Object id ) throws CallException {
        return null;
    }

    @Override
    public Iterable< ParameterMetadata<?> > getParameterIter() {
        return null;
    }

    @Override
    public Object getParameter( String parameterName ) {
        return null;
    }

    @Override
    public Object getParameter( int parameterIndex ) {
        return null;
    }

    /** */
    @Override
    public < C extends IDataCall > C execute() throws CallException {
        return null;
    }

    /** */
    @Override
    public void close() {
    }
}
