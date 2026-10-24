package ru.inversion.utils.sparser.lexer.recognizer;

import ru.inversion.utils.sparser.lexer.SqlScanner;
import ru.inversion.utils.sparser.lexer.TokenRecognizer;

import static ru.inversion.utils.sparser.lexer.SqlScanner.Kind.*;

/** Диалектные формы строк и параметров PostgreSQL. */
final class PostgresRecognizers {
    private PostgresRecognizers() { }

    static final class EscapeString implements TokenRecognizer {
        @Override
        public SqlScanner.Token recognize(String sql, int at) {
            char first = sql.charAt(at);
            if (!ScanChars.atPostgresWordBoundary(sql, at)
                    || (first != 'e' && first != 'E')
                    || !ScanChars.follows(sql, at, '\'')) return null;
            return new SqlScanner.Token(STRING, at,
                    ScanChars.quotedEnd(sql, at + 1, '\'', true, at));
        }
    }

    /** PostgreSQL U&'...' и U&"...". */
    static final class UnicodeQuoted implements TokenRecognizer {
        @Override
        public SqlScanner.Token recognize(String sql, int at) {
            char first = sql.charAt(at);
            if (!ScanChars.atPostgresWordBoundary(sql, at)
                    || (first != 'u' && first != 'U')
                    || at + 2 >= sql.length() || sql.charAt(at + 1) != '&') return null;
            char quote = sql.charAt(at + 2);
            if (quote != '\'' && quote != '"') return null;
            return new SqlScanner.Token(quote == '\'' ? STRING : QUOTED_IDENTIFIER,
                    at, ScanChars.quotedEnd(sql, at + 2, quote, false, at));
        }
    }

    /** PostgreSQL $tag$...$tag$ и $$...$$; tag не начинается с цифры. */
    static final class DollarQuoted implements TokenRecognizer {
        @Override
        public SqlScanner.Token recognize(String sql, int at) {
            if (sql.charAt(at) != '$' || !ScanChars.atPostgresWordBoundary(sql, at)) return null;
            int tagEnd = at + 1;
            if (tagEnd == sql.length()) return null;

            if (sql.charAt(tagEnd) != '$') {
                if (!ScanChars.identifierStart(sql.charAt(tagEnd))) return null;
                tagEnd++;
                while (tagEnd < sql.length()) {
                    char ch = sql.charAt(tagEnd);
                    if (Character.isLetterOrDigit(ch) || ch == '_') tagEnd++;
                    else break;
                }
                if (tagEnd == sql.length() || sql.charAt(tagEnd) != '$') return null;
            }

            String delimiter = sql.substring(at, tagEnd + 1);
            int closing = sql.indexOf(delimiter, tagEnd + 1);
            if (closing < 0) throw ScanChars.unterminated("PostgreSQL dollar-quoted string", at);
            return new SqlScanner.Token(STRING, at, closing + delimiter.length());
        }
    }

    /** PostgreSQL $1, $2, ... (только цифры после $). */
    static final class NumberedParameter implements TokenRecognizer {
        @Override
        public SqlScanner.Token recognize(String sql, int at) {
            if (sql.charAt(at) != '$' || !ScanChars.atPostgresWordBoundary(sql, at)
                    || at + 1 == sql.length() || !ScanChars.digit(sql.charAt(at + 1))) return null;
            int end = at + 2;
            while (end < sql.length() && ScanChars.digit(sql.charAt(end))) end++;
            return new SqlScanner.Token(NUMBERED_PARAMETER, at, end);
        }
    }
}
