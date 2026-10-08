package ru.inversion.utils.parser.state;

import ru.inversion.utils.parser.Tokenizer;
import ru.inversion.utils.parser.ITokenHandler;
import ru.inversion.utils.parser.Token;
import ru.inversion.utils.U;

import java.util.Map;
import java.util.TreeMap;

import static ru.inversion.utils.parser.Token.*;


/**
 *
 * @author ssu @
 */
public class ExpressionHandler extends AbstractTokenHandler<String> {

    final private static Map<String, Token<String>> keyMap;

    static {
        keyMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        keyMap.put( "null",   Null  );
        keyMap.put( "on",     On    );
        keyMap.put( "off",    Off   );
        keyMap.put( "end",    End   );
        keyMap.put( "begin",  Begin );
        keyMap.put( "format", Format);
        keyMap.put( "string", Str   );
    }

	public ExpressionHandler( ) {
	}

    @Override
    public Token<String> apply(Tokenizer.IContext t ) {

        final StringBuilder text = new StringBuilder();

        do {
            text.append( t.current() );
        } while( t.shift() && ITokenHandler.isAlphaDigit( t.current()) );

        final String s = text.toString();

        return U.nvl (
            keyMap.get(s.trim()),
            new Token<>( Token.TypeEnum.EXPRESSION, s )
        );
    }

    @Override
    public String getName() {
        return "ExpressionState";
    }

    @Override
    public boolean matches( Tokenizer.IContext ctx ) {
        return true;
    }

    @Override
    public int getUseCount() {
        return 0;
    }
}
