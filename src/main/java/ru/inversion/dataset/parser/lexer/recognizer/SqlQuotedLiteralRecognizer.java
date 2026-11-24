package ru.inversion.dataset.parser.lexer.recognizer;

import ru.inversion.dataset.parser.lexer.SqlScanner;
import ru.inversion.dataset.parser.lexer.TokenRecognizer;

/**
 * Распознаёт литерал DATE целиком, чтобы строка даты осталась частью SQL
 * при параметризации. Остальные специальные формы проверяются отдельно.
 */
final class SqlQuotedLiteralRecognizer implements TokenRecognizer {

    private final boolean oracle;
    private final CommonRecognizers.BlockComment blockComment;

    SqlQuotedLiteralRecognizer(boolean oracle) {
        this.oracle = oracle;
        this.blockComment = new CommonRecognizers.BlockComment(!oracle);
    }

    @Override
    public SqlScanner.Token recognize(String sql, int at) {
        if (!ScanChars.identifierStart(sql.charAt(at)))
            return null;

        int end = ScanChars.identifierEnd(sql, at, oracle);

        // Строки с префиксами E, N, q, B, X и подобными.
        if (end < sql.length() && sql.charAt(end) == '\'')
            throw unsupported(at);

        // PostgreSQL U&'...' / U&"...".
        if (end - at == 1
                && (sql.charAt(at) == 'u' || sql.charAt(at) == 'U')
                && end + 1 < sql.length()
                && sql.charAt(end) == '&'
                && (sql.charAt(end + 1) == '\''
                || sql.charAt(end + 1) == '"'))
            throw unsupported(at);

        if (word(sql, at, end, "DATE")) {
            int quote = skipTrivia(sql, end);
            return quote < sql.length() && sql.charAt(quote) == '\''
                    ? new SqlScanner.Token(SqlScanner.Kind.TYPED_LITERAL, at, ScanChars.quotedEnd(sql, quote, '\''))
                    : null;
        }

        // TIME, TIMESTAMP и INTERVAL пока отклоняются.
        if (unsupportedTypedLiteral(sql, at, end))
            throw unsupported(at);

        return null;
    }

    private boolean unsupportedTypedLiteral(String sql, int at, int end) {
        boolean time = word(sql, at, end, "TIME");
        boolean timestamp = word(sql, at, end, "TIMESTAMP");
        boolean interval = word(sql, at, end, "INTERVAL");

        if (!time && !timestamp && !interval)
            return false;

        int i = skipTrivia(sql, end);

        if (i < sql.length() && sql.charAt(i) == '(') {
            int afterPrecision = precisionEnd(sql, i);
            if (afterPrecision < 0)
                return false;
            i = skipTrivia(sql, afterPrecision);
        }

        if (time || timestamp) {
            int qualifier = keywordEnd(sql, i, "WITH");
            if (qualifier < 0)
                qualifier = keywordEnd(sql, i, "WITHOUT");

            if (qualifier >= 0) {
                i = skipTrivia(sql, qualifier);

                int timeEnd = keywordEnd(sql, i, "TIME");
                if (timeEnd < 0)
                    return false;

                i = skipTrivia(sql, timeEnd);

                int zoneEnd = keywordEnd(sql, i, "ZONE");
                if (zoneEnd < 0)
                    return false;

                i = skipTrivia(sql, zoneEnd);
            }
        }

        return i < sql.length() && sql.charAt(i) == '\'';
    }

    private int precisionEnd(String sql, int at) {
        int i = skipTrivia(sql, at + 1);

        if (i >= sql.length() || !ScanChars.digit(sql.charAt(i)))
            return -1;

        i = ScanChars.digitsEnd(sql, i);

        i = skipTrivia(sql, i);

        return i < sql.length() && sql.charAt(i) == ')'
                ? i + 1
                : -1;
    }

    private int skipTrivia(String sql, int at) {
        int i = at;

        while (i < sql.length()) {
            if (Character.isWhitespace(sql.charAt(i))) {
                i++;
                continue;
            }

            if (sql.charAt(i) == '-'
                    && i + 1 < sql.length()
                    && sql.charAt(i + 1) == '-') {
                i += 2;
                while (i < sql.length()
                        && sql.charAt(i) != '\n'
                        && sql.charAt(i) != '\r')
                    i++;
                continue;
            }

            if (sql.charAt(i) == '/'
                    && i + 1 < sql.length()
                    && sql.charAt(i + 1) == '*') {
                SqlScanner.Token comment = blockComment.recognize(sql, i);
                i = comment.end();
                continue;
            }

            break;
        }

        return i;
    }

    private int keywordEnd(String sql, int at, String keyword) {
        int end = at + keyword.length();

        if (end > sql.length()
                || !sql.regionMatches(true, at, keyword, 0, keyword.length()))
            return -1;

        if (end < sql.length()
                && ScanChars.identifierPart(sql.charAt(end), oracle))
            return -1;

        return end;
    }

    private static boolean word(
            String sql,
            int at,
            int end,
            String expected
    ) {
        return end - at == expected.length()
                && sql.regionMatches(true, at, expected, 0, expected.length());
    }

    private static IllegalArgumentException unsupported(int at) {
        return new IllegalArgumentException(
                "Unsupported quoted literal at offset " + at);
    }
}
