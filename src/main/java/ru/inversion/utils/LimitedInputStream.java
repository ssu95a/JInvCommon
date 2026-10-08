package ru.inversion.utils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * Обертка вокруг {@code InputStream} для ограниченного по объему чтения байтов из потока.
 * <p>
 * Предполагается что будет доступно для чтения из потока 
 * N кол-во байт, переданные через конструктор.
 * 
 * @author banin 
 */
public final class LimitedInputStream extends InputStream {

    /** */
    public static final InputStream EMPTY_STREAM = new InputStream() {
        @Override
        public int read() throws IOException {
            return -1;
        }
        @Override
        public int read(byte b[], int off, int len) throws IOException {
            return -1;
        } 
    };
   
    /** поток откуда будут считаны данные*/
    private final InputStream in;
   
    /** Счетчик байтов */
    private long pos = 0;
    
    /** Кол-во байт для чтения */
    private final int maxSize;

    /** */
    public LimitedInputStream( final InputStream in, final int maxSize ) throws IOException {
        
        this.in      = Objects.requireNonNull( in, "stream is null" );
        this.maxSize = maxSize; 
    }

    @Override
    public int read() throws IOException {
        
        if( maxSize >=0 && pos == maxSize ) {
            return -1;
        }
        
        int result = in.read();
        
        pos++;
        
        return result;
    }
    
    @Override
    public int read(byte[] b) throws IOException {
        return this.read(b, 0, b.length);
    }
    
    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (maxSize >= 0 && pos >= maxSize) {
            return -1;
        }
        long maxRead = maxSize >= 0 ? Math.min(len, maxSize-pos) : len;
        int bytesRead = in.read(b, off, (int) maxRead);

        if (bytesRead==-1) {
            return -1;
        }

        pos += bytesRead;
        
        return bytesRead;
    }

    @Override
    public int available() throws IOException {
        if (maxSize >= 0 && pos >= maxSize) {
            return 0;
        }
        return in.available();
    }
    
    @Override
    public void close() throws IOException {
        in.close();
    }

    /**
     * Ф-ция создания обертки для ограниченного чтения.
     * <p>
     * Рекомендуется к использованию.
     * 
     * @param is 
     *      поток откуда будет производиться чтение, если передается {@code null}, то возвращается {@code null}
     * @param maxSize
     *      кол-во байтов доступные из потока. Если {@code  maxSize < 0} то возвращается ссылка на is.
     */
    public static InputStream of( InputStream is, int maxSize ) throws IOException {

        if( is == null )
            return null;

        if( maxSize == 0 )  
            return EMPTY_STREAM;
        
        if( maxSize < 0L )
            return is;

        return new LimitedInputStream( is, maxSize );
    }
    
}
