package ru.inversion.utils.bits;

public interface IBits {

    /** */
    boolean get( int index );

    /** */
    void set( int index, boolean v );

    /** */
    default void set( int index ) { set(index,true); }

    /** */
    default void clear( int index ) { set(index,false); }

    /** */
    int getSize( );

    /** */
    void clear();

    Number asNumber();
}
