package ru.inversion.parser.token;

import ru.inversion.utils.ReaderScanner;
import ru.inversion.utils.S;
import ru.inversion.utils.Tags;
import ru.inversion.utils.U;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.Queue;

import static ru.inversion.parser.token.Token.TypeEnum.EXPRESSION;

/**
 *
 * @author ssu
 */
public class TokenHandler implements Iterable<Token<?>> {

    /** */
    private ITokenState currentState;

    /** */
    final private long options;

    /** */
    final private Iterator< ReaderScanner.IContext > rsIterator;

    /** */
    public TokenHandler( String strSQL, long options ) {

        if( S.isNullOrEmpty(strSQL) )
            throw new IllegalArgumentException( "'strSQL' is null" );
        this.options    = options;
        this.rsIterator = ReaderScanner.newIterable( strSQL ).iterator();
    }

    /** */
    public TokenHandler( String strSQL ) {
        this( strSQL, 0L );
    }

    /** */
    @Override
    public Iterator< Token<?> > iterator() {

        return new Iterator< Token<?> >() {

            //Token current;
            final Queue<Token> tokenQueue = new LinkedList<>();

            {
                getNextToken( );
            }

            @Override
            public boolean hasNext( ) {
                return !tokenQueue.isEmpty();
            }

            @Override
            public Token<?> next( ) {

                if( !hasNext() )
                     throw new NoSuchElementException();

                Token<?> t = tokenQueue.poll();
                getNextToken();
                return t;
            }

            /** */
            private Token getToken( ) {

                Token t = null;

                while( rsIterator.hasNext() && t == null ) {
                    t = TokenHandler.this.handle( rsIterator.next() );
                }

                return t;
            }

            /** */
            private void getNextToken( ) {

                Token<?> t1 = getToken( );

                if( t1 == null )
                    return;

                if( t1.getType() == EXPRESSION
                    &&
                    t1.getValue() != null
                )
                {
                    String expr = ((String)t1.getValue()).toUpperCase();

                    if( expr.length() == 5 )
                    {
                        if( "WHERE".equalsIgnoreCase(expr) )
                        {
                            tokenQueue.add( Token.Where );
                        }
                        else if( S.in( expr, "ORDER", "GROUP" ) )
                        {
                            Token<?> t2 = getToken( );

                            if( t2 == null )
                                tokenQueue.add(t1);
                            else
                            {
                                if( t2.getType() == EXPRESSION && "by".equalsIgnoreCase((String)t2.getValue() ) )
                                {
                                    tokenQueue.add (
                                        expr.charAt(0) == 'O'
                                        ?
                                        Token.OrderBy
                                        :
                                        Token.GroupBy
                                    );
                                }
                                else
                                {
                                    tokenQueue.add(t1);
                                    tokenQueue.add(t2);
                                }
                            }
                        }
                        else
                            tokenQueue.add(t1);
                    }
                    else
                        if( expr.length() == 6 )
                        {
                            if( "SELECT".equalsIgnoreCase(expr) )
                                tokenQueue.add( Token.Select );
                            else if( "HAVING".equalsIgnoreCase(expr) )
                                tokenQueue.add( Token.Having );
                            else
                                tokenQueue.add(t1);
                        }
                        else if( expr.length() == 4 )
                        {
                            if( "FROM".equalsIgnoreCase(expr) )
                                tokenQueue.add( Token.From );
                            else
                                tokenQueue.add(t1);
                        }
                        else if( expr.length() == 3 )
                        {
                            if( "END".equalsIgnoreCase(expr) )
                                tokenQueue.add( Token.End );
                            else
                                tokenQueue.add(t1);
                        }
                        else
                            tokenQueue.add(t1);
                }
                else
                    tokenQueue.add(t1);
            }
        };
    }

    /** */
    private Token<?> handle( ReaderScanner.IContext t ) {

        try {

            Token<?> returnToken = null;

            if(currentState != null) {

                returnToken = currentState.apply(t);

                if(returnToken != null) {

                    currentState = null;

                    return returnToken;
                }
            }

            if(currentState == null) {

                returnToken = fromChar(t.current());

                if(returnToken == null) {

                    if(ITokenState.isSpace(t.current()))
                        return null;

                    getState(t);

                    returnToken = currentState.apply(t);
                }
            }

            if( returnToken != null )
                currentState = null;

            return returnToken;
        }
        catch( Throwable th ) {
            throw new RuntimeException(Tags.PRODUCT_LABEL + "Error on parse statement text at line " + t.lineNum() + ", pos " + t.symbNum(), th );
        }
    }

    /** */
    private void getState( ReaderScanner.IContext t ) {

        if( t.current() == ':' && t.next() == ':' ) //for PostgreSQL
            currentState = new OperatorState();
        else if( t.current() == ':' && t.next() != '=' ) //for PL/SQL
            currentState = new NamedParameterState();
        else if (
            ( t.current() == '/' && t.next() == '*' )
            ||
            ( t.current() == '-' && t.next() == '-' )
        )
            currentState = new CommentState();

        else if( t.current() == '"' )
            currentState = new QuotesState();

        else if( t.current() == StringState.QUOTE )
            currentState = new StringState();

        else if( ITokenState.isDigit(t.current()) )
            currentState = options == 0 ? new NumberState() : new ExpressionState();

        else if( U.inChar( t.current(), OperatorState.OPERATOR_CHARS) )
            currentState = new OperatorState();

        if( currentState == null )
            currentState = new ExpressionState();
    }

    // https://docs.oracle.com/html/A90108_01/sqopr.htm
    private Token<?> fromChar( char ch ) {

        switch(ch) {
            case '~':
                return Token.UnchangedMark;
            case '?':
                return Token.IndexedParameter;
            case '(':
                return Token.ParenthesisLeft;
            case ')':
                return Token.ParenthesisRight;
            case ',':
                return Token.Comma;
            case ';':
                return Token.Semicolon;
            case '\n':
                return Token.Nl;
            case '{':
                return Token.LeftBrace;
            case '}':
                return Token.RightBrace;
//            case '\t':
//                return Token.Tab;
        }

        return null;
    }
}