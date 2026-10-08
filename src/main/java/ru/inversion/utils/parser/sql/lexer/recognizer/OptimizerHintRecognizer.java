package ru.inversion.utils.parser.sql.lexer.recognizer;

import ru.inversion.utils.parser.lexer.TokenMatch;
import ru.inversion.utils.parser.lexer.TokenRecognizer;
import ru.inversion.utils.parser.sql.lexer.SqlTokenKind;
import ru.inversion.utils.parser.text.SourceText;

import java.util.Objects;

/**
 * Распознаёт optimizer hint в формате блочного
 * SQL-комментария, начинающегося с {@code /*+}.
 *
 * <p>Примеры:</p>
 *
 * <pre>
 * /*+ INDEX(t IDX_T) *&#47;
 * /*+ FIRST_ROWS(10) *&#47;
 * /*+
 *     LEADING(a b)
 *     USE_NL(b)
 * *&#47;
 * </pre>
 *
 * <p>Между {@code /*} и {@code +} пробельные
 * символы не допускаются. Поэтому комментарий</p>
 *
 * <pre>
 * /* + INDEX(t IDX_T) *&#47;
 * </pre>
 *
 * <p>не является optimizer hint.</p>
 *
 * <p>Комментарий заканчивается первой
 * последовательностью {@code *&#47;}. Незакрытый
 * hint продолжается до конца исходного текста.</p>
 */
public final class OptimizerHintRecognizer
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

        if (source.get(offset) != '/'
                || source.get(offset + 1) != '*'
                || source.get(offset + 2) != '+') {

            return null;
        }

        int end =
                offset + 3;

        while (true) {
            int character =
                    source.get(end);

            if (character == SourceText.EOF) {
                return new TokenMatch<SqlTokenKind>(
                        SqlTokenKind.OPTIMIZER_HINT,
                        source.length()
                );
            }

            if (character == '*'
                    && source.get(end + 1) == '/') {

                return new TokenMatch<SqlTokenKind>(
                        SqlTokenKind.OPTIMIZER_HINT,
                        end + 2
                );
            }

            end++;
        }
    }
}
