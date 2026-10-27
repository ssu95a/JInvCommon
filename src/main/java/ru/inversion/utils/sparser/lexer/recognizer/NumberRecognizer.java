package ru.inversion.utils.sparser.lexer.recognizer;

import ru.inversion.utils.sparser.lexer.SqlScanner;
import ru.inversion.utils.sparser.lexer.TokenRecognizer;

import static ru.inversion.utils.sparser.lexer.SqlScanner.Kind.NUMBER;

/** Числовые лексемы, включая расширенные PostgreSQL radix-формы. */
final class NumberRecognizer implements TokenRecognizer {
    private final boolean postgres;

    NumberRecognizer(boolean postgres) {
        this.postgres = postgres;
    }

    @Override
    public SqlScanner.Token recognize( String sql, int at )
    {
        char ch = sql.charAt(at);

        if( !ScanChars.digit(ch)
             && !(ch == '.' && at + 1 < sql.length()
             && ScanChars.digit(sql.charAt(at + 1))
             && (at == 0 || sql.charAt(at - 1) != '.')))
        {
            return null;
        }

        int radixEnd = postgres ? postgresRadixEnd(sql, at) : at;
        int end = radixEnd > at ? radixEnd : decimalEnd(sql, at, postgres);
        if (!postgres && end < sql.length()) {
            // Oracle BINARY_FLOAT/BINARY_DOUBLE literal suffix, including 1e10d.
            char suffix = sql.charAt(end);
            if (suffix == 'f' || suffix == 'F' || suffix == 'd' || suffix == 'D') {
                int next = end + 1;
                if (next == sql.length() || !(Character.isLetterOrDigit(sql.charAt(next))
                        || sql.charAt(next) == '_' || sql.charAt(next) == '$')) {
                    end++;
                }
            }
        }
        return new SqlScanner.Token(NUMBER, at, end);
    }

    private static int postgresRadixEnd(String sql, int at) {
        if (sql.charAt(at) != '0' || at + 2 >= sql.length()) return at;
        char prefix = sql.charAt(at + 1);
        int radix = prefix == 'x' || prefix == 'X' ? 16
                : prefix == 'o' || prefix == 'O' ? 8
                : prefix == 'b' || prefix == 'B' ? 2 : 0;
        if (radix == 0) return at;

        int first = at + 2;
        if (sql.charAt(first) == '_') first++;
        if (first == sql.length() || Character.digit(sql.charAt(first), radix) < 0) return at;

        int end = first + 1;
        while (end < sql.length()) {
            char ch = sql.charAt(end);
            if (Character.digit(ch, radix) >= 0) {
                end++;
            } else if (ch == '_' && end + 1 < sql.length()
                    && Character.digit(sql.charAt(end + 1), radix) >= 0) {
                end++;
            } else {
                break;
            }
        }
        return end;
    }

    private static int decimalEnd(String sql, int at, boolean postgres) {
        int end = at;
        if (sql.charAt(end) == '.') end++;
        end = digitsEnd(sql, end, postgres);

        if (end < sql.length() && sql.charAt(end) == '.'
                && (end + 1 == sql.length() || sql.charAt(end + 1) != '.')) {
            end = digitsEnd(sql, end + 1, postgres);
        }

        if (end < sql.length() && (sql.charAt(end) == 'e' || sql.charAt(end) == 'E')) {
            int afterE = end + 1;
            if (afterE < sql.length()
                    && (sql.charAt(afterE) == '+' || sql.charAt(afterE) == '-')) afterE++;
            if (afterE < sql.length() && ScanChars.digit(sql.charAt(afterE))) {
                end = digitsEnd(sql, afterE, postgres);
            }
        }
        return end;
    }

    private static int digitsEnd(String sql, int at, boolean postgres) {
        int end = at;
        while (end < sql.length()) {
            char ch = sql.charAt(end);
            if (ScanChars.digit(ch)) {
                end++;
            } else if (postgres && ch == '_' && end > at && end + 1 < sql.length()
                    && ScanChars.digit(sql.charAt(end + 1))) {
                end++;
            } else {
                break;
            }
        }
        return end;
    }
}
