package ru.inversion.utils.parser.sql.lexer.recognizer;

import ru.inversion.utils.parser.lexer.TokenMatch;
import ru.inversion.utils.parser.lexer.TokenRecognizer;
import ru.inversion.utils.parser.sql.lexer.SqlTokenKind;
import ru.inversion.utils.parser.text.SourceText;

import java.util.Objects;

/**
 * Распознаёт строковый SQL-литерал в одинарных кавычках.
 * <p>
 * Одинарная кавычка внутри строки экранируется удвоением:
 *
 *   'John''s car'
 *
 * Незакрытый литерал продолжается до конца исходного текста.
 */
public final class StringLiteralRecognizer implements TokenRecognizer<SqlTokenKind> {

    @Override
    public TokenMatch<SqlTokenKind> match( SourceText source, int offset )
    {
        Objects.requireNonNull(source, "source");

        if( source.get(offset) != '\'' )
            return null;

        int end = offset + 1;

        while(true)
        {
            int character = source.get(end);

            if( character == SourceText.EOF)
                return new TokenMatch<>( SqlTokenKind.STRING_LITERAL, source.length() );

            if (character == '\'')
            {
                if( source.get(end + 1) == '\'' )
                {
                    end += 2;
                    continue;
                }
                return new TokenMatch<>( SqlTokenKind.STRING_LITERAL, end + 1 );
            }

            end++;
        }
    }
}