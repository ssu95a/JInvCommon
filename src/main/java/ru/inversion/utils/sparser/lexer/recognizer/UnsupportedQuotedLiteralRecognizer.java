package ru.inversion.utils.sparser.lexer.recognizer;

import ru.inversion.utils.sparser.lexer.SqlScanner;
import ru.inversion.utils.sparser.lexer.TokenRecognizer;

/**
 * Fail-fast guard for quoted literal forms outside the supported sparser SQL.
 *
 * Examples: E'...', N'...', q'...', B'...', X'...', U&'...', U&"...".
 */
final class UnsupportedQuotedLiteralRecognizer implements TokenRecognizer {

    private final boolean oracle;

    UnsupportedQuotedLiteralRecognizer(boolean oracle) {
        this.oracle = oracle;
    }

    @Override
    public SqlScanner.Token recognize(String sql, int at) {
        if (!ScanChars.identifierStart(sql.charAt(at)))
            return null;

        int end = at + 1;
        while (end < sql.length()
                && ScanChars.identifierPart(sql.charAt(end), oracle))
            end++;

        if (end < sql.length() && sql.charAt(end) == '\'')
            throw unsupported(at);

        if (end - at == 1
                && (sql.charAt(at) == 'u' || sql.charAt(at) == 'U')
                && end + 1 < sql.length()
                && sql.charAt(end) == '&'
                && (sql.charAt(end + 1) == '\''
                || sql.charAt(end + 1) == '"'))
            throw unsupported(at);

        return null;
    }

    private static IllegalArgumentException unsupported(int at) {
        return new IllegalArgumentException(
                "Unsupported quoted literal at offset " + at);
    }
}
