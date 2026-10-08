package ru.inversion.parser.token;

import java.util.function.Function;
import ru.inversion.utils.ReaderScanner;

/**
 *
 * @author ssu
 */
public interface ITokenState extends Function<ReaderScanner.IContext, Token<?>>{
	/** */
    public static boolean isDigit( char ch ) {
		return '0' <= ch && ch <= '9';
    }

	/** */
    public static boolean isSpace( char ch ) {

		if( ch <= 127)
			return ch ==  32 || ch == 9 || ch == 12 || ch == 11 || ch == '\n' || ch == '\r';
		else
			return ch == 160 || Character.getType( ch ) == Character.SPACE_SEPARATOR;
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
