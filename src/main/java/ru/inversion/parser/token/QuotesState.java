package ru.inversion.parser.token;

import ru.inversion.utils.ReaderScanner;

/**
 *
 * @author ssu
 */
public class QuotesState implements ITokenState {

	private StringBuilder text = null;
    
    /** */
    public QuotesState( ) {
    
    }
    
    /** */
    @Override
    public Token apply( ReaderScanner.IContext t ) {
        
        if( text == null )
			text = new StringBuilder();
        else
            if( t.current() == '"' ) {
                text.append( t.current() );
                return new Token(Token.TypeEnum.QUOTED_TEXT, text.toString());
            }

        text.append( t.current() );

		return null;
    }
}
