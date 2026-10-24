package ru.inversion.utils.sparser.lexer.recognizer;

import ru.inversion.utils.sparser.lexer.SqlScanner;
import ru.inversion.utils.sparser.lexer.TokenRecognizer;

import static ru.inversion.utils.sparser.lexer.SqlScanner.Kind.STRING;

/** Диалектные виды строковых литералов Oracle. */
final class OracleRecognizers {
    private OracleRecognizers() { }

    /** q'[...]', q'{...}', q'(...)', q'<...>', q'!...!' и nq'...'. */
    static final class QQuotedString implements TokenRecognizer {
        @Override
        public SqlScanner.Token recognize(String sql, int at) {
            if (!ScanChars.atWordBoundary(sql, at)) return null;
            int quote;
            char first = sql.charAt(at);
            if ((first == 'q' || first == 'Q') && ScanChars.follows(sql, at, '\'')) {
                quote = at + 1;
            } else if ((first == 'n' || first == 'N') && at + 2 < sql.length()
                    && (sql.charAt(at + 1) == 'q' || sql.charAt(at + 1) == 'Q')
                    && sql.charAt(at + 2) == '\'') {
                quote = at + 2;
            } else {
                return null;
            }

            if (quote + 1 == sql.length()) {
                throw ScanChars.unterminated("Oracle q-quoted string", at);
            }
            char opener = sql.charAt(quote + 1);
            if (Character.isWhitespace(opener) || Character.isSpaceChar(opener)
                    || opener == '\'') {
                throw new IllegalArgumentException("Invalid Oracle q-quote delimiter at offset " + at);
            }
            char closer;
            switch (opener) {
                case '[': closer = ']'; break;
                case '{': closer = '}'; break;
                case '(': closer = ')'; break;
                case '<': closer = '>'; break;
                default:  closer = opener;
            }
            for (int i = quote + 2; i + 1 < sql.length(); i++) {
                if (sql.charAt(i) == closer && sql.charAt(i + 1) == '\'') {
                    return new SqlScanner.Token(STRING, at, i + 2);
                }
            }
            throw ScanChars.unterminated("Oracle q-quoted string", at);
        }
    }

    /** Национальная строка Oracle N'...' . */
    static final class NationalString implements TokenRecognizer {
        @Override
        public SqlScanner.Token recognize(String sql, int at) {
            char first = sql.charAt(at);
            if (!ScanChars.atWordBoundary(sql, at)
                    || (first != 'n' && first != 'N')
                    || !ScanChars.follows(sql, at, '\'')) return null;
            return new SqlScanner.Token(STRING, at,
                    ScanChars.quotedEnd(sql, at + 1, '\'', false, at));
        }
    }
}
