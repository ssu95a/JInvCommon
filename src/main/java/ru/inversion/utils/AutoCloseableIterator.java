package ru.inversion.utils;

import java.util.Iterator;
import java.util.function.Consumer;

/** */
public interface AutoCloseableIterator<T> extends Iterator<T>, AutoCloseable {
    /** */
    public static <R> AutoCloseableIterator<R> of( final Iterator<R> iter, Consumer<Iterator<R>> onClose ) {

        if( iter == null )
            return null;

        return new AutoCloseableIterator< R >() {
            @Override
            public void close() {
                if( onClose != null )
                    onClose.accept( iter );
            }
            @Override
            public boolean hasNext() {
                return iter.hasNext();
            }
            @Override
            public R next() {
                return iter.next();
            }
        };
    }
}