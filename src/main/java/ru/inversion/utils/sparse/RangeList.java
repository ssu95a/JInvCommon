package ru.inversion.utils.sparse;

import java.util.List;

public class RangeList<V> implements IRange<V> {

    final private IRange parent;
    final private List<V> data;

    private int lower;

    /** */
    public RangeList( IRange parent, int lower, List<V> data ) {
        this.parent = parent;
        this.data   = data;
    }

    /** */
    public RangeList( int lower, List< V > data ) {
        this.parent = null;
        this.data   = data;
        this.lower  = lower;
    }

    @Override
    public int lower() {
        return parent == null ? lower : parent.upper() + lower;
    }

    @Override
    public int upper() {
        return lower + data.size() - 1;
    }

    @Override
    public boolean containsAll( Iterable< Integer > list ) {
        return false;
    }

    @Override
    public V get( int index ) {
        return null;
    }
}
