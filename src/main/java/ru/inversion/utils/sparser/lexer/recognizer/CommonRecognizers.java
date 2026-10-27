package ru.inversion.utils.sparser.lexer.recognizer;

import ru.inversion.utils.sparser.lexer.SqlScanner;
import ru.inversion.utils.sparser.lexer.TokenRecognizer;

import static ru.inversion.utils.sparser.lexer.SqlScanner.Kind.*;

/** Общие для Oracle и PostgreSQL простые правила. */
final class CommonRecognizers {

    private CommonRecognizers() { }

    static final class Whitespace implements TokenRecognizer {
        @Override
        public SqlScanner.Token recognize(String sql, int at) {
            if (!Character.isWhitespace(sql.charAt(at))) return null;
            int end = at + 1;
            while (end < sql.length() && Character.isWhitespace(sql.charAt(end))) end++;
            return new SqlScanner.Token(WHITESPACE, at, end);
        }
    }

    static final class LineComment implements TokenRecognizer {
        @Override
        public SqlScanner.Token recognize(String sql, int at) {
            if (sql.charAt(at) != '-' || !ScanChars.follows(sql, at, '-')) return null;
            int end = at + 2;
            while (end < sql.length() && sql.charAt(end) != '\n' && sql.charAt(end) != '\r') end++;
            return new SqlScanner.Token(COMMENT, at, end);
        }
    }

    static final class BlockComment implements TokenRecognizer {
        private final boolean nested;

        BlockComment(boolean nested) {
            this.nested = nested;
        }

        @Override
        public SqlScanner.Token recognize(String sql, int at) {
            if (sql.charAt(at) != '/' || !ScanChars.follows(sql, at, '*')) return null;
            int depth = 1;
            int i = at + 2;
            while (i + 1 < sql.length()) {
                if (nested && sql.charAt(i) == '/' && sql.charAt(i + 1) == '*') {
                    depth++;
                    i += 2;
                } else if (sql.charAt(i) == '*' && sql.charAt(i + 1) == '/') {
                    depth--;
                    i += 2;
                    if (depth == 0) return new SqlScanner.Token(COMMENT, at, i);
                } else {
                    i++;
                }
            }
            throw ScanChars.unterminated("block comment", at);
        }
    }

    static final class StringLiteral implements TokenRecognizer {
        @Override
        public SqlScanner.Token recognize(String sql, int at) {
            if (sql.charAt(at) != '\'') return null;
            return new SqlScanner.Token(STRING, at,
                    ScanChars.quotedEnd(sql, at, '\'', false, at));
        }
    }

    static final class QuotedIdentifier implements TokenRecognizer {
        @Override
        public SqlScanner.Token recognize(String sql, int at) {
            if (sql.charAt(at) != '"') return null;
            return new SqlScanner.Token(QUOTED_IDENTIFIER, at,
                    ScanChars.quotedEnd(sql, at, '"', false, at));
        }
    }

    /** Структурная пунктуация, которую scanner умеет пропускать безопасно. */
    static final class Symbol implements TokenRecognizer {

       private static final char[] SYMBOLS = "()[],.;~".toCharArray();

        @Override
        public SqlScanner.Token recognize( String sql, int at )
        {
           for( char ch : SYMBOLS )
               if( ch == sql.charAt(at) )
                   return new SqlScanner.Token( SYMBOL, at, at + 1);

           return null;
        }
    }

    /** Только явные JDBC-параметры: ? и :name. */
    private static boolean identifierPart(char ch, boolean oracle) {
        return ScanChars.identifierPart(ch) && (oracle || ch != '#');
    }

    static final class Parameter implements TokenRecognizer {
        private final boolean oracle;

        Parameter(boolean oracle) {
            this.oracle = oracle;
        }

        @Override
        public SqlScanner.Token recognize(String sql, int at) {
            char ch = sql.charAt(at);
            if (ch == '?') {
                return new SqlScanner.Token(POSITIONAL_PARAMETER, at, at + 1);
            }
            if (ch != ':' || at + 1 == sql.length()
                    || !ScanChars.identifierStart(sql.charAt(at + 1))) return null;
            int end = at + 2;
            while (end < sql.length() && identifierPart(sql.charAt(end), oracle)) end++;
            return new SqlScanner.Token(NAMED_PARAMETER, at, end);
        }
    }

    static final class Word implements TokenRecognizer {
        private final boolean oracle;

        Word(boolean oracle) {
            this.oracle = oracle;
        }

        @Override
        public SqlScanner.Token recognize(String sql, int at) {
            if (!ScanChars.identifierStart(sql.charAt(at))) return null;
            int end = at + 1;
            while (end < sql.length() && identifierPart(sql.charAt(end), oracle)) end++;
            return new SqlScanner.Token(WORD, at, end);
        }
    }
}
