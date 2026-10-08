package ru.inversion.dataset.parser;


import ru.inversion.utils.Tags;

/**
 *
 * @author ssu @
 */
public abstract class AbstractState {
	
	public final static SQLItem EMPTY_ITEM = new SQLItem( "", null );
	
	protected abstract void	clear( );
	protected abstract boolean doProcess  ( ReaderScaner.IContext context );
	protected abstract SQLItem produceItem( );

    public SQLItem process( ReaderScaner.IContext context ) {

    	try {

			if(context.current() == 0 || !doProcess(context)) {

				SQLItem item = produceItem();
				clear();
				return item;
			}

			return null;

		}
    	catch( Throwable th ) {
    		throw new RuntimeException(Tags.PRODUCT_LABEL + "process item at, " + context , th );
		}
	} 

	/** */
	private static final AbstractState states[] = {
		new ExpressionState(true),
		new NumberState(),
		new StringState(),
		new NamedParameterState(),
		new CommentState(),
		new ExpressionState(false),
        null,
        new UnchangedState(),
        null,
        new QuotesState()
	};
	
    /** */
	public static AbstractState getState( SQLItem.ItemTypeEnum itemType ) {
		//return states[itemType.ordinal()];
        switch( itemType ) {
            case COMMENT:
                return new CommentState();
            case TEXT_EXPRESSION:
                return new ExpressionState(true);
            case INTEGER:
                return new NumberState();
            case STRING:
                return new StringState();
            case NAMED_PARAMETER:
                return new NamedParameterState();
            case UNCHANGED:
                return new UnchangedState();
            case QUOTES:
                return new QuotesState();
        }
        throw new IllegalArgumentException("Bad SQLItem.ItemTypeEnum: " + itemType );
	}
	
    /** */
	public static AbstractState getExpressionState( boolean extractConstants ) {
		//return extractConstants ? states[0] : states[5];
        return new ExpressionState(extractConstants);
	}
    
	/** */
    public static boolean isDigit( char ch ) {
		return '0' <= ch && ch <= '9';
    }
	
    /** */
    public static boolean isSpace( char ch ) {
		if( ch <= 127)
			return ch ==  32 || ch == 9 || ch == 12 || ch == 11 || ch == '\n' || ch == '\r';
		else
			return ch == 160 || Character.getType( (char)ch ) == Character.SPACE_SEPARATOR;
    }
    
    /** */
    public static boolean isAlphaDigit( char ch ) {
		return isAlpha(ch) || isDigit( ch );
    }
    
    /** буква ли */
    public static boolean isAlpha( char ch ) {
		switch( ch ) {
			case '_':
			case '$':
			case '#':
			return true;
		}
		return ( ch <= 'Z' ? 'A' <= ch : ( 'a' <= ch && ch <= 'z' ) );
    }	
}
