package ru.inversion.utils.io;

import ru.inversion.utils.Tags;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import static ru.inversion.utils.LimitedInputStream.EMPTY_STREAM;

public class SegmentedBAOS extends ByteArrayOutputStream {

    /** */
    final protected List<byte[]> segments = new LinkedList<>();

    /** */
    final protected int segmentSize;

    /** {@inheritDoc} */
    public SegmentedBAOS() {
        this(512);
    }

    /** {@inheritDoc} */
    public SegmentedBAOS( int size ) {
        super(size);
        segmentSize = size;
        segments.add( buf );
    }

    /** */
    private void ensureOpen() {
        if( count == -1 )
            throw new IllegalStateException(Tags.PRODUCT_LABEL + "Writer is closed");
    }

    /** {@inheritDoc} */
    private void grow( ) {
        buf   = new byte[segmentSize];
        count = 0;
        segments.add( buf );
    }

    /** {@inheritDoc} */
    @Override
    public synchronized int size() {
        ensureOpen();
        return count + ( segments.size() - 1 ) * segmentSize;
    }

    /** {@inheritDoc} */
    @Override
    public synchronized void write( int b ) {

        ensureOpen();

        if( count == segmentSize )
            grow();
        buf[count] = (byte)b;
        count++;
    }

    /** {@inheritDoc} */
    @Override
    public synchronized void write( byte[] b, int off, int len) {

        ensureOpen();

        Objects.requireNonNull(b, "'b' is null");

        if ((off < 0) || (off > b.length) || (len < 0) ||
                ((off + len) - b.length > 0)) {
            throw new IndexOutOfBoundsException();
        }

        int r = segmentSize - count;

        if( len <= r )
        {
            System.arraycopy( b, off, buf, count, len );
            count += len;
        }
        else
        {
            System.arraycopy( b, off, buf, count, r );

            len -= r;
            off += r;

            do {

                grow();

                r = Math.min( len, segmentSize );
                System.arraycopy( b, off, buf, 0, r );

                count = r;

                len -= r;
                off += r;

            } while( len > 0 );
        }
    }

    /** {@inheritDoc} */
    @Override
    public synchronized void writeTo( OutputStream out) throws IOException {

        ensureOpen();

        for( byte[] b : segments ) {
             if( b == buf )
                 out.write( b, 0, count );
             else
                 out.write( b );
        }
    }

    /** {@inheritDoc} */
    @Override
    public synchronized void reset() {

        ensureOpen();

        segments.clear();
        segments.add(buf);
        count = 0;
    }

    /** {@inheritDoc} */
    @Override
    public synchronized byte[] toByteArray() {

        ensureOpen();

        final byte[] returnBuf = new byte[size()];

        int off = 0;

        for( byte[] b : segments )
        {
            int length = (b == buf) ? count : b.length;
            System.arraycopy( b, 0, returnBuf, off, length );
            off += length;
        }
        return returnBuf;
    }

    /** {@inheritDoc} */
    @Override
    public synchronized String toString() {
        try {
            return toString( StandardCharsets.UTF_8.name() );
        } catch(UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    /** {@inheritDoc} */
    @Override
    public synchronized String toString( String charsetName )
        throws UnsupportedEncodingException
    {
        if( segments.size() == 1 )
            return new String( buf, 0, count, charsetName );

        return new String( toByteArray(), charsetName );
    }


    private int refCount = 1;

    /** */
    @Override
    public synchronized void close() throws IOException {

        refCount--;

        // Thread.yield();

        if( refCount > 0 )
            return;

        segments.clear();
        buf   = null;
        count = -1;
    }

    /** */
    public synchronized InputStream inputStream()
    {
        ensureOpen();

        if( segments.isEmpty() )
            return EMPTY_STREAM;

        return new InputStream( ) {

            final private Iterator<byte[]> segmentIter = segments.iterator();

            private byte[] currentSegment;
            private int currentSize = 0;
            private int pos = 0;

            {
                SegmentedBAOS.this.refCount++;
                next();
            }

            @Override
            public int read( ) throws IOException {

                if( pos == -1 )
                    return -1;

                //return (pos < count) ? (buf[pos++] & 0xff) : -1;

                if( pos >= currentSize )
                {
                    if(!next() )
                        return -1;
                }
                return ( currentSegment[pos++] & 0xff );
            }

            /** */
            private boolean next( )
            {
                if( !segmentIter.hasNext() ) {
                     pos = -1;
                     return false;
                }

                currentSegment = segmentIter.next();
                currentSize = currentSegment == buf ? count : currentSegment.length;
                pos = 0;

                return true;
            }

            /** */
            @Override
            public int read( byte[] b, int off, int len ) throws IOException {

                if( pos == -1 )
                    return -1;

                Objects.requireNonNull( b, "'b' is null" );

                if( off < 0 || len < 0 || len > b.length - off ) {
                    throw new IndexOutOfBoundsException();
                } else if (len == 0) {
                    return 0;
                }

                final int originalLen = len;

                do {

                    int readLen = Math.min( currentSize - pos, len );

                    System.arraycopy( currentSegment, pos, b, off, readLen );

                    pos += readLen;
                    off += readLen;
                    len -= readLen;

                    if( pos >= currentSize )
                        if( !next() )
                            break;

                } while( len > 0 );

                return originalLen - len;
            }

            @Override
            public void close() throws IOException {
                currentSegment = null;
                SegmentedBAOS.this.close();
            }
        };
    }

    /** */
    public synchronized int getSegmentCount( ) {
        return segments.size();
    }

    /** */
    public synchronized int getSegmentSize( ) {
        return segmentSize;
    }

    /** */
    public synchronized boolean isEmpty( ) {
        return size() == 0;
    }
}
