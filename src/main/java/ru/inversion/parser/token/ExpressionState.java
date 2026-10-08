package ru.inversion.parser.token;

import ru.inversion.utils.ReaderScanner;


/**
 *
 * @author ssu @
 */
public class ExpressionState implements ITokenState {
	
	private StringBuilder text = new StringBuilder();
	
	public ExpressionState( ) {
	}

    @Override
    public Token apply( ReaderScanner.IContext t ) {

        text.append( t.current() );
        
        if( !ITokenState.isAlphaDigit( t.next() )/* && t.next() != '.'*/ )
            return new Token( Token.TypeEnum.EXPRESSION, text.toString() );
        
        return null;
    }

}
