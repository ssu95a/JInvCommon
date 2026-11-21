package ru.inversion.dataset.parser.lexer.recognizer;

import ru.inversion.dataset.parser.lexer.SqlScanner;
import ru.inversion.dataset.parser.lexer.TokenRecognizer;

import static ru.inversion.dataset.parser.lexer.SqlScanner.Kind.NUMBERED_PARAMETER;
import static ru.inversion.dataset.parser.lexer.SqlScanner.Kind.STRING;

/** PostgreSQL-only syntax that is part of the supported sparser contract. */
final class PostgresRecognizers {
    private PostgresRecognizers() { }

    /** Литералы, которые надо пропускать целиком при подстановке параметров в PL/pgSQL. */
    static final class QuotedLiteral implements TokenRecognizer {
        @Override
        public SqlScanner.Token recognize(String sql, int at) {
            char first = sql.charAt(at);
            if ((first == 'E' || first == 'e') && ScanChars.follows(sql, at, '\'')) {
                for (int i = at + 2; i < sql.length(); i++) {
                    char ch = sql.charAt(i);
                    if (ch == '\\' || (ch == '\'' && ScanChars.follows(sql, i, '\''))) {
                        i++;
                    } else if (ch == '\'') {
                        return new SqlScanner.Token(STRING, at, i + 1);
                    }
                }
                throw ScanChars.unterminated("escape string", at);
            }

            if (first != '$' || !ScanChars.atPostgresWordBoundary(sql, at) || at + 1 == sql.length())
                return null;

            int tagEnd = at + 1;
            if (ScanChars.identifierStart(sql.charAt(tagEnd))) {
                do {
                    tagEnd++;
                } while (tagEnd < sql.length() && (ScanChars.identifierStart(sql.charAt(tagEnd))
                        || ScanChars.digit(sql.charAt(tagEnd))));
            }
            if (tagEnd == sql.length() || sql.charAt(tagEnd) != '$')
                return null;

            String delimiter = sql.substring(at, tagEnd + 1);
            int close = sql.indexOf(delimiter, tagEnd + 1);
            if (close < 0)
                throw ScanChars.unterminated("dollar-quoted string", at);
            return new SqlScanner.Token(STRING, at, close + delimiter.length());
        }
    }

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

            int end = ScanChars.digitsEnd(sql, at + 1);

            return new SqlScanner.Token(NUMBERED_PARAMETER, at, end);
        }
    }
}
