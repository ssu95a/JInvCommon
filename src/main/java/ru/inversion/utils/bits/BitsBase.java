package ru.inversion.utils.bits;

import ru.inversion.utils.S;

import java.util.stream.IntStream;

public abstract class BitsBase extends Number implements IBits {

    abstract protected boolean doGet( int index );
    abstract protected void doSet( int index, boolean v );

    public BitsBase( ) {
    }

    public BitsBase( String of ) {
        this( of != null && !of.isEmpty() ? of.toCharArray() : new char[]{} );
    }

    public BitsBase( char[] of ) {
        if( of.length > 0 )
            for( int i = 0; i < getSize() && i < of.length; i++ )
                doSet( i, of[i] != '0' );
    }

    /** */
    @Override
    public boolean get( int index ) {

        if( index >= getSize() || index < 0 )
            throw new IndexOutOfBoundsException("Bits index out of range: " + index);

        return doGet(index);
    }

    /** */
    @Override
    public void set( int index, boolean v ) {

        if( index >= getSize() || index < 0 )
            throw new IndexOutOfBoundsException("Bits index out of range: " + index);

        doSet(index,v);
    }

    /** */
    public void setRange(int from, int to, boolean value) {
        for( int i = from; i <= to; i++)
             set( i, value );
    }

    /** Количество установленных битов */
    public int cardinality() {
        return Integer.bitCount(intValue());
    }

    /** */
    public Number asNumber() { return this; }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder( getSize() * 2 + 2  );
        sb.append('{');
        IntStream.range( 0, getSize() ).forEach( i->sb.append( doGet(i) ? '1' : '0' ).append(',') );
        sb.setCharAt( sb.length() - 1, '}');
        return sb.toString();
    }
}
