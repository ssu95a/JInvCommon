package ru.inversion.utils.lstn;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Consumer;

/**
 *
 * @author ssu
 */
public class ListenerManBase<L> implements IListenerMan<L> {

    private static final Object[] EMPTY =new Object[0];

    private volatile Object[] listeners = EMPTY;

    /** */        
    public ListenerManBase( ) {
    }

    @Override
    public synchronized void addListener( L listener ) {

        if( listener == null )
            return;

        Object[] current = listeners;

        for (Object value : current) {
            if( value == listener )
                return;
        }

        Object[] next = Arrays.copyOf( current, current.length + 1 );

        next[current.length] = listener;

        listeners = next;
    }

    @Override
    public synchronized void removeListener( L listener )
    {
        if( listener == null || isEmpty() )
            return;

        Object[] current = listeners;

        for( int i = 0; i < current.length; i++ )
        {
            if( current[i] != listener )
                continue;

            Object[] next = new Object[current.length - 1];

            System.arraycopy( current, 0, next, 0, i );
            System.arraycopy( current, i + 1, next, i, current.length - i - 1 );

            listeners = next;

            return;
        }
    }

    /** */
    @Override
    public boolean isEmpty() {
        return listeners.length == 0;
    }


    /** */
    @SuppressWarnings("unchecked")
    protected <E> void fireListeners( E event, Consumer<L> fireStarter )
    {
        if( fireStarter == null )
            return;

        final Object[] snapshot = listeners;

        for( Object value : snapshot )
        {
            L listener = (L) value;

            if( event != null && listener instanceof IListenerCondition<?> )
            {
                IListenerCondition<E> condition = (IListenerCondition<E>) listener;

                if (!condition.shouldHandle(event))
                    continue;
            }

            fireStarter.accept(listener);
        }
    }

}
