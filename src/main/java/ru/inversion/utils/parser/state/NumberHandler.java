package ru.inversion.utils.parser.state;

import ru.inversion.utils.parser.Tokenizer;
import ru.inversion.utils.parser.ITokenHandler;
import ru.inversion.utils.parser.Token;
import ru.inversion.utils.converter.TypeConverter;

/**
 *
 * @author ssu
 */
public class NumberHandler extends AbstractTokenHandler<Object> {

    /** */
    public NumberHandler() {
    }

    /** */
    @Override
    public Token<Object> apply(Tokenizer.IContext t )
    {
        final StringBuilder numberText = new StringBuilder();

        do {

            if( ITokenHandler.isAlpha( t.next() ) )
                throw new NumberFormatException("NumberState: error digit format");

            numberText.append( t.current() );

        } while( t.shift() && ITokenHandler.isDigit(t.current()) );

        if( numberText.length() > 1 && numberText.charAt(0) == '0' )
            return new Token<>( Token.TypeEnum.INTEGER_STRING, numberText.toString() );

        return new Token<>( Token.TypeEnum.INTEGER, TypeConverter.convert( numberText.toString(), Number.class ));
    }

    @Override
    public String getName() {
        return "NumberState";
    }

    @Override
    public boolean matches( Tokenizer.IContext ctx ) {
        return ITokenHandler.isDigit( ctx.current() );
    }
}
