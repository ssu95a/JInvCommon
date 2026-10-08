package ru.inversion.utils.parser.state;

import ru.inversion.utils.parser.Tokenizer;
import ru.inversion.utils.parser.Token;

/**
 *
 * @author ssu @
 */
public class StringHandler extends AbstractTokenHandler<String> {

    public final static char APSTRF = '\'';

	public StringHandler(  ) {
	}

    @Override
    public Token<String> apply(Tokenizer.IContext t ) {

        final StringBuilder text = new StringBuilder( );

        while( t.shift() ) {

            if( t.current() == APSTRF )
            {
                if( t.next() == APSTRF )
                {
                    text.append( t.current() );
                    t.shift(); // Пропускаем следующий апостроф
                }
                else
                {
                    t.shift(); // Пропускаем последний апостроф
                    break;
                }
            }
            else
            {
                // Это любой другой символ
                text.append( t.current() );
            }
        }
		return new Token<>( Token.TypeEnum.STRING, text.toString() );
	}

    /** */
    @Override
    public String getName() {
        return "StringState";
    }

    /** */
    @Override
    public boolean matches( Tokenizer.IContext ctx ) {
        return ctx.current() == APSTRF;
    }
}
