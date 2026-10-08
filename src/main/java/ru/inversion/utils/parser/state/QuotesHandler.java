package ru.inversion.utils.parser.state;


import ru.inversion.utils.parser.Tokenizer;
import ru.inversion.utils.parser.Token;

/**
 *
 * @author ssu
 */
public class QuotesHandler extends AbstractTokenHandler<String> {

    /** */
    public QuotesHandler( ) {
    }
    
    /** */
    @Override
    public Token<String> apply(Tokenizer.IContext t ) {

        final StringBuilder text = new StringBuilder();

        while( t.shift() && t.current() != '"' ) {
               text.append( t.current() );
        }

        t.shift();

        return new Token<>( Token.TypeEnum.TEXT_IN_QUOTES, text.toString() );
    }

    @Override
    public String getName() {
        return "QuotesState";
    }

    @Override
    public boolean matches( Tokenizer.IContext ctx ) {
        return ctx.current() == '"';
    }
}
