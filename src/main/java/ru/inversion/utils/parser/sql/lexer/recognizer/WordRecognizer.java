package ru.inversion.utils.parser.sql.lexer.recognizer;

import ru.inversion.utils.parser.lexer.TokenMatch;
import ru.inversion.utils.parser.lexer.TokenRecognizer;
import ru.inversion.utils.parser.sql.lexer.SqlTokenKind;
import ru.inversion.utils.parser.text.SourceText;

import java.util.Objects;

/**
 * Распознаёт обычное SQL-слово.
 *
 * Первый символ:
 * - Unicode-буква;
 * - символ подчёркивания.
 *
 * Последующие символы:
 * - Unicode-буквы;
 * - цифры;
 * - символ подчёркивания;
 * - знак доллара.
 *
 * Конкретное грамматическое значение слова определяет parser.
 */
public final class WordRecognizer implements TokenRecognizer<SqlTokenKind> {

    @Override
    public TokenMatch<SqlTokenKind> match( SourceText source, int offset )
    {
        Objects.requireNonNull(source, "source");

        int character = source.get(offset);

        if( !isWordStart(character) )
            return null;

        int end = offset + 1;

        while( isWordPart(source.get(end)) )
            end++;

        return new TokenMatch<>( SqlTokenKind.WORD, end );
    }

    /** */
    private static boolean isWordStart(int character)
    {
        if( character == SourceText.EOF )
            return false;

        char value = (char) character;

        return value == '_' || Character.isLetter(value);
    }

    /** */
    private static boolean isWordPart( int character )
    {
        if( character == SourceText.EOF )
            return false;

        char value = (char) character;

        return value == '_' || value == '$' || Character.isLetterOrDigit(value);
    }
}