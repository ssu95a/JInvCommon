package ru.inversion.parser.token;

import ru.inversion.utils.ReaderScanner;
import ru.inversion.utils.U;

/**
 *
 * @author ssu
 */
public class OperatorState implements ITokenState {

    final public static char[] OPERATOR_CHARS = {'=','!','^','<','>','|', '+', '-', '/','*',':','.'};
    
    private char chrs[];
    
    @Override
    public Token apply( ReaderScanner.IContext t) {

        char ch = t.current();
        
        if( chrs != null ) {
            
            chrs[1] = ch;

            if( chrs[0] == ':' && chrs[1] == ':' )
                return Token.PgCastOperator;
            if( chrs[0] == '|' && chrs[1] == '|' )
                return new Token( Token.TypeEnum.CONCAT_STRINGS_OPERATOR, String.valueOf(chrs) );
            if( chrs[0] == '.' && chrs[1] == '.' )
                return new Token( Token.TypeEnum.DOUBLE_DOT, String.valueOf(chrs) );
            if( chrs[0] == '=' && chrs[1] == '>' )
                return Token.NamedNotation;
            if( chrs[0] == ':' && chrs[1] == '=' )
                return Token.Assignment;

            return new Token( Token.TypeEnum.COMPARISON_OPERATOR, String.valueOf(chrs) );
        }
        
        if( U.inChar( ch, '+', '-', '*', '/' ) )
            return new Token( Token.TypeEnum.ARITHMETIC_OPERATOR, ch );
        
        if( ch == '=' && t.next() != '>' )
            return new Token(Token.TypeEnum.COMPARISON_OPERATOR, ch );

        if( ( ch == '>' || ch == '<' ) && U.notInChar( t.next(), '<', '>', '=' ) )
            return new Token(Token.TypeEnum.COMPARISON_OPERATOR, ch );

        if( ch == '.' && t.next() != '.' )
            return Token.Dot; //new Token(Token.TypeEnum.DOT, ch );

        chrs    = new char[2];
        chrs[0] = ch;
        
        return null;
    }
}
