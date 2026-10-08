package ru.inversion.parser.token;

import ru.inversion.dataset.parser.SQLItem;
import ru.inversion.utils.ReaderScanner;
import ru.inversion.utils.converter.TypeConverter;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 *
 * @author ssu
 */
public class NumberState implements ITokenState {

    private StringBuilder numberText = null;
	private boolean		  isDecimal	 = false;

    public NumberState() {
    
    }

    private Token<?> makeToken()
    {
        if( isDecimal )
            return new Token<>( Token.TypeEnum.DECIMAL, new BigDecimal( numberText.toString() ) );
        else
        {
            if( numberText.length() <= 19 )
                return new Token<>( Token.TypeEnum.INTEGER, TypeConverter.convert( numberText.toString(), Number.class) ) ;

                //return new Token<>( Token.TypeEnum.INTEGER, Long.parseLong(numberText.toString() ) );

            return new Token<>( Token.TypeEnum.INTEGER, new BigInteger( numberText.toString() ) );
        }
    }

    @Override
    public Token apply( ReaderScanner.IContext t )
    {
        if( t.current() == '.' )
        {
            if( isDecimal )
            {
                if( t.previous() == '.' ) {
                    numberText.append( t.current() );
                    return new Token ( Token.TypeEnum.EXPRESSION, numberText.toString() );
                }
                else
                    throw new NumberFormatException("NumberState: error digit format, add dot" );
            }

            isDecimal = true;

            numberText.append( t.current() );

            return null;
		}
		else
		{
            if( numberText == null )
                numberText = new StringBuilder();

            numberText.append( t.current() );
		}
		
		if( ITokenState.isAlpha( t.next() ) )
			throw new NumberFormatException("NumberState: error digit format");
		
		if( !ITokenState.isDigit( t.next() ) && t.next() != '.' )
            return makeToken();

        return null;
    }
    
}
