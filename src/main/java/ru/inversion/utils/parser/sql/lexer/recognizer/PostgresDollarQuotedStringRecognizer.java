package ru.inversion.utils.parser.sql.lexer.recognizer;

import ru.inversion.utils.parser.lexer.TokenMatch;
import ru.inversion.utils.parser.lexer.TokenRecognizer;
import ru.inversion.utils.parser.sql.lexer.SqlTokenKind;
import ru.inversion.utils.parser.text.SourceText;

import java.util.Objects;

/**
 * Распознаёт PostgreSQL dollar-quoted string.
 *
 * <p>Поддерживаемые формы:</p>
 *
 * <pre>
 * $$text$$
 * $tag$text$tag$
 * $_tag$text$_tag$
 * $tag123$text$tag123$
 * </pre>
 *
 * <p>Открывающий и закрывающий разделители должны
 * полностью совпадать.</p>
 *
 * <p>Правила тега:</p>
 *
 * <ul>
 *     <li>первый символ — ASCII-буква или underscore;</li>
 *     <li>остальные символы — ASCII-буква, цифра
 *     или underscore;</li>
 *     <li>пустой тег разрешён и образует разделитель
 *     {@code $$}.</li>
 * </ul>
 *
 * <p>Незакрытый литерал продолжается до конца
 * исходного текста.</p>
 */
public final class PostgresDollarQuotedStringRecognizer
        implements TokenRecognizer<SqlTokenKind> {

    @Override
    public TokenMatch<SqlTokenKind> match(
            SourceText source,
            int offset
    ) {
        Objects.requireNonNull(
                source,
                "source"
        );

        int openingDelimiterEnd =
                findOpeningDelimiterEnd(
                        source,
                        offset
                );

        if (openingDelimiterEnd < 0) {
            return null;
        }

        int delimiterLength =
                openingDelimiterEnd - offset;

        int tokenEnd =
                findTokenEnd(
                        source,
                        offset,
                        openingDelimiterEnd,
                        delimiterLength
                );

        return new TokenMatch<SqlTokenKind>(
                SqlTokenKind
                        .POSTGRES_DOLLAR_QUOTED_STRING,
                tokenEnd
        );
    }

    /**
     * Возвращает позицию сразу после открывающего
     * разделителя или -1, если в указанной позиции
     * разделитель не начинается.
     */
    private static int findOpeningDelimiterEnd(
            SourceText source,
            int offset
    ) {
        if (source.get(offset) != '$') {
            return -1;
        }

        int nextCharacter =
                source.get(offset + 1);

        /*
         * Пустой тег:
         *
         *   $$
         */
        if (nextCharacter == '$') {
            return offset + 2;
        }

        /*
         * Именованный тег не может начинаться
         * с цифры.
         */
        if (!isTagStart(nextCharacter)) {
            return -1;
        }

        int current =
                offset + 2;

        while (isTagPart(
                source.get(current)
        )) {
            current++;
        }

        if (source.get(current) != '$') {
            return -1;
        }

        return current + 1;
    }

    /**
     * Ищет первый закрывающий разделитель,
     * полностью совпадающий с открывающим.
     */
    private static int findTokenEnd(
            SourceText source,
            int openingDelimiterStart,
            int contentStart,
            int delimiterLength
    ) {
        int candidate =
                contentStart;

        while (source.get(candidate)
                != SourceText.EOF) {

            if (source.get(candidate) == '$'
                    && delimiterMatches(
                    source,
                    openingDelimiterStart,
                    candidate,
                    delimiterLength
            )) {
                return candidate
                        + delimiterLength;
            }

            candidate++;
        }

        /*
         * Незакрытый литерал занимает текст
         * до конца входа.
         */
        return source.length();
    }

    private static boolean delimiterMatches(
            SourceText source,
            int openingDelimiterStart,
            int candidateStart,
            int delimiterLength
    ) {
        for (int index = 0;
             index < delimiterLength;
             index++) {

            if (source.get(
                    openingDelimiterStart + index
            ) != source.get(
                    candidateStart + index
            )) {
                return false;
            }
        }

        return true;
    }

    private static boolean isTagStart(
            int character
    ) {
        return isAsciiLetter(character)
                || character == '_';
    }

    private static boolean isTagPart(
            int character
    ) {
        return isTagStart(character)
                || isAsciiDigit(character);
    }

    private static boolean isAsciiLetter(
            int character
    ) {
        return character >= 'a'
                && character <= 'z'
                || character >= 'A'
                && character <= 'Z';
    }

    private static boolean isAsciiDigit(
            int character
    ) {
        return character >= '0'
                && character <= '9';
    }
}