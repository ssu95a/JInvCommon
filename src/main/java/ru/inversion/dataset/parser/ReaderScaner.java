package ru.inversion.dataset.parser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;

/**
 * Класс для считывания и разбора файлов
 * @author Ssu
 */
public class ReaderScaner {

    /** текущий скан контекст */
    public interface IContext {
        char current ( );
		char next    ( );
		char previous( );
		int  lineNum ( );
		int  symbNum ( );
    }
    /** */
    public enum ProcessResultEnum {
        OK,
        STOP
    }
    /** */
    public interface IScanerListener {
        ProcessResultEnum process( IContext context );
    }

    final private IScanerListener listener;
    final private Reader          reader;
    private int					  lineNum = 0,
								  symbNum = 0;

    final private Context         context = new Context();
    /** */
    private class Context implements IContext {
        private final char ch[] = new char[]{ '\n', 0, 0 };
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
        @Override
        public String toString( ){
            return "sc: p = " + ch[0] + ", с = " + ch[1] + ", n = " + ch[2] + ", line = " + lineNum + ", symb = " + symbNum;
        }
    }
    /** */
    public ReaderScaner( Reader reader, IScanerListener listener ) {

        if( reader == null || listener == null )
            throw new IllegalArgumentException("ReaderScaner: reader or listener is null");

        if( reader instanceof BufferedReader || reader instanceof StringReader )
            this.reader = reader;
        else
            this.reader = new BufferedReader(reader);
        this.listener = listener;
    }
    /** */
    public void process( ) {

        try {
        
            context.ch[1] = read( );
            context.ch[2] = read( );

            while( context.ch[0] != 0 ) {

                if( listener.process(context) == ProcessResultEnum.STOP )
                    break;

                context.ch[0] = context.ch[1];
                context.ch[1] = context.ch[2];

                context.ch[2] = read( );
            }
        }
        catch( IOException ex ) {
            throw new RuntimeException("ReaderScaner: error on read from reader", ex);
        }
    }
	/** */
	private char _ch[] = new char[1];

    private char read( ) throws IOException {

		do {	
			if( reader.read( _ch ) == -1 )
				return 0;
		} while( _ch[0] == '\r' );
		
        if( _ch[0] == '\n' ) {
            lineNum ++;
            symbNum = 0;
        }
        else
            symbNum ++;

        return _ch[0];
    }
}
