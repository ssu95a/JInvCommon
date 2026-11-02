package ru.inversion.utils.sparser.lexer.recognizer;

import ru.inversion.utils.sparser.lexer.SqlScanner;
import ru.inversion.utils.sparser.lexer.TokenRecognizer;

import static ru.inversion.utils.sparser.lexer.SqlScanner.Kind.NUMBER;

/**
 * Ordinary decimal SQL numbers shared by Oracle and PostgreSQL.
 * Dialect-specific radix forms, underscores and numeric suffixes are unsupported.
 */
final class NumberRecognizer implements TokenRecognizer {

    @Override
    public SqlScanner.Token recognize(String sql, int at) {
        char ch = sql.charAt(at);

        if( !ScanChars.digit(ch)
                && !(ch == '.'
                && at + 1 < sql.length()
                && ScanChars.digit(sql.charAt(at + 1))
                && (at == 0 || sql.charAt(at - 1) != '.')) ) {
            return null;
        }

        int end = decimalEnd(sql, at);

        if( end < sql.length()
                && ScanChars.identifierPart(sql.charAt(end)) ) {
            throw new IllegalArgumentException(
                    "Unsupported numeric literal at offset " + at);
        }

        return new SqlScanner.Token(NUMBER, at, end);
    }

    private static int decimalEnd(String sql, int at) {
        int end = at;

        if( sql.charAt(end) == '.' )
            end++;

        end = digitsEnd(sql, end);

        if( end < sql.length()
                && sql.charAt(end) == '.'
                && (end + 1 == sql.length()
                || sql.charAt(end + 1) != '.') ) {
            end = digitsEnd(sql, end + 1);
        }

        if( end < sql.length()
                && (sql.charAt(end) == 'e'
                || sql.charAt(end) == 'E') ) {
            int exponent = end + 1;

            if( exponent < sql.length()
                    && (sql.charAt(exponent) == '+'
                    || sql.charAt(exponent) == '-') ) {
                exponent++;
            }

            if( exponent < sql.length()
                    && ScanChars.digit(sql.charAt(exponent)) ) {
                end = digitsEnd(sql, exponent);
            }
        }

        return end;
    }

    private static int digitsEnd(String sql, int at) {
        int end = at;
        while (end < sql.length() && ScanChars.digit(sql.charAt(end)))
            end++;
        return end;
    }
}
