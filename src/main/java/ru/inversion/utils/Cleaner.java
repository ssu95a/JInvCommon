package ru.inversion.utils;

import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


/** */
public class Cleaner {

    /** */
    final static private ThreadFactory g_thf = new ThreadFactory() {
        @Override
        public Thread newThread( Runnable r ) {
            final Thread th = new Thread( r, "ru.inversion.utils.Cleaner");
            th.setDaemon(true);
            return th;
        }
    };

    private final ReferenceQueue<Object> queue = new ReferenceQueue<>();
    private final Set<Node> activeRef = ConcurrentHashMap.newKeySet() ;

    private final Lock lock = new ReentrantLock();
    private volatile boolean threadRunning = false;

    /** */
    public void add( Object o, AutoCloseable ac )
    {
        if( o == null || ac == null ) {
            return;
        }

        final Node node = new Node( o, ac );
        activeRef.add(node);

        lock.lock();

        try {

            if( !threadRunning )
                 threadRunning = startThread();

        } finally {
            lock.unlock();
        }
    }

    /** */
    public void add( AutoCloseable o )
    {
        if( o == null )
            return;
        add( o, o );
    }

    /** */
    private void runnable()
    {
        try {

            Thread.currentThread().setContextClassLoader(null);
            Thread.currentThread().setUncaughtExceptionHandler(null);

            while(true)
            {
                try {

                    final Node ref = (Node) queue.remove(1000);

                    if( ref == null )
                    {
                        lock.lock();

                        try {

                            if( activeRef.isEmpty() ) {
                                threadRunning = false;
                                break; // thread exit
                            }
                        }
                        finally {
                            lock.unlock();
                        }

                        continue;
                    }

                    activeRef.remove(ref);

                    try {
                        ref.close();
                    } catch (Throwable e) {

                        if (e instanceof InterruptedException) {
                            throw e;
                        }

                        e.printStackTrace();
                    }
                }
                catch( InterruptedException e ) {
                    if(activeRef.isEmpty() ) {
//                        LOGGER.log(
//                                Level.FINE,
//                                "Cleanup queue is empty, and got interrupt, will terminate the cleanup thread"
//                        );
                    }
                    //LOGGER.log(Level.FINE, "Ignoring interrupt since the cleanup queue is non-empty");

                    Thread.currentThread().interrupt();
                }
            }//while
        }
        catch (Throwable e) {

            lock.lock();
            try {
                threadRunning = false;
            } finally {
                lock.unlock();
            }            // Ignore exceptions from the cleanup action
            //LOGGER.log(Level.WARNING, "Unexpected exception in cleaner thread main loop", e);
        }
    }

    /** */
    private boolean startThread()
    {
        final Thread th = g_thf.newThread(this::runnable);

        if( th == null )
            return false;

        th.start();

        return true;
    }

    /** */
    private class Node extends PhantomReference<Object> implements AutoCloseable {

        final private AutoCloseable closer;
        /** */
        public Node( Object referent, AutoCloseable closer ) {
            super(referent, queue );
            this.closer = closer;
        }
        /** */
        @Override
        public void close( ) throws Exception {
            try {
                closer.close();
            }
            finally {
                activeRef.remove(this);
            }
        }
    }

    //static

    final static private Cleaner instance = new Cleaner();

    public static AutoCloseableList closeableList( boolean doRegister )
    {
        return new AutoCloseableList();
    }

    /** */
    public static AutoCloseableList closeableList( Object ... items )
    {
        return new AutoCloseableList();
    }

    /** */
    public static void closeLater( Object o, AutoCloseable ac )
    {
        if( o != null )
            instance.add( o, ac );
    }
}
