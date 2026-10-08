package ru.inversion.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Char cursor.
 * Посимвольно идет по Reader, давая текущий символ, соседние символы и позицию.
 * @author ssu
 */
public class ReaderScanner implements Iterable<ReaderScanner.IContext>, AutoCloseable {
    
    public static class ReaderScannerException extends RuntimeException {
        public ReaderScannerException( ) { }
        public ReaderScannerException( String message ) {
            super(message);
        }
        public ReaderScannerException( String message, Throwable cause) {
            super(message, cause);
        }
        public ReaderScannerException( Throwable cause ){
            super(cause);
        }
    }

    /** */
    public static final class Position {

        private final int lineNum;
        private final int symbNum;

        public Position( int lineNum, int symbNum ) {
            this.lineNum = lineNum;
            this.symbNum = symbNum;
        }

        public int lineNum() {
            return lineNum;
        }

        public int symbNum() {
            return symbNum;
        }

        @Override
        public String toString() {
            return "line=" + lineNum + ", symb=" + symbNum;
        }
    }

    /** текущий скан контекст */
    public interface IContext {
        char current ( );
        char next    ( );
        char previous( );
        int  lineNum ( );
        int  symbNum ( );

        Position position();
    }

    final private Reader reader;
    
    private int			 lineNum = 0,
                         symbNum = 0;
    
    final private Context context = new Context();
    /** */
    private class Context implements IContext {
        private final char ch[] = new char[]{ '\n', '\n', 0 };
        public Context( ) {
        }
        @Override
        public char current( ) { return ch[1];  }
        @Override
        public char next   ( ) { return ch[2];  }
        @Override
        public char previous( ){ return ch[0];  }
        @Override
        public int lineNum( )  { return lineNum;}
        @Override
        public int symbNum( )  { return symbNum;}
        // mark
        // reset
        // unget
        // peek
        @Override
        public String toString( ){
            return "sc: p = " + ch[0] + ", с = " + ch[1] + ", n = " + ch[2] + ", line = " + lineNum + ", symb = " + symbNum;
        }

        @Override
        public Position position() {
            return new Position( lineNum, symbNum );
        }
    }
    
    /** */
    private ReaderScanner( Reader reader ) {

        if( reader == null )
            throw new IllegalArgumentException("ReaderScanner: reader is null");

        if( reader instanceof BufferedReader || reader instanceof StringReader )
            this.reader = reader;
        else
            this.reader = new BufferedReader(reader, 50);
    }
    
    /** */
    @Override
    public Iterator<IContext> iterator() {
        return new RCIterator();
    }

    /** */
    @Override
    public void close() throws IOException {
        reader.close();
    }

	/** */
	private char _ch[] = new char[1];

    private char read( ) throws IOException {

		do {

			if( reader.read( _ch ) == -1 ) {
			    reader.close();
                return 0;
            }

		} while( _ch[0] == '\r' );
		
        if( _ch[0] == '\n' ) {
            lineNum ++;
            symbNum = 0;
        }
        else
            symbNum ++;

        return _ch[0];
    }

    /** */
    private class RCIterator implements Iterator<IContext> {

        public RCIterator() {
            try {
//                context.ch[1] = read( );
                context.ch[2] = read( );
            }
            catch( Throwable th ) {
                throw new ReaderScannerException( "Error on init Iterator", th );
            }
        }

        @Override
        public boolean hasNext( ) {
            return context.ch[2] != 0;
        }

        @Override
        public IContext next( ) {
            
            if( !hasNext() )
                 throw new NoSuchElementException();
            
            try {
                
                context.ch[0] = context.ch[1];
                context.ch[1] = context.ch[2];
                context.ch[2] = read( );
                return context;
            }
            catch( Throwable th ) {
                throw new ReaderScannerException( "Error on read next char from Reader. Context: " + context.toString(), th );
            }
        }
    }
    
    /** */
    public static Iterable<IContext> newIterable( Reader r ) {
        return new ReaderScanner(r);
    }
    /** */
    public static Iterable<IContext> newIterable( String s ) {
        return new ReaderScanner( new StringReader(s) );
    }
    /** */
    public static Stream<IContext> newStream( Reader r ) {
        Iterable<IContext> iterable = newIterable(r);
        return StreamSupport.stream(iterable.spliterator(), false );
    }
    /** */
    public static Stream<IContext> newStream( String s ) {
        return newStream( new StringReader(s) );
    }
}
