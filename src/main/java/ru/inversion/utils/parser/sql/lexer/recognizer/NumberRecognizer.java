package ru.inversion.utils.parser.sql.lexer.recognizer;


import ru.inversion.utils.parser.lexer.TokenMatch;
import ru.inversion.utils.parser.lexer.TokenRecognizer;
import ru.inversion.utils.parser.sql.lexer.SqlTokenKind;
import ru.inversion.utils.parser.text.SourceText;

import java.util.Objects;

/**
 * Распознаёт целые и десятичные числовые литералы.
 *
 * Поддерживаемые формы:
 *   123
 *   123.45
 *   123.
 *   .45
 *
 * Знак числа не является частью литерала.
 */
public final class NumberRecognizer implements TokenRecognizer<SqlTokenKind> {

    @Override
    public TokenMatch<SqlTokenKind> match( SourceText source, int offset )
    {
        Objects.requireNonNull(source, "source");

        int end = offset;

        while( isDigit(source.get(end)) )
        {
            end++;
        }

        boolean hasIntegerPart = end > offset;

        if( source.get(end) == '.')
        {
            /*
             * Одиночная точка числом не является.
             *
             * Для формы без целой части после точки
             * обязательно должна быть цифра.
             */
            if (!hasIntegerPart && !isDigit(source.get(end + 1))) {
                return null;
            }

            /*
             * Не поглощаем первую точку в последовательности "..".
             * Например:
             *
             *   123..45
             *
             * Первый токен будет INTEGER_LITERAL "123".
             */
            if( source.get(end + 1) == '.')
            {
                if( !hasIntegerPart )
                    return null;

                return new TokenMatch<>( SqlTokenKind.INTEGER_LITERAL, end);
            }

            end++;

            while (isDigit(source.get(end))) {
                end++;
            }

            return new TokenMatch<>( SqlTokenKind.DECIMAL_LITERAL, end );
        }

        if( !hasIntegerPart )
            return null;

        return new TokenMatch<>( SqlTokenKind.INTEGER_LITERAL, end );
    }
}