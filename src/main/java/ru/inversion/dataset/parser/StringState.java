package ru.inversion.dataset.parser;

import ru.inversion.utils.S;

/**
 *
 * @author ssu @
 */
public class StringState extends AbstractState {

    public final static char quote = "'".charAt(0);
    
	private boolean		  begin= false, eof = false, skipNext = false;
	private StringBuilder text = null;
	
	public StringState(  ) {
	}

	@Override
	protected void clear( ) {
		text = null;
		eof	 = false;
        begin= false;
	}
	/** */
	@Override
	protected boolean doProcess( ReaderScaner.IContext context ) 
	{
        if( eof )
			return false;
        
		if( context.current() == quote ) {
            
            if( !begin )
                 begin = true;
            
            else {

                if( skipNext )
                    skipNext = false;
                
                else {
                    
                    if( context.next() == quote )
                        skipNext = true;
                    else
                        eof = true;
                }
            }//end if
		}
		
        if( text == null )
            text = new StringBuilder( );

        text.append( context.current() );
        
		return true;
	}
	/** */
	@Override
	protected SQLItem produceItem( ) {
        
        String s = null;
        
        if( text == null || text.length() == 0 )
            s = S.EMPTY_STRING;
        else 
        {
            text.deleteCharAt( 0 );
            text.deleteCharAt( text.length() - 1 );
            
            s = text.toString();
            
            s = s.replace( "''", "'" );
        }
        
		return new SQLItem( s, SQLItem.ItemTypeEnum.STRING );
	}

}
