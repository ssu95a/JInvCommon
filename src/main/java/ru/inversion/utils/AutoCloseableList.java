package ru.inversion.utils;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author ssu
 */
public final class AutoCloseableList implements AutoCloseable {

    private final List<AutoCloseable> list = new ArrayList<>();
    private final boolean suppressException;

    public AutoCloseableList(boolean suppressException) {
        this.suppressException = suppressException;
    }

    /** */
    public AutoCloseableList() {
        this(true);
    }

    public AutoCloseableList add( AutoCloseable... items )
    {
        if( items == null )
            return this;

        for( AutoCloseable a : items ) {
            if( a != null && !list.contains(a))
                list.add(a);
        }
        return this;
    }

    @Override
    public void close( ) {

        Exception first = null;

        for( int i = list.size() - 1; i >= 0; i-- )
        {
            AutoCloseable a = list.get(i);
            try {
                a.close();
            } catch (Exception e)
            {
                if( first == null)
                    first = e;
                else
                    first.addSuppressed(e);
            }
        }

        list.clear();

        if( first != null )
            if( !suppressException )
                throw new RuntimeException( Tags.PRODUCT_LABEL + "AutoCloseableList make closed with exception", first );
            else
                first.printStackTrace();
    }
}
