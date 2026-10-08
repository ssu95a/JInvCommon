package ru.inversion.db.session.pool;
import java.lang.reflect.*;
import java.sql.Connection;
import java.sql.SQLException;

/** */
class PooledConnection implements InvocationHandler {

    private Connection connection;
    final private Connection proxyConnection;

    private volatile PooledDataSource pool;

    public PooledConnection( Connection connection, PooledDataSource pool ) {
        this.connection = connection;
        this.pool       = pool;
        this.proxyConnection = (Connection) Proxy.newProxyInstance( Connection.class.getClassLoader(), new Class<?>[]{Connection.class}, this );
    }

    public Connection getRealConnection() {
        return connection;
    }

    public Connection getProxyConnection() {
        return proxyConnection;
    }

    public boolean isValid() {
        return connection != null;
    }

    public boolean isDetached() {
        return pool == null;
    }

    /** */
    public void detach() {
        pool = null;
    }

    /** */
    public void invalidate() {
        connection = null;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable
    {
        String methodName = method.getName();

        if ("close".equals( methodName ))
        {
            PooledDataSource p = pool;

            if( p != null )
            {
                p.pushConnection(this);
            }
            else
            {
                doCloseConnection();
            }

            return null;
        }

        if( !isValid() )
            throw new SQLException("Connection is closed or detached from pool");


        if( method.getDeclaringClass() == Object.class)
            return method.invoke( this, args );

        try {
            return method.invoke(connection, args);
        } catch (InvocationTargetException ite ) {
            throw ite.getTargetException();
        }
    }

    private void doCloseConnection() {
        if (connection != null) {
            try {
                connection.rollback();
                connection.close();
            } catch (Exception ignored) {}
            invalidate();
        }
    }
}
