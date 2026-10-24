package ru.inversion.utils.sparser.lexer.recognizer;

import ru.inversion.utils.sparser.lexer.SqlScanner;
import ru.inversion.utils.sparser.lexer.TokenRecognizer;

import static ru.inversion.utils.sparser.lexer.SqlScanner.Kind.PARAMETERIZE_OFF;
import static ru.inversion.utils.sparser.lexer.SqlScanner.Kind.PARAMETERIZE_ON;

/**
 * Распознаёт директивы @parameterize:off/on в блочных SQL-комментариях.
 *
 * Никакого состояния параметризации здесь нет: переключение режима
 * происходит при обходе уже собранного SQL, в том числе между его частями.
 */
final class ParameterizeDirectiveRecognizer implements TokenRecognizer {

    private static final String NAME = "@parameterize";

    @Override
    public SqlScanner.Token recognize(String sql, int at) {
        if (sql.charAt(at) != '/' || !ScanChars.follows(sql, at, '*')) return null;

        int i = skipSpaces(sql, at + 2);
        if (i >= sql.length() || sql.charAt(i) != '@') return null;
        if (!sql.regionMatches(true, i, NAME, 0, NAME.length())) return null;
        i += NAME.length();
        i = skipSpaces(sql, i);
        if (i >= sql.length() || sql.charAt(i) != ':') throw invalidDirective(at);
        i = skipSpaces(sql, i + 1);

        final SqlScanner.Kind kind;
        if (sql.regionMatches(true, i, "off", 0, 3)) {
            kind = PARAMETERIZE_OFF;
            i += 3;
        } else if (sql.regionMatches(true, i, "on", 0, 2)) {
            kind = PARAMETERIZE_ON;
            i += 2;
        } else {
            throw invalidDirective(at);
        }

        i = skipSpaces(sql, i);
        if (i + 1 >= sql.length() || sql.charAt(i) != '*' || sql.charAt(i + 1) != '/') {
            throw invalidDirective(at);
        }
        return new SqlScanner.Token(kind, at, i + 2);
    }

    private static int skipSpaces(String sql, int i) {
        while (i < sql.length() && Character.isWhitespace(sql.charAt(i))) i++;
        return i;
    }

    private static IllegalArgumentException invalidDirective(int at) {
        return new IllegalArgumentException("Invalid parameterize directive at offset " + at);
    }
}
