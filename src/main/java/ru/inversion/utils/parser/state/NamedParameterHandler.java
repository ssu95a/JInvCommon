package ru.inversion.utils.parser.state;

import ru.inversion.utils.parser.Tokenizer;
import ru.inversion.utils.parser.ITokenHandler;
import ru.inversion.utils.parser.Token;

/**
 *
 * @author ssu @
 */
public class NamedParameterHandler extends AbstractTokenHandler<String> {

	public NamedParameterHandler( ) {
	
	}
	
    @Override
    public Token<String> apply(Tokenizer.IContext t  ) {

		final StringBuilder name = new StringBuilder();

		while( t.shift() && ( ITokenHandler.isAlphaDigit( t.current() ) ) ) {
			   name.append( t.current() );
		}
		return new Token<>( Token.TypeEnum.NAMED_PARAMETER, name.toString() );
	}

	@Override
	public String getName() {
		return "NamedParameterState";
	}

	@Override
	public boolean matches( Tokenizer.IContext ctx ) {
		return ctx.current() == ':' && ctx.next() != '=';
	}
}
