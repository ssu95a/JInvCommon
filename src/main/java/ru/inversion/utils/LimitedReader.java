package ru.inversion.utils;

import java.io.IOException;
import java.io.Reader;
import java.util.Objects;

/**
 * Обертка вокруг {@code Reader} для ограниченного по объему чтения символов из потока.
 * <p>
 * Предполагается что будет доступно для чтения из потока 
 * N кол-во символов, переданных через конструктор.
 * 
 * @author banin 
 */
public class LimitedReader extends Reader {

    /** */
    public static final Reader EMPTY_READER = new Reader() {
        @Override
        public int read() throws IOException {
            return -1;
        }
        @Override
        public int read(char[] cbuf, int off, int len) throws IOException {
            return -1;
        }
        @Override
        public void close() throws IOException {
        }
    };
    
    /** Откуда считывать данные*/
    private final Reader reader;

    /** Кол-во символов доступных для чтения*/
    private final int maxChars;
    
    /** Кол-во прочитанных символов */
    private int charsRead = 0;
    
    /** */
    public LimitedReader( final Reader reader, final int maxChars ) {
        this.reader   = Objects.requireNonNull(reader, "reader is null");
        this.maxChars = maxChars;
    }
    
    /** */
    @Override
    public int read() throws IOException {

        if ( charsRead >= maxChars ) {
            return -1;
        }
        
        charsRead++;
        
        return reader.read();
    }
    
    @Override
    public int read(char[] cbuf, int off, int len) throws IOException {
        int c;
        for ( int i = 0; i < len; i++ ) {
            c = read();
            if ( c == -1 ) {
                return i;
            }
            cbuf[off + i] = (char) c;
        }
        return len;
    }
    
    @Override
    public void close() throws IOException {
        reader.close();
    }
    
    /**
     * Ф-ция создания обертки для огранниченного чтения.
     * <p>
     * Рекомендуется к использованию.
     * 
     * @param r
     *      поток откуда будет произволиться чтение, если передается {@code null}, то возвращается {@code null}
     * @param maxChars
     *      кол-во символов доступные из потока. Если {@code  maxChars < 0} то возвращается ссылка на is.
     */
    public static Reader of( Reader r, int maxChars ) throws IOException {

        if( maxChars < 0 )
            return r;

        if( maxChars == 0 )
            return EMPTY_READER;

        return new LimitedReader( r, maxChars );
    }
    
}
