package ru.inversion.parser.token;

import ru.inversion.utils.ReaderScanner;
import ru.inversion.utils.S;

/**
 *
 * @author ssu @
 */
public class NamedParameterState implements ITokenState {

	private StringBuilder parameterName = null;
	
	public NamedParameterState( ) {
	
	}
	
    @Override
    public Token apply( ReaderScanner.IContext t ) {

		if( parameterName == null && t.current() == ':' )
			;
		else {	
			
			if( parameterName == null ) 
				parameterName = new StringBuilder();
			
			parameterName.append( t.current() );

            if( !ITokenState.isAlphaDigit( t.next() ) && t.next() != '.' )
                return new Token( Token.TypeEnum.NAMED_PARAMETER, parameterName == null ? S.EMPTY_STRING : parameterName.toString() );
		}
		return null;
	}

}
