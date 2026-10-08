package ru.inversion.utils.parser.sql.lexer.recognizer;

import ru.inversion.utils.parser.lexer.TokenMatch;
import ru.inversion.utils.parser.lexer.TokenRecognizer;
import ru.inversion.utils.parser.sql.lexer.SqlTokenKind;
import ru.inversion.utils.parser.text.SourceText;

import java.util.Objects;

/**
 * Распознаёт однострочный SQL-комментарий.
 * <p>
 * Комментарий начинается с "--" и продолжается до:
 * - символа '\r';
 * - символа '\n';
 * - конца исходного текста.
 *
 * Перевод строки не входит в диапазон комментария.
 */
public final class LineCommentRecognizer implements TokenRecognizer<SqlTokenKind> {

    @Override
    public TokenMatch<SqlTokenKind> match( SourceText source, int offset )
    {
        Objects.requireNonNull(source, "source");

        if( source.get(offset) != '-' || source.get(offset + 1 ) != '-')
            return null;

        int end = offset + 2;

        while (true)
        {
            int character = source.get(end);

            if( character == SourceText.EOF || character == '\r' || character == '\n')
                break;

            end++;
        }

        return new TokenMatch<>( SqlTokenKind.LINE_COMMENT, end );
    }
}