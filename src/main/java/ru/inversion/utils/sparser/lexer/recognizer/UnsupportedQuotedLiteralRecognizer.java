package ru.inversion.utils.sparser.lexer.recognizer;

import ru.inversion.utils.sparser.lexer.SqlScanner;
import ru.inversion.utils.sparser.lexer.TokenRecognizer;

/**
 * Fail-fast guard for quoted value syntax outside the supported sparser SQL.
 *
 * Supported application values are plain strings/numbers or explicit bind
 * parameters. Prefixed and typed quoted literals are intentionally rejected.
 */
final class UnsupportedQuotedLiteralRecognizer implements TokenRecognizer {

    private final boolean oracle;
    private final CommonRecognizers.BlockComment blockComment;

    UnsupportedQuotedLiteralRecognizer(boolean oracle) {
        this.oracle = oracle;
        this.blockComment = new CommonRecognizers.BlockComment(!oracle);
    }

    @Override
    public SqlScanner.Token recognize(String sql, int at) {
        if (!ScanChars.identifierStart(sql.charAt(at)))
            return null;

        int end = identifierEnd(sql, at);

        // E'...', N'...', q'...', B'...', X'...' and similar prefixes.
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

        // DATE '...', TIME '...', TIMESTAMP(3) WITH TIME ZONE '...', ...
        if (typedQuotedLiteral(sql, at, end))
            throw unsupported(at);

        return null;
    }

    private int identifierEnd(String sql, int at) {
        int end = at + 1;
        while (end < sql.length()
                && ScanChars.identifierPart(sql.charAt(end), oracle))
            end++;
        return end;
    }

    private boolean typedQuotedLiteral(String sql, int at, int end) {
        boolean date = word(sql, at, end, "DATE");
        boolean time = word(sql, at, end, "TIME");
        boolean timestamp = word(sql, at, end, "TIMESTAMP");
        boolean interval = word(sql, at, end, "INTERVAL");

        if (!date && !time && !timestamp && !interval)
            return false;

        int i = skipTrivia(sql, end);

        if ((time || timestamp || interval)
                && i < sql.length()
                && sql.charAt(i) == '(') {
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

        while (i < sql.length() && ScanChars.digit(sql.charAt(i)))
            i++;

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
