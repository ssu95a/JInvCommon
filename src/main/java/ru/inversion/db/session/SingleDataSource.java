package ru.inversion.db.session;

import ru.inversion.utils.Tags;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

/**
 * DataSource с одним физическим JDBC Connection.
 * <p>
 * Каждый getConnection() возвращает lease-proxy.
 * close() этого proxy освобождает lease, но не закрывает physical connection.
 * <p>
 * Physical connection закрывается:
 * - по idle timeout, когда нет активных lease;
 * - при close() самого SingleDataSource.
 * <p>
 * Это НЕ connection pool и не предназначено для параллельной работы
 * нескольких JDBC операций.
 */
public final class SingleDataSource implements DataSource, AutoCloseable
{
    private static final long NOT_USED = 0L;

    private static final int DEFAULT_IDLE_SECONDS = 3;

    /*
     * Lease не должен менять transaction/session state общей physical connection.
     */
    private static final Set<String> FORBIDDEN_METHODS =
            Collections.unmodifiableSet(
                    new HashSet<>(
                            Arrays.asList(
                                    "setAutoCommit",
                                    "commit",
                                    "rollback",
                                    "setSavepoint",
                                    "releaseSavepoint",
                                    "setTransactionIsolation",
                                    "setReadOnly",
                                    "setCatalog",
                                    "setSchema",
                                    "setHoldability",
                                    "setClientInfo",
                                    "setNetworkTimeout",
                                    "setTypeMap",
                                    "abort"
                            )
                    )
            );

    private final SingleConnection singleConnection;

    private final ScheduledExecutorService scheduler;

    private final IDataSourceListener listener;

    private volatile boolean closed;


    /** */
    public SingleDataSource()
    {
        this(null, DEFAULT_IDLE_SECONDS);
    }


    /** */
    public SingleDataSource( IDataSourceListener listener )
    {
        this(listener, DEFAULT_IDLE_SECONDS);
    }


    /** */
    public SingleDataSource(
            IDataSourceListener listener,
            int idleSeconds
    )
    {
        if( idleSeconds < 0 )
            throw new IllegalArgumentException("idleSeconds < 0");

        this.listener = listener;

        singleConnection = new SingleConnection(idleSeconds);

        scheduler =
                Executors.newSingleThreadScheduledExecutor(
                        runnable -> {
                            Thread thread =
                                    new Thread(
                                            runnable,
                                            "SingleDataSource-IdleChecker"
                                    );

                            thread.setDaemon(true);

                            return thread;
                        }
                );

        scheduler.scheduleAtFixedRate(
                singleConnection::checkIdle,
                1,
                1,
                TimeUnit.SECONDS
        );
    }


    /**
     * Lease proxy.
     */
    private static final class ProxyHandler implements InvocationHandler
    {
        private SingleConnection singleConnection;

        private boolean closed;


        /** */
        private ProxyHandler( SingleConnection singleConnection )
        {
            this.singleConnection = singleConnection;
        }


        /** */
        @Override
        public Object invoke(
                Object proxy,
                Method method,
                Object[] args
        )
                throws Throwable
        {
            final String name = method.getName();

            if( Object.class.equals(method.getDeclaringClass()) )
            {
                switch( name )
                {
                    case "equals":
                        return args != null
                                && args.length == 1
                                && proxy == args[0];

                    case "hashCode":
                        return System.identityHashCode(proxy);

                    case "toString":
                        return "SingleDataSourceConnection(closed=" + closed + ")";

                    default:
                        return method.invoke(this, args);
                }
            }

            if( "close".equals(name)
                    && method.getParameterTypes().length == 0 )
            {
                proxyClose();
                return null;
            }

            if( "isClosed".equals(name)
                    && method.getParameterTypes().length == 0 )
            {
                return closed;
            }

            if( closed )
                throw new SQLException("Connection is closed");

            SingleConnection sc = singleConnection;

            if( sc == null )
                throw new SQLException("Connection is closed");

            /*
             * unwrap(Connection.class) не должен выпускать physical connection.
             */
            if( "unwrap".equals(name)
                    && args != null
                    && args.length == 1 )
            {
                Class<?> clazz = (Class<?>) args[0];

                if( clazz.isInstance(proxy) )
                    return clazz.cast(proxy);
            }

            if( "isWrapperFor".equals(name)
                    && args != null
                    && args.length == 1 )
            {
                Class<?> clazz = (Class<?>) args[0];

                if( clazz.isInstance(proxy) )
                    return true;
            }

            if( FORBIDDEN_METHODS.contains(name) )
            {
                throw new SQLFeatureNotSupportedException(
                        "Operation not allowed on SingleDataSource: " + name
                );
            }

            return sc.invokeOnReal(method, args);
        }


