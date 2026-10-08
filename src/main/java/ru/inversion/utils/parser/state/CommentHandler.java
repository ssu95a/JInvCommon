package ru.inversion.utils.parser.state;


import ru.inversion.utils.parser.Tokenizer;
import ru.inversion.utils.parser.Token;
import ru.inversion.utils.U;

/**
 *
 * @author ssu @
 */
public class CommentHandler extends AbstractTokenHandler<String>
{
	private static final char[] beginnings =  new char[] { '-', '-', '/','*', '/', '/' };

	private static boolean isCommentBegin( char ch1, char ch2 )
	{
		for( int i = 0; i < beginnings.length - 1; i += 2 )
			if( ch1 == beginnings[i] && ch2  == beginnings[i+1] )
				return true;

		return false;
	}

	public CommentHandler( ) {
	}

    @Override
    public Token<String> apply(Tokenizer.IContext t ) {

		StringBuilder commentText = null;
		int		  	  commentMode = 0;

		for( int i = 0; i < beginnings.length; i++ )
		{
			if (
				t.current() == beginnings[i]
				&&
				t.next()  == beginnings[++i]
			)
			{
				commentMode = i;
				commentText = new StringBuilder();
				break;
			}
		}

		if( commentText == null )
			return null;

		if( U.inInt( commentMode, 1, 5, 7 ) )
		{
			t.shift( );

			while( t.shift( ) )
			{
				if( t.current() == '\n' )
					break;

				commentText .append( t.current() );
			}
		}
		else if( commentMode == 3 )
		{
			int level = 0;

			do {

				if( t.current() == '/' && t.next() == '*' ) {

					level++;

					if( level == 1 )
						t.shift();
				}
				else if( t.current() == '*' && t.next() == '/' ) {
					level --;
					if( level == 0 ) {
						t.shift();
						t.shift();
					}
				}
				else commentText .append( t.current() );

			} while( level > 0 && t.shift() );
		}

		String comment = commentText.toString().trim();
		// hint
		if( commentMode == 3 && comment.charAt(0) == '+' )
			return new Token<>( Token.TypeEnum.QUERY_HINT, "/* " + comment + " */" );

        return new Token<>( Token.TypeEnum.COMMENT, comment );
	}

	@Override
	public String getName() {
		return "CommentState";
	}

	/** */
	@Override
	public boolean matches( Tokenizer.IContext ctx ) {
		return isCommentBegin( ctx.current(), ctx.next() );
	}
}
