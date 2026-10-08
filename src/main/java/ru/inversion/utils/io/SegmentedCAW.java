package ru.inversion.utils.io;

import ru.inversion.utils.S;
import ru.inversion.utils.Tags;
import ru.inversion.utils.U;

import java.io.*;
import java.text.CharacterIterator;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;

import static ru.inversion.utils.LimitedReader.EMPTY_READER;

/** */
public class SegmentedCAW extends CharArrayWriter {

    final private static Character NL = '\n';
    final private static Character SPACE = ' ';

    final protected LinkedList<Object> segments = new LinkedList<>();

    /** */
    final protected int segmentSize;

    private int size = 0;

    /** */
    public SegmentedCAW( ) {
        this(1024);
    }

    /** */
    public SegmentedCAW( int size ) {
        super(size);
        segmentSize = size;
    }

    /** */
    @Override
    public int size() {
        return size;
    }

    /** */
    private void ensureOpen() {
        if( size == -1 )
            throw new IllegalStateException(Tags.PRODUCT_LABEL + "Writer is closed");
    }
    /** */
    @Override
    public void reset() {

        ensureOpen();

        synchronized(lock) {
            segments.clear();
            size = 0;
            super.reset();
        }
    }


    /** */
    private Character ch ( int c )
    {
        ensureOpen();

        char ch = (char)c;
        switch( ch ) {
            case '\n':
                return NL;
            case ' ':
                return SPACE;
        }

        return ch;
    }

    /** */
    public void write( int c ) {

        ensureOpen();

        synchronized( lock )
        {
            if( count == segmentSize )
            {
                segments.add( buf );

                buf   = new char[segmentSize];
                count = 0;
            }

            buf[count] = (char)c;
            count++;
            size ++;
        }
    }

    @Override
    public void write( String str ) throws IOException {

        ensureOpen();

        if( S.isNullOrEmpty(str) )
            return;

        if( str.length() == 1 )
            write( str.charAt(0) );
        else
        {
            synchronized( lock )
            {
                if( count > 0 )
                {
                    if( count == 1 )
                        segments.add( ch(buf[0]) );
                    else
                        segments.add( Arrays.copyOf( buf, count ) );

                    count = 0;
                }
                segments.add( str );

                size += str.length();
            }
        }
    }

    /** */
    public void write( char[] c, int off, int len ) {

        ensureOpen();

        if( c == null || c.length == 0 || len == 0 )
            return;

        if ((off < 0) || (off > c.length) || (len < 0) ||
                ((off + len) > c.length) || ((off + len) < 0)) {
            throw new IndexOutOfBoundsException();
        }

        synchronized(lock) {

            if( count == 0 )
            {
                if( segmentSize > len ) {
                    System.arraycopy( c, off, buf, 0, len );
                    count = len;
                }
                else
                    segments.add( Arrays.copyOfRange( c, off, off + len ) );
            }
            else
            {
                final char[] b = new char[count + len];

                System.arraycopy( buf, 0, b, 0, count   );
                System.arraycopy( c, off, b, count, len );

                segments.add( b );

                count = 0;
            }
            size += len;
        }//lock
    }

    /** {@inheritDoc} */
    @Override
    public void write( String str, int off, int len) {

        ensureOpen();

        if( S.isNullOrEmpty(str) )
            return;

        synchronized( lock ) {

            if( count == 0 )
            {
                if( len > buf.length )
                {
                    char[] c= new char[len];
                    str.getChars(off, off + len, c, 0 );
                    segments.add( c );
                }
                else
                {
                    str.getChars( off, off + len, buf, 0 );
                    count = len;
                }
            }
            else
            {
                if( buf.length < count + len )
                {
                    char[] b= new char[len+count];
                    System.arraycopy( buf, 0, b, 0, count );
                    str.getChars(off, off + len, b, count );

                    segments.add( b );

                    count = 0;
                }
                else
                {
                    str.getChars(off, off + len, buf, count );
                    count += len;
                }
            }

            size += len;
        }// lock
    }

    /** */
    @Override
    public void write( char[] cbuf ) throws IOException {
        write( cbuf, 0, cbuf.length);
    }

    /** */
    public void write( Reader r ) throws IOException
    {
        ensureOpen( );

        if( r == null )
            return;

        synchronized(lock) {

            if( count > 0 ) {
                segments.add( Arrays.copyOf( buf, count ) );
                size += count;
                count = 0;
            }

            final char[] buffer = new char[segmentSize];
            int readed;
            while((readed = r.read(buffer)) != -1)
            {
                if( readed == 0 )
                    continue;

                if( readed == segmentSize ) {
                    // Полный буфер - сразу добавляем
                    segments.add(Arrays.copyOf(buffer, readed));
                    size += readed;
                }
                else
                {
                    // Частичный буфер - используем текущий buf
                    if (readed <= buf.length - count)
                    {
                        System.arraycopy(buffer, 0, buf, count, readed);
                        count += readed;
                        size  += readed;
                    }
                    else
                    {
                        // Текущий буфер заполнен, добавляем в сегменты
                        if (count > 0) {
                            segments.add(Arrays.copyOf(buf, count));
                            size += count;
                            count = 0;
                        }
                        // Для больших данных добавляем как отдельный сегмент
                        segments.add(Arrays.copyOf(buffer, readed));
                        size += readed;
                    }
                }
            }
        }
    }

