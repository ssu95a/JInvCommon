package ru.inversion.utils.parser.sql.lexer.recognizer;

import ru.inversion.utils.parser.lexer.TokenMatch;
import ru.inversion.utils.parser.lexer.TokenRecognizer;
import ru.inversion.utils.parser.sql.lexer.SqlTokenKind;
import ru.inversion.utils.parser.text.SourceText;

import java.util.Objects;

/**
 * Распознаёт именованный параметр:
 *
 *   :name
 *   :_name
 *   :parameter1
 */
public final class NamedParameterRecognizer implements TokenRecognizer<SqlTokenKind> {

    @Override
    public TokenMatch<SqlTokenKind> match( SourceText source, int offset )
    {
        Objects.requireNonNull(source, "source");

        if( source.get(offset) != ':' )
            return null;

        if( !isNameStart(source.get(offset + 1)) )
            return null;

        int end = offset + 2;

        while( isNamePart(source.get(end)) )
            end++;

        return new TokenMatch<>( SqlTokenKind.NAMED_PARAMETER, end );
    }

    /** */
    private static boolean isNameStart(int character) {

        if( character == SourceText.EOF )
            return false;

        char value = (char) character;

        return value == '_' || Character.isLetter(value);
    }

    /** */
    private static boolean isNamePart(int character) {

        if( character == SourceText.EOF )
            return false;

        char value = (char) character;

        return value == '_' || value == '$' || Character.isLetterOrDigit(value);
    }
}