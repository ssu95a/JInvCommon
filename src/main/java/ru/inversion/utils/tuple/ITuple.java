package ru.inversion.utils.tuple;

/** */
public interface ITuple extends Iterable<Object>
{
    int getSize();

    <T> T get( int n );
    <T> void set( int n, T value );
    /** */
    default <T> T get0() {
        return get(0);
    }
    <T1> T1 get1();
    <T2> T2 get2();
    <T3> T3 get3();
    <T> T get4();
    <T> T get5();
    <T> T get6();
    <T> T get7();
    <T> T get8();
    <T> T get9();

    default Object[] toArray()
    {
        Object[] r = new Object[getSize()];
        for( int i = 0; i < getSize(); i++ )
             r[i] = get(i);
        return r;
    }

    <T> int indexOf( T value );
}
