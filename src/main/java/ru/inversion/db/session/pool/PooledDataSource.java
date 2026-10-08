package ru.inversion.db.session.pool;

import ru.inversion.db.session.IDataSourceListener;
import ru.inversion.db.session.SessionEnvironment;


import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;
import java.util.*;
import java.util.concurrent.locks.*;

public class PooledDataSource implements DataSource, AutoCloseable {

    private enum PoolState { RUNNING, CLOSED }

    private final Lock      poolLock = new ReentrantLock(true);
    private final Condition connectionAvailable = poolLock.newCondition();

    private final Set<PooledConnection> idleConnections   = new HashSet<>();
    private final Set<PooledConnection> activeConnections = new HashSet<>();

    private PoolState state = PoolState.RUNNING;

    private final int maxActiveConnection;
    private final int maxIdleConnections;
    private final int poolTimeToWait;
    private final int numberOfAttempts;

    private final boolean waitWithCoef;

    private final boolean createRealIfExceeded;

    private final IDataSourceListener listener;

    /** */
    public PooledDataSource(IPoolSettings settings, IDataSourceListener listener) {

        this.maxActiveConnection = settings.getPoolMaxActiveConnections() == 0 ? Integer.MAX_VALUE : settings.getPoolMaxActiveConnections();
        this.maxIdleConnections  = settings.getPoolMaxIdleConnections() == 0 ? Integer.MAX_VALUE : settings.getPoolMaxIdleConnections();
        this.poolTimeToWait      = settings.getPoolTimeToWait();
        this.waitWithCoef        = settings.isPoolWaitWithCoef();
        this.numberOfAttempts    = settings.getPoolNumberOfAttempts();
        this.createRealIfExceeded= settings.isCreateRealIfExceeded();

        this.listener            = listener;
    }

    /** Получение соединения */
    @Override
    public Connection getConnection( ) throws SQLException {

        final PooledConnection pc = popConnection();

        if( pc != null )
            return pc.getProxyConnection( );

        if( !createRealIfExceeded )
             throw new SQLException("Pool exhausted");

        final Connection c = SessionEnvironment.getInstance().getConnectionProvider().getConnection();

        if( listener != null )
            listener.afterCreateConnection(c);

        return c;
    }

    /**
     * Выдает соединение из ожидающих, должен вызываться под poolLock
     */
    private PooledConnection getIdleConnection( ) {

        PooledConnection pc = null;

        if( !idleConnections.isEmpty() )
        {
            pc = idleConnections.iterator().next();

            idleConnections.remove(pc);
            activeConnections.add (pc);
        }

        return pc;
    }

    /** */
    private PooledConnection popConnection() throws SQLException {

        poolLock.lock();

        try {

            if( state == PoolState.CLOSED )
                throw new SQLException("Pool is closed");

            // idle
            PooledConnection pc = getIdleConnection( );

            if( pc != null )
                return pc;

            // create pooled
            if( activeConnections.size() < maxActiveConnection )
            {
                pc = create();
                activeConnections.add(pc);
                return pc;
            }

            // 3. wait
            for( int attempt = 1; attempt <= numberOfAttempts; attempt++ )
            {
                long waitTime = waitWithCoef ? (long) poolTimeToWait * attempt : poolTimeToWait;

                boolean signaled = connectionAvailable.await( waitTime, TimeUnit.MILLISECONDS );

                if( state == PoolState.CLOSED )
                    throw new SQLException("Pool is closed");

                pc = getIdleConnection( );

                if( pc != null )
                    return pc;
            }

            // exhausted
            return null;
        }
        catch( InterruptedException e )
        {
            Thread.currentThread().interrupt();
            throw new SQLException("Interrupted while waiting", e );
        }
        finally
        {
            poolLock.unlock();
        }
    }


    /** Возврат соединения в пул */
    void pushConnection( PooledConnection pooledConnection ) throws SQLException {

        if( pooledConnection == null )
            return;

        poolLock.lock();

        try {

            // Если соединение detached — закрываем реально
            if( pooledConnection.isDetached() )
            {
                doCloseConnection(pooledConnection);
                return;
            }

            if( !activeConnections.remove( pooledConnection ) )
                return;

            if( !pooledConnection.isValid() )
                return;

            Connection c = pooledConnection.getRealConnection();
            pooledConnection.invalidate();

            try {

                if( c != null && !c.isClosed() )
                    c.rollback();
                else
                    return;

            } catch( SQLException ignored ) {
                return;
            }

            if( state == PoolState.RUNNING && idleConnections.size() < maxIdleConnections ) {
                idleConnections.add( new PooledConnection(c, this));
                // wake up one waiting thread
                connectionAvailable.signal();
            }
            else
            {
                if( listener != null )
                    listener.beforeCloseConnection(c);

                try { c.close(); }  catch (SQLException ignored) {}
            }
        } finally {
            poolLock.unlock();
        }
    }


    /** Создание нового соединения */
    private PooledConnection create( ) throws SQLException
    {
        Connection c = SessionEnvironment.getInstance().getConnectionProvider().getConnection();

        if( listener != null )
            listener.afterCreateConnection(c);

        return new PooledConnection(c, this);
    }


    /** Закрытие пула */
    @Override
    public void close()
    {
        poolLock.lock();

        try {

            state = PoolState.CLOSED;

            connectionAvailable.signalAll( ); // разбудить ожидающие потоки

            idleConnections.forEach( this::doCloseConnection );
            idleConnections.clear( );

            // Активные соединения остаются, но больше не управляются пулом
            activeConnections.forEach( PooledConnection::detach );

        } finally {
            poolLock.unlock();
        }
    }

    /** закрытие */
    private void doCloseConnection(PooledConnection pc)
    {
        if( pc != null && pc.isValid() )
        {
            Connection c = pc.getRealConnection();
            pc.invalidate();
            try {

                if( c != null && !c.isClosed() )
                {
                    if( listener != null )
                        listener.beforeCloseConnection(c);

                    c.rollback();
                    c.close();
                }
            } catch (Exception ignored) {}
        }
    }

    /** методы DataSource */
    @Override public Connection getConnection(String username, String password) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public PrintWriter getLogWriter() throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void setLogWriter(PrintWriter out) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void setLoginTimeout(int seconds) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public int getLoginTimeout() throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public Logger getParentLogger() throws SQLFeatureNotSupportedException { throw new SQLFeatureNotSupportedException(); }
    @Override public <T> T unwrap(Class<T> iface) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public boolean isWrapperFor(Class<?> iface) throws SQLException { return false; }
}