        /** */
        private void proxyClose()
        {
            SingleConnection sc;

            synchronized( this )
            {
                if( closed )
                    return;

                closed = true;

                sc = singleConnection;
                singleConnection = null;
            }

            if( sc != null )
                sc.releaseProxy();
        }
    }


    /**
     * Owner физического Connection.
     *
     * Все поля, кроме idleSeconds, защищены lock.
     */
    private final class SingleConnection implements AutoCloseable
    {
        private final int idleSeconds;

        private final ReentrantLock lock = new ReentrantLock();

        private Connection connection;

        private int activeProxies;

        private long lastReleaseStamp = NOT_USED;


        /** */
        private SingleConnection( int idleSeconds )
        {
            this.idleSeconds = idleSeconds;
        }


        /**
         * Создание lease + учёт activeProxies выполняются атомарно
         * относительно idle checker.
         */
        private Connection newProxy()
                throws SQLException
        {
            lock.lock();

            try
            {
                checkDataSourceOpen();

                ensureRealConnection();

                Connection proxy =
                        (Connection) Proxy.newProxyInstance(
                                Connection.class.getClassLoader(),
                                new Class<?>[] { Connection.class },
                                new ProxyHandler(this)
                        );

                activeProxies++;

                /*
                 * Пока есть lease, idle timestamp значения не имеет.
                 */
                lastReleaseStamp = NOT_USED;

                return proxy;
            }
            finally
            {
                lock.unlock();
            }
        }


        /** */
        private void releaseProxy()
        {
            lock.lock();

            try
            {
                if( activeProxies <= 0 )
                {
                    System.err.println(
                            "Warn: bug in 'activeProxies' logic mechanism"
                    );

                    return;
                }

                activeProxies--;

                if( activeProxies == 0 )
                    lastReleaseStamp = System.currentTimeMillis();
            }
            finally
            {
                lock.unlock();
            }
        }


        /**
         * Idle checker.
         *
         * Проверка activeProxies и закрытие physical connection
         * происходят под одним lock, поэтому новый lease не может
         * появиться между проверкой и close().
         */
        private void checkIdle()
        {
            if( idleSeconds <= 0 )
                return;

            lock.lock();

            try
            {
                if( closed )
                    return;

                if( activeProxies != 0 )
                    return;

                if( connection == null )
                    return;

                if( lastReleaseStamp == NOT_USED )
                    return;

                long idleMillis =
                        System.currentTimeMillis()
                                - lastReleaseStamp;

                if( idleMillis
                        < TimeUnit.SECONDS.toMillis(idleSeconds) )
                {
                    return;
                }

                closeRealConnection();
            }
            catch( Throwable throwable )
            {
                throwable.printStackTrace();
            }
            finally
            {
                lock.unlock();
            }
        }


        /**
         * lock уже должен быть захвачен.
         */
        private void ensureRealConnection()
                throws SQLException
        {
            if( connection != null
                    && !connection.isClosed() )
            {
                return;
            }

            Connection created =
                    SessionEnvironment
                            .getInstance()
                            .getConnectionProvider()
                            .getConnection();

            try
            {
                if( listener != null )
                    listener.afterCreateConnection(created);

                connection = created;
            }
            catch( Throwable throwable )
            {
                try
                {
                    created.close();
                }
                catch( Throwable closeError )
                {
                    throwable.addSuppressed(closeError);
                }

                if( throwable instanceof SQLException )
                    throw (SQLException) throwable;

                if( throwable instanceof RuntimeException )
                    throw (RuntimeException) throwable;

                if( throwable instanceof Error )
                    throw (Error) throwable;

                throw new SQLException(
                        "Error initializing physical connection",
                        throwable
                );
            }
        }


