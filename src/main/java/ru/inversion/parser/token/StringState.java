package ru.inversion.parser.token;

import ru.inversion.utils.ReaderScanner;
import ru.inversion.utils.S;

/**
 *
 * @author ssu @
 */
public class StringState implements ITokenState {

    public final static char QUOTE = "'".charAt(0);
    
	private boolean		  begin    = false, 
                          eof      = false, 
                          skipNext = false;
    
	private StringBuilder text = null;
	
	public StringState(  ) {
	}

    @Override
    public Token apply( ReaderScanner.IContext t ) {

		if( t.current() == QUOTE ) {
            
            if( !begin )
                 begin = true;
            
            else {

                if( skipNext )
                    skipNext = false;
                
                else {
                    
                    if( t.next() == QUOTE )
                        skipNext = true;
                    else
                        eof = true;
                }
            }//end if
		}
		
        if( text == null )
            text = new StringBuilder( );

        text.append( t.current() );

        if( eof )
            return produceItem( );

		return null;
	}
    
	/** */
	private Token produceItem( ) {
        
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
        
		return new Token( Token.TypeEnum.STRING, s );
	}

}
