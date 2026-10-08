package ru.inversion.utils.sparse;

/** */
public class RangeTree<V> {

    protected RangeNode<V> root = null;

    /** */
    public V get( int index )
    {

        RangeNode<V> p = root;
        while (p != null) {
            int cmp = p.range().contains(index);
            if (cmp < 0)
                p = p.getLeft();
            else if (cmp > 0)
                p = p.getRight();
            else
                ;//return p.range().get(index);
        }
        return null;
    }

    /**
    public V put( IRange range, V data) {
        return putHelper( root, range, data );
    }
    */
    /*
    private V putHelper( RangeNode<V> node, IRange range, V value)
    {
        int cmp = node.range().compareTo(range);
        if( cmp == 0 )
            return node.setValue(value);
        if( cmp < 0 ) {
            Node<K,V> l = node.getLeft();
            if( l == null ) {
                fillNode(node);
                putHelper( node, key, value );
            }
            else
                return putHelper(l, key, value);

        }
        else
        {
            Node<K,V> r = node.getRight();
            if( r == null ) {
                fillNode(node);
                putHelper( node, key, value );
            }
            else
                return putHelper( r, key, value);
        }
        return null;
    }
    */
}
