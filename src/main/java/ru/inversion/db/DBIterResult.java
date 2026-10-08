package ru.inversion.db;

import ru.inversion.tc.TaskContext;

import java.util.Iterator;

public class DBIterResult<R,V> extends AbstractDBResult<R, Iterator<V>,DBIterResult > {
    /**
     * @param tc
     */
    protected DBIterResult( TaskContext tc ) {
        super(tc);
    }

    /** */
    public Iterator<V> execute( ) { return null; }
}
