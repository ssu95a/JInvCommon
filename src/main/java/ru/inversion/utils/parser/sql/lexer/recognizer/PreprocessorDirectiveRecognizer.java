package ru.inversion.utils.parser.sql.lexer.recognizer;

import ru.inversion.utils.parser.lexer.TokenMatch;
import ru.inversion.utils.parser.lexer.TokenRecognizer;
import ru.inversion.utils.parser.sql.lexer.SqlTokenKind;
import ru.inversion.utils.parser.text.SourceText;

import java.util.Objects;

/**
 * Распознаёт директивы SQL-препроцессора.
 *
 * <p>Поддерживаются только две директивы:</p>
 *
 * <pre>
 * /* @parameterize:off *&#47;
 * /* @parameterize:on  *&#47;
 * </pre>
 *
 * <p>Имена и команды регистронезависимы.
 * Пробельные символы разрешены:</p>
 *
 * <ul>
 *     <li>после открытия комментария;</li>
 *     <li>до и после двоеточия;</li>
 *     <li>перед закрытием комментария.</li>
 * </ul>
 *
 * <p>Комментарий должен целиком состоять из директивы.
 * Дополнительный текст не допускается.</p>
 */
public final class PreprocessorDirectiveRecognizer
        implements TokenRecognizer<SqlTokenKind> {

    private static final String DIRECTIVE_NAME =
            "parameterize";

    private static final String OFF_COMMAND =
            "off";

    private static final String ON_COMMAND =
            "on";

    @Override
    public TokenMatch<SqlTokenKind> match(
            SourceText source,
            int offset
    ) {
        Objects.requireNonNull(
                source,
                "source"
        );

        if (source.get(offset) != '/'
                || source.get(offset + 1) != '*') {

            return null;
        }

        int current =
                offset + 2;

        current =
                skipWhitespace(
                        source,
                        current
                );

        if (source.get(current) != '@') {
            return null;
        }

        current++;

        current =
                matchTextIgnoreCase(
                        source,
                        current,
                        DIRECTIVE_NAME
                );

        if (current < 0) {
            return null;
        }

        current =
                skipWhitespace(
                        source,
                        current
                );

        if (source.get(current) != ':') {
            return null;
        }

        current++;

        current =
                skipWhitespace(
                        source,
                        current
                );

        int commandEnd =
                matchTextIgnoreCase(
                        source,
                        current,
                        OFF_COMMAND
                );

        if (commandEnd < 0) {
            commandEnd =
                    matchTextIgnoreCase(
                            source,
                            current,
                            ON_COMMAND
                    );
        }

        if (commandEnd < 0) {
            return null;
        }

        current =
                skipWhitespace(
                        source,
                        commandEnd
                );

        if (source.get(current) != '*'
                || source.get(current + 1) != '/') {

            return null;
        }

        return new TokenMatch<SqlTokenKind>(
                SqlTokenKind.PREPROCESSOR_DIRECTIVE,
                current + 2
        );
    }

    private static int skipWhitespace(
            SourceText source,
            int offset
    ) {
        int current =
                offset;

        while (isWhitespace(
                source.get(current)
        )) {
            current++;
        }

        return current;
    }

    private static boolean isWhitespace(
            int character
    ) {
        if (character == SourceText.EOF) {
            return false;
        }

        char value =
                (char) character;

        return Character.isWhitespace(value)
                || Character.isSpaceChar(value);
    }

    /**
     * Возвращает позицию сразу после совпавшего
     * текста или -1 при несовпадении.
     */
    private static int matchTextIgnoreCase(
            SourceText source,
            int offset,
            String expected
    ) {
        for (int index = 0;
             index < expected.length();
             index++) {

            int actualCharacter =
                    source.get(offset + index);

            if (actualCharacter == SourceText.EOF) {
                return -1;
            }

            char actual =
                    toLowerAscii(
                            (char) actualCharacter
                    );

            char expectedCharacter =
                    expected.charAt(index);

            if (actual != expectedCharacter) {
                return -1;
            }
        }

        return offset + expected.length();
    }

    private static char toLowerAscii(
            char character
    ) {
        if (character >= 'A'
                && character <= 'Z') {

            return (char) (
                    character
                            + ('a' - 'A')
            );
        }

        return character;
    }
}