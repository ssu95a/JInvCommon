package ru.inversion.utils.io;

import java.io.*;
import java.util.Arrays;

public class RawBAOS extends ByteArrayOutputStream {
    public RawBAOS() {}
    public RawBAOS( int size ) { super(size);}
    public synchronized byte[] buf() {
        if( count < buf.length )
            buf = Arrays.copyOf(buf, count);
        return this.buf;
    }
    public synchronized int count()  { return this.count;}
    public synchronized InputStream inputStream() { return new ByteArrayInputStream( buf, 0, count() );}
    public synchronized InputStream inputStream(int offset, int length) { return new ByteArrayInputStream(buf,offset,Math.min( length, count()));}
    /** */
    public void write( InputStream is ) throws IOException
    {
        if( is == null )
            return;

        final byte[] buffer = new byte[4096]; // Оптимальный размер буфера
        int length;
        while((length = is.read(buffer)) != -1) {
            write( buffer, 0, length );
        }
    }

}


