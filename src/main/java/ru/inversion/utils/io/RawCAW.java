package ru.inversion.utils.io;

import ru.inversion.utils.S;

import java.io.*;
import java.util.Arrays;

public class RawCAW extends CharArrayWriter implements CharSequence {

    private static final int TINY_SIZE     = 256;  // всегда String
    private static final int SNAPSHOT_SIZE = 4096; // Максимум для снапшота

    // Коэффициенты
    private static final int STRING_RATIO = 10;    // Для small

    public RawCAW() {}
    public RawCAW( int size ) { super(size);}
    public synchronized char[] buf() {
        if( buf.length > this.count )
            buf = Arrays.copyOf( buf, count );
        return this.buf;
    }
    public synchronized int count()     { return this.count; }
    public synchronized Reader reader() { return new CharArrayReader(buf,0,count); }
    public synchronized Reader reader(int offset, int length) { validateRange( offset, length ); return new CharArrayReader(buf,offset,length);}
    /** */
    public void write( Reader r ) throws IOException
    {
        if( r == null )
            return;

        final char[] buffer = new char[4096]; // Оптимальный размер буфера
        int length;
        while((length = r.read(buffer)) != -1) {
               write( buffer, 0, length );
        }
    }


    @Override
    public int length() {
        return count;
    }

    @Override
    public char charAt(int index)
    {
        if( index < 0 || index >= count )
            throw new StringIndexOutOfBoundsException(index);

        return buf[index];
    }

    @Override
    public synchronized String toString() {
        return new String( buf, 0, count );
    }

    private class CharSegment implements CharSequence {

        private final int offset;
        private final int length;

        CharSegment( int offset, int length ) {
            this.offset = offset;
            this.length = length;
        }

        @Override
        public int length() {
            return length;
        }

        @Override
        public char charAt(int index)
        {
            if( index < 0 || index >= length )
                throw new IndexOutOfBoundsException( "Index: " + index + ", Segment length: " + length);

            return buf[offset + index];
        }

        @Override
        public CharSequence subSequence(int start, int end)
        {
            if( start < 0 || end > length || start > end )
                throw new IndexOutOfBoundsException( String.format("start=%d, end=%d, segment length=%d", start, end, length) );

            int length = end - start;

            if( length == 0 )
                return S.EMPTY_STRING;

            if( length <= TINY_SIZE || length * STRING_RATIO < count  )
                return new String( buf, offset + start, length );

            return new CharSegment( offset + start, end - start );
        }

        @Override
        public String toString() {
            return new String( buf, offset, length );
        }

        public int getOffset() {
            return offset;
        }

        public boolean isEmpty() {
            return length == 0;
        }
    }

    /** */
    private void validateRange(int offset, int length)
    {
        if( offset < 0 || length < 0 || offset + length > count )
            throw new IndexOutOfBoundsException( String.format("offset=%d, length=%d, count=%d", offset, length, count) );
    }

    @Override
    public CharSequence subSequence( int start, int end )
    {
        if( start < 0 || end > count || start > end )
            throw new IndexOutOfBoundsException( String.format("start=%d, end=%d, length=%d", start, end, count) );

        int length = end - start;

        if( length == 0 )
            return S.EMPTY_STRING;

        if( length > SNAPSHOT_SIZE )
            return new CharSegment( start, length );

        if( length <= TINY_SIZE )
            return new String( buf, start, length );

        // String если малая часть буфера
        if( length * STRING_RATIO < count )
            return new String( buf, start, length );

        return new CharSegment(start, length);
    }
}
