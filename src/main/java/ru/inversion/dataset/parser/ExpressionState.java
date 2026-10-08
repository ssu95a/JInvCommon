package ru.inversion.dataset.parser;


/**
 *
 * @author ssu @
 */
public class ExpressionState extends AbstractState {
	
	private StringBuilder   expressionText = null;
	private char            lastChar = 0;
    private boolean         extractConstants = true;
	
	public ExpressionState( boolean extractConstants ) {
        this.extractConstants = extractConstants;
	}

	@Override
	protected void clear( ) {
		expressionText = null;
		lastChar	   = 0;
	}

	private boolean hasMinus = false;

	/** */
	@Override
	protected boolean doProcess( ReaderScaner.IContext context ) 
	{
		if( context.current() == '-' )
			hasMinus = true;
	
        if( extractConstants )
        {
            if( 
                ( context.current() == '-' && context.next() == '-' )
                ||
                ( context.current() == '/' && context.next() == '*' )
                ||	
                ( context.current() == '?' )
                ||	
                ( context.current() == '~' )
                ||
                ( context.current() == '"' )
                ||	
                ( context.current() == ':' && context.next() != '=' )
                ||	
                ( context.current() == "'".charAt(0) )
                ||
                ( !hasMinus && !isAlphaDigit(context.previous() ) && isDigit( context.current() ) )
            )
                return false;
        }
        else {
            
            if( 
                ( context.current() == '-' && context.next() == '-' )
                ||
                ( context.current() == '"' )
                ||	
                ( context.current() == '/' && context.next() == '*' )
                ||	
                ( context.current() == '?' )
                ||	
                ( context.current() == '~' )
                ||	
                ( context.current() == ':' && context.next() != '=' )
                ||	
                ( context.current() == "'".charAt(0) )
            )
                return false;
        }
        
		if( isSpace( context.current() ) ) {
			
			if( lastChar == context.current() )
				return true;
			
			lastChar = context.current();
		}
		else
			lastChar = 0;
		
		if( expressionText == null )
			expressionText = new StringBuilder();
		
		expressionText.append( context.current() );
		
		return true;
	}
	/** */
	@Override
	protected SQLItem produceItem( ) {
		
		if( expressionText == null )
			return null;
		return new SQLItem( expressionText.toString(), SQLItem.ItemTypeEnum.TEXT_EXPRESSION );
	}

}
