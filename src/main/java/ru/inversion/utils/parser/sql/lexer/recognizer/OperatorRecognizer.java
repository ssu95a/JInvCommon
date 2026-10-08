package ru.inversion.utils.parser.sql.lexer.recognizer;

import ru.inversion.utils.parser.lexer.TokenMatch;
import ru.inversion.utils.parser.lexer.TokenRecognizer;
import ru.inversion.utils.parser.sql.lexer.SqlTokenKind;
import ru.inversion.utils.parser.text.SourceText;

import java.util.Arrays;
import java.util.Objects;

/**
 * Распознаёт SQL-операторы из заданного набора.
 * <p>
 * При совпадении нескольких операторов выбирается самый длинный:
 *
 *   >    и >=   -> >=
 *   ->   и ->>  -> ->>
 */
public final class OperatorRecognizer implements TokenRecognizer<SqlTokenKind> {

    private final String[] operators;

    public OperatorRecognizer(String... operators)
    {
        Objects.requireNonNull(operators, "operators");

        if( operators.length == 0 )
            throw new IllegalArgumentException("operators is empty");

        this.operators = Arrays.copyOf( operators, operators.length );

        for( String operator : this.operators )
        {
            Objects.requireNonNull(operator, "operator");

            if( operator.isEmpty() )
                throw new IllegalArgumentException( "operator is empty" );
        }
    }

    @Override
    public TokenMatch<SqlTokenKind> match( SourceText source, int offset )
    {
        Objects.requireNonNull(source, "source");

        int bestLength = 0;

        for( String operator : operators )
        {
            if( operator.length() <= bestLength)
                continue;

            if( matches(source, offset, operator) )
                bestLength = operator.length();
        }

        if( bestLength == 0 )
            return null;

        return new TokenMatch<>( SqlTokenKind.OPERATOR, offset + bestLength );
    }

    private static boolean matches( SourceText source, int offset, String operator )
    {
        for (int index = 0; index < operator.length(); index++)
        {
            if( source.get(offset + index) != operator.charAt(index) )
                return false;
        }

        return true;
    }
}