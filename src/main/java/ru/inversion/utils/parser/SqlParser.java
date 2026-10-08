package ru.inversion.utils.parser;

import ru.inversion.utils.Pair;

import java.util.Iterator;
import java.util.NoSuchElementException;

import static ru.inversion.utils.parser.Token.*;
import static ru.inversion.utils.parser.Token.TypeEnum.*;

/** */
class SqlParser extends PLxxSqlParser {

    /** */
    private int level = 0;

    /** */
    public SqlParser( Iterator<Token<?>> base ) {
        super( base  );
    }

    /** */
    protected Token<?> nextToken( ) {

        if( !hasNext() )
            throw new NoSuchElementException();

        if( lastToken == Select )
            return new Token<>( MARK_COLUMN, "" );
        else if( lastToken == Where )
            return new Token<>( ADDITIONAL_PREDICATE, "" );

        Token<?> result = tryNamedParameter();
        if( result != null )
            return result;

        final int nshift;

        if( level == 0 )
        {
            final Pair<Token<String>,Integer> pair = MultiWordKeyword.get( buffer );

            if( pair != null )
            {
                result = pair.first;
                nshift = pair.second;
            }
            else
            {
                result = buffer[0];
                nshift = 1;
            }
        }
        else
        {
            result = buffer[0];
            nshift  = 1;
        }

        shift( nshift );

        return result;
    }

    /** */
    @Override
    public Token< ? > next() {

        final Token<?> n = nextToken();

        if( n == ParenthesisLeft )
            level++;
        else if( n == ParenthesisRight )
            level--;

        lastToken = n;

        return n;
    }
}
