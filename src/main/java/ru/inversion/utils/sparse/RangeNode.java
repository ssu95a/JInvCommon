package ru.inversion.utils.sparse;

import java.util.*;

public class RangeNode<V> {

    private RangeNode<V> left, right;
    final private IRange range;

    /** */
    public RangeNode( IRange range ) {
        this.range = range;
    }

    /** */
    public IRange range()
    { return range; }

    /** */
    public void setLeft( RangeNode<V>l )
    {
        this.left = l;
    }
    /** */
    public void setRight( RangeNode<V> r )
    {
        this.right = r;
    }

    public RangeNode<V> getLeft() {
        return left;
    }
    public RangeNode<V> getRight() {
        return right;
    }

}
