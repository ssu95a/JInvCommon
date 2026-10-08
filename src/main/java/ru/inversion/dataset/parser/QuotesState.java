package ru.inversion.dataset.parser;

/**
 *
 * @author ssu
 */
public class QuotesState extends AbstractState {

	private StringBuilder expressionText = null;
    private boolean       eof            = false;
    
    @Override
    protected void clear() {
        expressionText = null;
        eof            = false;
    }

    @Override
    protected boolean doProcess( ReaderScaner.IContext context ) {

        if( eof )
            return false;
        
        if( expressionText == null )
			expressionText = new StringBuilder();
        else
            if( context.current() == '"' )
                eof = true;
        
		expressionText.append( context.current() );
        
		return true;
    }

    @Override
    protected SQLItem produceItem() {

        if( expressionText == null )
			return null;
        
		return new SQLItem( expressionText.toString(), SQLItem.ItemTypeEnum.TEXT_EXPRESSION );
    }
}
