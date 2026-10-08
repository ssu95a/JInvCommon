package ru.inversion.parser.token;

import ru.inversion.utils.ReaderScanner;
import ru.inversion.utils.S;

/**
 *
 * @author ssu @
 */
public class CommentState implements ITokenState
{
	private boolean		  multiLine	  = false;
	private StringBuilder commentText = null;

	public CommentState( ) {
	}

    @Override
    public Token<?> apply( ReaderScanner.IContext t ) {

		if( commentText == null ) {
			multiLine = ( t.current() == '/' && t.next() == '*' );
			commentText = new StringBuilder( ); 
		}

		commentText.append( t.current() );

		if( multiLine ) {
			
			if( t.previous() == '*' && t.current() == '/' )
			{
				if( commentText.length() > 3 && commentText.charAt(2) == '+' )
					return new Token( Token.TypeEnum.OPTIMIZER_HINT, commentText.toString() );
				else
					return new Token( Token.TypeEnum.COMMENT, commentText.toString() );
			}
		}
		else 
		{
			if( t.current() == '\n' )
				return new Token( Token.TypeEnum.COMMENT, commentText.toString() );
		}
		
        return null;
	}
}
