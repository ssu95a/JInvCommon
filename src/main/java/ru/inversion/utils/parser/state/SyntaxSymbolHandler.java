package ru.inversion.utils.parser.state;

import ru.inversion.utils.parser.Tokenizer;
import ru.inversion.utils.parser.Token;
import ru.inversion.utils.U;

public class SyntaxSymbolHandler extends AbstractTokenHandler<Character> {

    final public static char[] SYMB_CHARS = {'~','?','(',')',',',';', '\n', '{', '}', '#' };

    private Token< Character > fromChar(char ch )
    {
        switch( ch ) {
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
            case '#':
                return Token.Sharp;
            default:
                return null;
            //            case '\t':
            //                return Token.Tab;
        }
    }

    @Override
    public Token<Character> apply(Tokenizer.IContext t ) {

        final Token< Character > retToken = fromChar( t.current() );

        if( retToken != null )
            t.shift();

        return retToken;
    }

    @Override
    public String getName() {
        return "SymbState";
    }

    @Override
    public boolean matches( Tokenizer.IContext ctx ) {
        return U.inChar( ctx.current(), SyntaxSymbolHandler.SYMB_CHARS );
    }
}
