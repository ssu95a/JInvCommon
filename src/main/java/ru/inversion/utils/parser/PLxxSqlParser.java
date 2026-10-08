package ru.inversion.utils.parser;

import ru.inversion.utils.U;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;

import static ru.inversion.utils.parser.Token.*;
import static ru.inversion.utils.parser.Token.TypeEnum.*;

/** */
class PLxxSqlParser implements Iterator<Token<?>> {

    /** */
    final protected Iterator<Token<?>> base;

    /** */
    final protected Token<?>[] buffer = new Token[3];

    /** */
    protected Token<?> lastToken;

    /** */
    public PLxxSqlParser( Iterator<Token<?>> base ) {

        this.base = Objects.requireNonNull(base, "'base' is null");

        for( int i = 0; i < buffer.length; i++ ) {
             buffer[i] = base.hasNext() ? base.next() : null;
        }
    }

    /** */
    protected void shift( int count ) {
        // Сдвигаем буфер
        for( int i = 0; i < buffer.length - count; i++ )
             buffer[i] = buffer[ i + count ];
        //
        for( int i = buffer.length - count; i < buffer.length; i++ ) {
             buffer[i] = base.hasNext() ? base.next() : null;
        }
    }

    /** */
    protected Token<?> tryNamedParameter( )
    {
        if (
            U.in( typeOf( buffer[0] ), NAMED_PARAMETER, EXPRESSION )
            &&
            typeOf( buffer[1] ) == DOT
            &&
            U.in( typeOf( buffer[2] ), EXPRESSION, INTEGER )
        )
        {
            final String s = buffer[0].getValue() + "." + buffer[2].getValue();
            shift(3);
            return new Token<>( buffer[0].getType() == NAMED_PARAMETER ? NAMED_PARAMETER : EXPRESSION, s );
        }
        return null;
    }

    /** */
    protected Token<?> tryDeclaredType( )
    {
        if ( buffer[0] == ParenthesisLeft && typeOf( buffer[1] ) == INTEGER && buffer[2] == ParenthesisRight )
        {
            final String s = "(" + buffer[1].getValue() + ")";
            shift(3);
            return new Token<>( EXPRESSION, s );
        }
        return null;
    }

    /** */
    protected Token<?> nextToken() {

        if( !hasNext() )
            throw new NoSuchElementException();

        Token<?> result = U.nvl( tryNamedParameter( ), tryDeclaredType() );
        if( result != null )
            return result;

        result = buffer[0];

        shift(1);

        return result;
    }

    /** */
    @Override
    public boolean hasNext() {
        return buffer[0] != null || base.hasNext();
    }

    /** */
    public Token<?> next() {
        final Token<?> n = nextToken();
        lastToken = n;
        return n;
    }
}
