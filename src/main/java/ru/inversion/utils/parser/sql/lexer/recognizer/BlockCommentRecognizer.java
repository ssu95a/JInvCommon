package ru.inversion.utils.parser.sql.lexer.recognizer;

import ru.inversion.utils.parser.lexer.TokenMatch;
import ru.inversion.utils.parser.lexer.TokenRecognizer;
import ru.inversion.utils.parser.sql.lexer.SqlTokenKind;
import ru.inversion.utils.parser.text.SourceText;

import java.util.Objects;

/**
 * Распознаёт блочный SQL-комментарий.
 *
 * Комментарий начинается с "/*" и заканчивается первым "* /".
        * Незакрытый комментарий продолжается до конца исходного текста.
 */
public final class BlockCommentRecognizer implements TokenRecognizer<SqlTokenKind> {

    @Override
    public TokenMatch<SqlTokenKind> match( SourceText source, int offset )
    {
        Objects.requireNonNull(source, "source");

        if( source.get(offset) != '/' || source.get(offset + 1) != '*')
            return null;

        int end = offset + 2;

        while( true )
        {
            int character = source.get(end);

            if( character == SourceText.EOF )
                return new TokenMatch<>( SqlTokenKind.BLOCK_COMMENT, source.length() );

            if( character == '*' && source.get(end + 1) == '/')
                return new TokenMatch<>( SqlTokenKind.BLOCK_COMMENT, end + 2 );

            end++;
        }
    }
}