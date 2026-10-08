package ru.inversion.utils.parser.sql.lexer.recognizer;

import ru.inversion.utils.parser.lexer.TokenMatch;
import ru.inversion.utils.parser.lexer.TokenRecognizer;
import ru.inversion.utils.parser.sql.lexer.SqlTokenKind;
import ru.inversion.utils.parser.text.SourceText;

import java.util.Objects;

/**
 * Распознаёт SQL-идентификатор в двойных кавычках.
 * <p>
 * Двойная кавычка внутри идентификатора экранируется удвоением:
 *
 *   "Column""Name"
 *
 * Незакрытый идентификатор продолжается до конца исходного текста.
 */
public final class QuotedIdentifierRecognizer implements TokenRecognizer<SqlTokenKind> {

    @Override
    public TokenMatch<SqlTokenKind> match( SourceText source, int offset )
    {
        Objects.requireNonNull(source, "source");

        if( source.get(offset) != '"')
            return null;

        int end = offset + 1;

        while( true )
        {
            int character = source.get(end);

            if( character == SourceText.EOF)
                return new TokenMatch<>( SqlTokenKind.QUOTED_IDENTIFIER, source.length() );

            if (character == '"')
            {
                if( source.get(end + 1) == '"')
                {
                    end += 2;
                    continue;
                }

                return new TokenMatch<>( SqlTokenKind.QUOTED_IDENTIFIER, end + 1 );
            }

            end++;
        }
    }
}