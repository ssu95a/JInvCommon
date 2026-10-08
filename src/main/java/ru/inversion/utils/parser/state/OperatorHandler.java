package ru.inversion.utils.parser.state;

import ru.inversion.utils.parser.Tokenizer;
import ru.inversion.utils.parser.Token;
import ru.inversion.utils.U;

/**
 *
 * @author ssu
 */
public class OperatorHandler extends AbstractTokenHandler<Object> {

    final public static char[] OPERATOR_CHARS = {'=','!','^','<','>','|', '+', '-', '/','*','.'};

    @Override
    public Token<Object> apply(Tokenizer.IContext t) {

        Token<?> retToken = null;
        
        final char ch = t.current();
        final char cn = t.next();
        //
        switch( ch )
        {
            case ':':
            {
                switch( cn ) {
                    case ':':
                        retToken = Token.PgCastOperator;
                    break;
                    case '=':
                        retToken = Token.Assignment;
                    break;
                }
                // break inner switch.
            }
            break;
            case '|':
            {
                if( cn == '|' )
                    retToken = Token.ConcatStringsPL;
            }
            break;
            case '.':
            {
                if( cn == '.' )
                    retToken = Token.DoubleDot;
            }
            break;
            case '=':
            {
                if( cn == '>' )
                    retToken = Token.NamedNotation;
            }
            break;
            case '<':
            {
                if( cn == '>' )
                    retToken = new Token<>( Token.TypeEnum.COMPARISON_OPERATOR, "<>" );
            }
            break;
            case '!':
            {
                if( cn == '=' )
                    retToken = new Token<>( Token.TypeEnum.COMPARISON_OPERATOR, "!=" );
            }
            break;
        }

        if( retToken != null )
        {
            t.shift();
        }
        else
        {
            if( U.inChar( ch, '+', '-', '*', '/', '%', '^' ) ) {
                if( ch == '-' )
                    retToken = Token.Minus;
                else if ( ch == '+' )
                    retToken = Token.Plus;
                else
                    retToken = new Token<>( Token.TypeEnum.ARITHMETIC_OPERATOR, ch );
            }
            else if( U.inChar( ch, '>', '<', '=') ) {
                if( ch == '=' )
                    retToken = Token.Equals;
                else
                    retToken = new Token<>(Token.TypeEnum.COMPARISON_OPERATOR, ch );
            }
            else if( ch == '.' )
                retToken = Token.Dot;
//            else if( ch == ':' )
//                retToken = NewToken.Colon;
        }

        if( retToken != null )
            t.shift();

        return (Token< Object >)retToken;
    }

    @Override
    public String getName() {
        return "OperatorState";
    }

    @Override
    public boolean matches( Tokenizer.IContext ctx ) {

        return U.inChar( ctx.current(), OperatorHandler.OPERATOR_CHARS )
               ||
               ctx.current() == ':' && ctx.next() == ':'
               ||
               ctx.current() == ':' && ctx.next() == '=';

    }
}
