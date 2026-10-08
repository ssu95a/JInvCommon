package ru.inversion.dataset.parser;

/**
 *
 * @author ssu @
 */
public class CommentState extends AbstractState
{
	private boolean		  eof		  = false;
	private boolean		  multiLine	  = false;
	private StringBuilder commentText = null;
	
	public CommentState( ) {
	}

	@Override
	protected void clear( ) {
		commentText = null;
		eof			= false;
	}
	/** */
	@Override
	protected boolean doProcess( ReaderScaner.IContext context ) 
	{
		if( eof )
			return false;
		
		if( commentText == null ) {
			multiLine = ( context.current() == '/' && context.next() == '*' );
			commentText = new StringBuilder( ); 
		}
		
		commentText.append( context.current() );
		
		if( multiLine ) {
			
			if( context.previous() == '*' && context.current() == '/' )
				eof = true;
		}
		else 
		{
			if( context.current() == '\n' )
				eof = true;
		}
		return true;
	}
	/** */
	@Override
	protected SQLItem produceItem( ) {
        
        if( commentText == null || commentText.length() == 0 )
            return AbstractState.EMPTY_ITEM;
        
        String text = commentText.toString();
        
		return new SQLItem( text, text.charAt(0) == '+' ? SQLItem.ItemTypeEnum.HINT : SQLItem.ItemTypeEnum.COMMENT );
	}
}
