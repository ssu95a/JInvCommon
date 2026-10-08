package ru.inversion.utils.parser.state;

import ru.inversion.utils.converter.TypeConverter;
import ru.inversion.utils.parser.ITokenHandler;
import ru.inversion.utils.parser.Token;
import ru.inversion.utils.parser.Tokenizer;

/**
 *
 * @author ssu @
 */
public class PgPositionParameterHandler extends AbstractTokenHandler<Integer> {

	public PgPositionParameterHandler( ) {
	}
	
    @Override
    public Token<Integer> apply(Tokenizer.IContext t  ) {

		final StringBuilder position = new StringBuilder();

        t.shift(); // пропускаем '$'

        while( ITokenHandler.isDigit(t.current()) )
        {
            position.append(t.current());
            t.shift();
        }

        return new Token<>( Token.TypeEnum.PG_POSITIONAL_PARAMETER, TypeConverter.convert( position.toString(), Integer.class ) );
	}

	@Override
	public String getName() {
		return "PgPositionParameterHandler";
	}

	@Override
	public boolean matches( Tokenizer.IContext ctx ) {
		return ctx.current() == '$' && ITokenHandler.isDigit( ctx.next() ) ;
	}
}
