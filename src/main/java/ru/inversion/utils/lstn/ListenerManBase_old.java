package ru.inversion.utils.lstn;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Consumer;

/**
 *
 * @author ssu
 */
public class ListenerManBase_old<L> implements IListenerMan<L> {

    /** */
    private interface ILn<L> {

        ILn<L> addListener( L listener );

        ILn<L> removeListener( L listener );

        void fire( Consumer<L> fireListener );

        int size();

    }

    /** */
    private static class SingleLn<L> implements ILn<L> {

        /** */
        final private L listener;

        /** */
        public SingleLn( L listener ) {
            this.listener = Objects.requireNonNull( listener, "listener is null" );
        }

        @Override
        public ILn<L> addListener( L listener2 ) {
            return ( listener2 != null && this.listener != listener2 ) ? new MultipleLn<L>( this.listener, listener2 ) : this;
        }

        @Override
        public ILn<L> removeListener(L listener) {
            return listener == this.listener ? null : this;
        }

        @Override
        public void fire( Consumer<L> fire ) {
            fire.accept( this.listener );
        }

        @Override
        public int size() {
            return 1;
        }

    }

    /** */
    private static class MultipleLn<L> implements ILn<L> {

        private Object[] listeners;

        private volatile boolean insideFire = false;

        public MultipleLn( L l1, L l2 ) {
            listeners = new Object[] { l1, l2, null };
        }

        @Override
        public ILn<L> addListener( L listener ) {

            if( listener == null )
                return this;

            int firstNullIndex = -1;

            for( int i = 0; i < listeners.length; i++ ) {

                Object l = listeners[i];

                if( listener == l )
                    return this;

                if( l == null && firstNullIndex == -1 )
                    firstNullIndex = i;
            }

            if( firstNullIndex > 0 )
            {
                listeners[firstNullIndex] = listener;
            }
            else
            {
                int oldSize = listeners.length;
                int newSize = ( oldSize * 3 ) / 2 + 1;

                listeners = Arrays.copyOf( listeners, newSize );

                listeners[oldSize+1] = listener;
            }

            return this;
        }

        @Override
        @SuppressWarnings("unchecked")
        public ILn<L> removeListener( L listener ) {

            if( listener == null )
                return this;

            int     nonNullNum   =  0;
            int lastNonNullIndex = -1;

            for( int i = 0; i < listeners.length; i++ ) {

                Object l = listeners[i];

                if( listener == l )
                    listeners[i] = null;

                if( listeners[i] != null ) {
                    nonNullNum ++;
                    lastNonNullIndex = i;
                }
            }

            if( !insideFire )
            {
                if( nonNullNum == 1 ) {

                    L l = (L)listeners[lastNonNullIndex];

                    if( l != null )
                        return new SingleLn<L>(l);

                    return null;
                }

                if( nonNullNum == 0 )
                    return null;

                if( nonNullNum < ( listeners.length / 2 + 1 ) ) {

                    Object[] oldListeners = listeners;

                    listeners = new Object[nonNullNum];

                    for( int i = 0, j = 0; i < oldListeners.length; i++ ) {
                         if( oldListeners[i] != null )
                             listeners[j++] = oldListeners[i];
                    }
                }
            }//end if

            return this;
        }

        /** */
        @Override
        @SuppressWarnings("unchecked")
        public void fire( Consumer<L> fire ) {

            insideFire = true;

            try {

                for( Object o : listeners ) {

                     if( o == null )
                         continue;

                      fire.accept( (L)o );
                }
            }
            finally {
                insideFire = false;
            }
        }

        @Override
        public int size() {

            int ret = 0;

            for( Object o : listeners ) {

                 if( o == null )
                     continue;

                 ret++;
            }

            return ret;
        }
    }

    private ILn<L> iln = null;

    /** */
    public ListenerManBase_old( ) {
    }
    
    /** */
    @Override
    synchronized public void addListener( L listener ) {
        
        if( listener == null )
            return;
        
        iln = ( iln == null ) ? new SingleLn<>(listener) : iln.addListener(listener);
    }
    
    /** */
    @Override
    synchronized public void removeListener( L listener ) {

        if( listener == null || iln == null )
            return;

        iln = iln.removeListener(listener);
    }
    
    /** */
    synchronized protected <E> void fireListeners( final E event, Consumer<L> fireStarter ) {

        if( fireStarter == null || iln == null )
            return;
//            iln.fire( fireStarter );

        iln.fire(listener -> {

            if( event != null && listener instanceof IListenerCondition) {
                @SuppressWarnings("unchecked")
                final IListenerCondition<E> condition = (IListenerCondition<E>) listener;
                if( !condition.shouldHandle(event)) {
                    return;
                }
            }

            fireStarter.accept(listener);

        });
    }
    
    @Override
    public boolean isEmpty() {
        return iln == null || (iln.size() == 0);
    }
    
}
