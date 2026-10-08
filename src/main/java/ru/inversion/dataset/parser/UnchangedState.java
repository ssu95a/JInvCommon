package ru.inversion.dataset.parser;


/**
 *
 * @author ssu
 */
public class UnchangedState extends AbstractState {

    public final static char DELIM = '~';
    
	private StringBuilder text      = null;
    private boolean       eof       = false;
    private boolean       insideStr = false;

    @Override
    protected void clear() {
        text = null;
        eof  = false;
    }

    @Override
    protected boolean doProcess( ReaderScaner.IContext context ) {
        
        if( eof )
            return false;

        if( context.current() == StringState.quote )
            insideStr = !insideStr;

        if( context.current() == DELIM && !insideStr ) {
            if( text == null )
                text = new StringBuilder();
            else
                eof = true;
        }
        else
            text.append( context.current() );
        
        return true;
    }

    @Override
    protected SQLItem produceItem() {
        
        if( text == null || text.length() == 0 )
            return AbstractState.EMPTY_ITEM;
                    
        return new SQLItem( text.toString(), SQLItem.ItemTypeEnum.UNCHANGED );
    }
    
}
