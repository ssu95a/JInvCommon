package ru.inversion.utils.parser.sql.lexer.recognizer;

import ru.inversion.utils.parser.lexer.TokenMatch;
import ru.inversion.utils.parser.lexer.TokenRecognizer;
import ru.inversion.utils.parser.sql.lexer.SqlTokenKind;
import ru.inversion.utils.parser.text.SourceText;

import java.util.Objects;

/**
 * Распознаёт позиционный JDBC-параметр:
 *
 *   ?
 */
public final class JdbcParameterRecognizer implements TokenRecognizer<SqlTokenKind> {

    @Override
    public TokenMatch<SqlTokenKind> match( SourceText source, int offset )
    {
        Objects.requireNonNull( source, "source" );

        if( source.get(offset) != '?' )
            return null;

        return new TokenMatch<>( SqlTokenKind.JDBC_PARAMETER, offset + 1 );
    }
}