package ru.inversion.utils.sparse;

/** */
public interface IRange<V> extends Comparable<IRange> {

    /** */
    int lower();

    /** */
    int upper();

    /** */
    default int contains( int v )
    {
        if( v > upper() )
            return 1;
        if( v < lower() )
            return -1;

        return 0;
    }

    /** */
    boolean containsAll( Iterable<Integer> list );

    /** */
    default boolean isEmpty()
    { return lower() == upper(); }

    @Override
    default int compareTo( IRange r )
    {
        if( r.lower() == lower() && r.upper() == upper() )
            return 0;

        if( r.upper() < lower() )
            return 1;

        if( upper() < r.lower() )
            return -1;

        throw new IllegalStateException("compareTo");
    }

    /** */
    default boolean isIntersect( IRange r )
    {
        if( r == null )
            return false;
        if( this == r )
            return false;

        if (
            ( r.lower() > lower()  && r.lower() < r.upper() )
            ||
            ( r.upper() > lower()  && upper() > r.upper() )
        )
            return true;

        return false;
    }

    /** */
    V get( int index );

}
