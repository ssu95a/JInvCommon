package ru.inversion.utils.parser.sql.lexer.recognizer;

import ru.inversion.utils.parser.lexer.TokenMatch;
import ru.inversion.utils.parser.lexer.TokenRecognizer;
import ru.inversion.utils.parser.sql.lexer.SqlTokenKind;
import ru.inversion.utils.parser.text.SourceText;

import java.util.Objects;

/**
 * Распознаёт позиционный PostgreSQL-параметр:
 *
 *   $1
 *   $25
 */
public final class PostgresPositionalParameterRecognizer implements TokenRecognizer<SqlTokenKind> {

    @Override
    public TokenMatch<SqlTokenKind> match( SourceText source, int offset )
    {
        Objects.requireNonNull(source, "source");

        if (source.get(offset) != '$' || !isDigit(source.get(offset + 1)))
            return null;

        int end = offset + 2;

        while (isDigit(source.get(end))) { end++; }

        return new TokenMatch<>( SqlTokenKind.POSTGRES_POSITIONAL_PARAMETER, end );
    }

}