    @Override
    public char[] toCharArray() {

        ensureOpen();

        synchronized(lock) {

            final RawCAW caw = new RawCAW(size);
            try {
                writeTo(caw);
            } catch( IOException e ) {
                throw new RuntimeException(e);
            }
            return caw.buf();
        }
    }

    /** */
    @Override
    public String toString() {

        ensureOpen();

        synchronized(lock) {

            if( segments.isEmpty() && count == 0 )
                return S.EMPTY_STRING;

            if( segments.isEmpty() )
                return new String(buf,0,count);

            return new String( toCharArray() );
        }
    }

    /** {@inheritDoc} */
    @Override
    public void writeTo( Writer out) throws IOException {

        ensureOpen();

        synchronized( lock )
        {
            for( Object o : segments ) {
                 if( o instanceof String )
                    out.write( (String)o );
                 else if ( o instanceof char[] )
                    out.write( (char[])o );
                 else if ( o instanceof Character )
                     out.write( (Character)o );
            }//end for

            if( buf != null )
                out.write( buf, 0, count );
        }
    }

    private int refCount = 1;

    @Override
    public void close() {

        refCount--;

        // Thread.yield();

        synchronized(lock) {

            if( refCount > 0 )
                return;

            segments.clear();
            size = -1;
            count= 0;
            buf  = null;
        }
    }

    public synchronized Reader reader()
    {
        ensureOpen();

        if( count == 0 && segments.isEmpty()  )
            return EMPTY_READER;

        return new Reader( ) {

            final private Iterator segmentIter = segments.iterator();

            private Object currentSegment;
            private int currentSize = 0;
            private int pos = 0;
            private int currentIndex = 0;

            {
                SegmentedCAW.this.refCount++;
                next();
            }

            /** */
            private boolean next( )
            {
                if( pos == -1 )
                    return false;

                currentIndex = 0;

                if(!segmentIter.hasNext() ) {

                    if( buf == null || currentSegment == buf ) {
                        pos = -1;
                        return false;
                    }

                    currentSegment = buf;
                    currentSize    = count;
                    return true;
                }

                currentSegment = segmentIter.next();

                if( currentSegment instanceof Character )
                    currentSize = 1;
                else if( currentSegment instanceof String )
                    currentSize = ((String)currentSegment).length();
                else
                    currentSize = ((char[])currentSegment).length;

                return true;
            }

            @Override
            public int read() throws IOException {

                synchronized( this.lock ) {

                    if( pos == -1 )
                        return -1;

                    if( currentSegment instanceof Character ) {
                        Character ch = (Character)currentSegment;
                        pos++;
                        currentIndex = 0;
                        next();
                        return ch;
                    }

                    if( currentIndex >= currentSize )
                    {
                        if( !next() )
                             return -1;

                        return read();
                    }

                    pos ++;

                    if( currentSegment instanceof String )
                        return ((String)currentSegment).charAt(currentIndex++);
                    else
                        return ( ((char[])currentSegment)[currentIndex++] );
                }
            }

            /** */
            @Override
            public int read( char[] b, int off, int len ) throws IOException {

                if( pos == -1 )
                    return -1;

                if ((off < 0) || (off > b.length) || (len < 0) ||
                        ((off + len) > b.length) || ((off + len) < 0)) {
                    throw new IndexOutOfBoundsException();
                } else if (len == 0) {
                    return 0;
                }

                final int originalLen = len;

                do {

                    int readLen = Math.min( currentSize - currentIndex, len );

                    if( currentSegment instanceof char[] )
                        System.arraycopy( (char[])currentSegment, currentIndex, b, off, readLen );
                    else if( currentSegment instanceof String )
                        ((String)currentSegment).getChars( currentIndex, currentIndex + readLen, b, off );
                    else if( currentSegment instanceof Character ) {
                        readLen = 1;
                        b[off] = ((Character)currentSegment).charValue();
                    }

                    pos += readLen;
                    off += readLen;
                    len -= readLen;

                    currentIndex += readLen;

                    if( currentIndex >= currentSize )
                        if( !next() )
                            break;

                } while( len > 0 );

                return originalLen - len;
            }

            @Override
            public void close() throws IOException {
                SegmentedCAW.this.close();
            }
        };
    }

    /** */
    public synchronized int getSegmentCount() {
        return segments.size();
    }
    /** */
    public synchronized int getSegmentSize() {
        return segmentSize;
    }
    /** */
    public synchronized boolean isEmpty() {
        return size() == 0;
    }
}
