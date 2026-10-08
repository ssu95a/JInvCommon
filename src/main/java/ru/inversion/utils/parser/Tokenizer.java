package ru.inversion.utils.parser;

import ru.inversion.utils.parser.state.*;
import ru.inversion.utils.CharSupplier;
import ru.inversion.utils.U;

import java.io.BufferedReader;
import java.io.Reader;
import java.io.StringReader;
import java.util.*;

/** */
public class Tokenizer implements AutoCloseable {

    /** */
    public interface IContext {
        char current ( );
        char next    ( );
        char previous( );
        char    peek ( ); // previous + 1
        //char[]   peek ( int n);
        boolean unget( ); //

        boolean eof  ( );

        boolean shift( ); // + 1

        int  lineNum ( );
        int  symbNum ( );

//        void    mark ( );
//        void   reset ( );
    }

    /** */
    private class Context implements IContext {

        final private char[] buffer = new char[100];
        private int index = -1;
        /** */
        public Context() {

            char ch = ' ';

            for( int i = 0; i < buffer.length && ch != 0; i++ ) {
                //ch = Tokenizer.this.read();
                ch = charReader.getAsChar();
                buffer[i] = ch;
            }

            index = 0;
        }

        @Override
        public char current() { return buffer[index]; }

        @Override
        public char next() { return buffer[index+1];}

        @Override
        public char previous() { return buffer[index-1];}

        @Override
        public char peek( ) {
            return buffer[index+2];
        }

        @Override
        public boolean unget() {

            if( index == 0 )
                return false;

            index --;

            return true;
        }

        @Override
        public boolean eof() {
            return current() == (char)0;
        }

        @Override
        public boolean shift() {

            if( eof() )
                return false;

            ++index;

            if( eof() )
                return false;

            if( index >= buffer.length - 3 )
            {
                char ch = ' ';

                int i = 0;
                int k = 0;
                for( ; i < 5; i++ ) {
                     k = buffer.length - ( 5-i );
                     buffer[i] = buffer[k];
                }

                for( ; i < buffer.length && ch != 0; i++ ) {
                    //ch = Tokenizer.this.read();
                    ch = charReader.getAsChar();
                    buffer[i] = ch;
                }

                for( ; i < buffer.length; i++ ) {
                    buffer[i] = (char)0;
                }

                index = 2;
            }

            return true;
        }

        @Override
        public int lineNum() {
            return Tokenizer.this.lineNum;
        }

        @Override
        public int symbNum() {
            return Tokenizer.this.symbNum;
        }
    }

    //
    final private Reader reader;

    //
    final private Context context;

    //
    private int lineNum = 0,
                symbNum = 0;

    protected final List<ITokenHandler<?>> states;

    protected Tokenizer( Reader reader )
    {
        if( reader == null )
            throw new IllegalArgumentException("ReaderScanner: reader is null");

        if( reader instanceof BufferedReader || reader instanceof StringReader)
            this.reader = reader;
        else
            this.reader = new BufferedReader( reader, 50 );

        this.context = new Context( );

        states = prepareStates();
    }

    /** */
    protected List<ITokenHandler<?>> prepareStates( )
    {
        return Arrays.asList(
            new NumberHandler(),
            new CommentHandler(),
            new QuotesHandler(),
            new StringHandler(),
            new OperatorHandler(),
            new PgPositionParameterHandler(),
            new NamedParameterHandler(),
            new SyntaxSymbolHandler(),
            new ExpressionHandler()
        );
    }

    /** */
    @Override
    public void close() throws Exception {
    }

    /** */
    public IContext context() { return context; }
    public List<ITokenHandler<?>> states() { return states; }
    /** */
    private CharSupplier charReader = this::_read;

    /** */
    private final char[] _ch = new char[1];

    /** */
    private char _read( ) {

        try {

            do {

                if( reader.read(_ch) == -1 ) {
                    reader.close();

                    charReader = ()->(char)0;

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
        catch( Throwable th ) {
            throw new RuntimeException( "Error on read next char from Reader. Context: " /*+ context.toString()*/, th );
        }
    }

    /** */
    private static Iterable<Token<?>> tokenize(Reader r )
    {
        final Tokenizer ep = new Tokenizer(r);
        final Context    ctx = ep.context;

        final Iterator<Token<?>> iter = new Iterator<Token< ? >>() {

            //private int totalToken = 0;

            /** */
            @Override
            public boolean hasNext() {
                return !ctx.eof();
            }

            /** */
            @Override
            public Token<?> next() {

                try {

                    while( ITokenHandler.isSpace( ctx.current() ) && ctx.shift() ) {}

                    if( ctx.eof() )
                        return null;

                    for( ITokenHandler<?> state : ep.states )
                    {
                        if( state.matches(ctx) )
                            return state.apply( ctx );
                    }

                    return ep.states.get(7).apply(ctx);
                }
                catch( Throwable th ) {
                    throw new RuntimeException(th);
                }
            }
        };
        return U.iterable( iter );
    }

    /** */
    public static Iterable<Token<?>> parse(Reader r )
    {
        return U.iterable (
            new Parser(tokenize(r).iterator() )
        );
    }
    /** */
    public static Iterable<Token<?>> parse(String s )
    {
        return parse( new StringReader(s) );
    }
}