        /**
         * Выполнение JDBC вызова на physical connection.
         */
        private Object invokeOnReal(
                Method method,
                Object[] args
        )
                throws Throwable
        {
            lock.lock();

            try
            {
                if( connection == null
                        || connection.isClosed() )
                {
                    throw new SQLException(
                            Tags.PRODUCT_LABEL
                                    + "SingleDataSource: Error accessing "
                                    + "SingleConnection. Connection is invalid. "
                                    + "method ='"
                                    + method.getName()
                                    + "'"
                    );
                }

                try
                {
                    return method.invoke(connection, args);
                }
                catch( InvocationTargetException ex )
                {
                    throw ex.getTargetException();
                }
            }
            finally
            {
                lock.unlock();
            }
        }


        /**
         * lock уже должен быть захвачен.
         */
        private void closeRealConnection()
                throws SQLException
        {
            Connection current = connection;

            connection = null;
            lastReleaseStamp = NOT_USED;

            if( current == null )
                return;

            SQLException failure = null;

            try
            {
                if( !current.isClosed()
                        && listener != null )
                {
                    try
                    {
                        listener.beforeCloseConnection(current);
                    }
                    catch( SQLException ex )
                    {
                        failure = ex;
                    }
                }
            }
            finally
            {
                try
                {
                    if( !current.isClosed() )
                        current.close();
                }
                catch( SQLException ex )
                {
                    if( failure == null )
                        failure = ex;
                    else
                        failure.addSuppressed(ex);
                }
            }

            if( failure != null )
                throw failure;
        }


        /** */
        @Override
        public void close()
        {
            lock.lock();

            try
            {
                closeRealConnection();
            }
            catch( SQLException ex )
            {
                throw new RuntimeException(
                        Tags.PRODUCT_LABEL
                                + "Error on close connection",
                        ex
                );
            }
            finally
            {
                lock.unlock();
            }
        }
    }


    /** */
    @Override
    public Connection getConnection()
            throws SQLException
    {
        checkDataSourceOpen();

        return singleConnection.newProxy();
    }


    /** */
    @Override
    public Connection getConnection(
            String username,
            String password
    )
            throws SQLException
    {
        throw new SQLFeatureNotSupportedException(
                "Use getConnection() without credentials"
        );
    }


    /** */
    private void checkDataSourceOpen()
            throws SQLException
    {
        if( closed )
            throw new SQLException("SingleDataSource is closed");
    }


    /** */
    @Override
    public <T> T unwrap( Class<T> iface )
            throws SQLException
    {
        if( iface.isInstance(this) )
            return iface.cast(this);

        throw new SQLException(
                "DataSource of type ["
                        + getClass().getName()
                        + "] cannot be unwrapped as ["
                        + iface.getName()
                        + "]"
        );
    }


    /** */
    @Override
    public boolean isWrapperFor( Class<?> iface )
    {
        return iface.isInstance(this);
    }


    /** */
    @Override
    public PrintWriter getLogWriter()
            throws SQLException
    {
        throw new SQLFeatureNotSupportedException("getLogWriter");
    }


    /** */
    @Override
    public void setLogWriter( PrintWriter out )
            throws SQLException
    {
        throw new SQLFeatureNotSupportedException("setLogWriter");
    }


    /** */
    @Override
    public void setLoginTimeout( int seconds )
            throws SQLException
    {
        throw new SQLFeatureNotSupportedException("setLoginTimeout");
    }


    /** */
    @Override
    public int getLoginTimeout()
            throws SQLException
    {
        throw new SQLFeatureNotSupportedException("getLoginTimeout");
    }


    /** */
    @Override
    public Logger getParentLogger()
            throws SQLFeatureNotSupportedException
    {
        throw new SQLFeatureNotSupportedException("getParentLogger");
    }


    /** */
    @Override
    public void close()
    {
        if( closed )
            return;

        closed = true;

        scheduler.shutdownNow();

        try
        {
            if( !scheduler.awaitTermination(
                    3,
                    TimeUnit.SECONDS
            ))
            {
                System.err.println(
                        "SingleDataSource scheduler did not terminate"
                );
            }
        }
        catch( InterruptedException ex )
        {
            Thread.currentThread().interrupt();
        }

        singleConnection.close();
    }
}
