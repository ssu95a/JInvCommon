package ru.inversion.utils.sparser.lexer.recognizer;

import ru.inversion.utils.sparser.lexer.SqlScanner;
import ru.inversion.utils.sparser.lexer.TokenRecognizer;

import static ru.inversion.utils.sparser.lexer.SqlScanner.Kind.NUMBERED_PARAMETER;

/** PostgreSQL-only syntax that is part of the supported sparser contract. */
final class PostgresRecognizers {
    private PostgresRecognizers() { }

    /** PostgreSQL $1, $2, ... bind parameters. */
    static final class NumberedParameter implements TokenRecognizer {
        @Override
        public SqlScanner.Token recognize(String sql, int at) {
            if (sql.charAt(at) != '$'
                    || !ScanChars.atPostgresWordBoundary(sql, at)
                    || at + 1 == sql.length()
                    || !ScanChars.digit(sql.charAt(at + 1))) {
                return null;
            }

            int end = at + 2;
            while (end < sql.length() && ScanChars.digit(sql.charAt(end)))
                end++;

            return new SqlScanner.Token(NUMBERED_PARAMETER, at, end);
        }
    }
}
