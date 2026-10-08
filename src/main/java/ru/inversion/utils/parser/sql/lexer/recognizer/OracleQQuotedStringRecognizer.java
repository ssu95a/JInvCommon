package ru.inversion.utils.parser.sql.lexer.recognizer;

import ru.inversion.utils.parser.lexer.TokenMatch;
import ru.inversion.utils.parser.lexer.TokenRecognizer;
import ru.inversion.utils.parser.sql.lexer.SqlTokenKind;
import ru.inversion.utils.parser.text.SourceText;

import java.util.Objects;

/**
 * Распознаёт Oracle alternative quoted string.
 *
 * <p>Поддерживаемые формы:</p>
 *
 * <pre>
 * q'[text]'
 * q'{text}'
 * q'(text)'
 * q'&lt;text&gt;'
 * q'!text!'
 * Q'[text]'
 * </pre>
 *
 * <p>Для открывающих разделителей {@code [}, {@code \{},
 * {@code (} и {@code <} используются соответствующие
 * закрывающие разделители.</p>
 *
 * <p>Все остальные допустимые разделители закрываются
 * тем же символом.</p>
 *
 * <p>Незакрытый литерал продолжается до конца
 * исходного текста.</p>
 */
public final class OracleQQuotedStringRecognizer
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

        if (!isQ(source.get(offset))) {
            return null;
        }

        if (source.get(offset + 1) != '\'') {
            return null;
        }

        int openingDelimiter =
                source.get(offset + 2);

        if (!isValidDelimiter(
                openingDelimiter
        )) {
            return null;
        }

        int closingDelimiter =
                closingDelimiter(
                        openingDelimiter
                );

        int contentStart =
                offset + 3;

        int tokenEnd =
                findTokenEnd(
                        source,
                        contentStart,
                        closingDelimiter
                );

        return new TokenMatch<SqlTokenKind>(
                SqlTokenKind.ORACLE_Q_QUOTED_STRING,
                tokenEnd
        );
    }

    /**
     * Ищет закрывающий разделитель, за которым
     * непосредственно следует одинарная кавычка.
     */
    private static int findTokenEnd(
            SourceText source,
            int contentStart,
            int closingDelimiter
    ) {
        int candidate =
                contentStart;

        while (source.get(candidate)
                != SourceText.EOF) {

            if (source.get(candidate)
                    == closingDelimiter
                    && source.get(candidate + 1)
                    == '\'') {

                return candidate + 2;
            }

            candidate++;
        }

        return source.length();
    }

    private static int closingDelimiter(
            int openingDelimiter
    ) {
        switch (openingDelimiter) {
            case '[':
                return ']';

            case '{':
                return '}';

            case '(':
                return ')';

            case '<':
                return '>';

            default:
                return openingDelimiter;
        }
    }

    private static boolean isQ(
            int character
    ) {
        return character == 'q'
                || character == 'Q';
    }

    /**
     * Разделителем не может быть конец текста
     * или whitespace.
     */
    private static boolean isValidDelimiter(
            int character
    ) {
        return character != SourceText.EOF
                && character != ' '
                && character != '\t'
                && character != '\r'
                && character != '\n';
    }
}