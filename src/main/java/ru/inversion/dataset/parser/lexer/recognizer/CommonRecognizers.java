package ru.inversion.dataset.parser.lexer.recognizer;

import ru.inversion.dataset.parser.lexer.SqlScanner;
import ru.inversion.dataset.parser.lexer.TokenRecognizer;

import static ru.inversion.dataset.parser.lexer.SqlScanner.Kind.*;

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
            if( sql.charAt(at) != '\'')
                return null;
            return new SqlScanner.Token( STRING, at, ScanChars.quotedEnd(sql, at, '\'') );
        }
    }

    static final class QuotedIdentifier implements TokenRecognizer {
        @Override
        public SqlScanner.Token recognize(String sql, int at) {
            if (sql.charAt(at) != '"') return null;
            return new SqlScanner.Token(QUOTED_IDENTIFIER, at,
                    ScanChars.quotedEnd(sql, at, '"'));
        }
    }

    /** Структурная пунктуация, которую scanner умеет пропускать безопасно. */
    static final class Symbol implements TokenRecognizer {

        private final String symbols;

        Symbol(boolean call) {
            symbols = call ? "()[]{},.;~" : "()[],.;~";
        }

        @Override
        public SqlScanner.Token recognize( String sql, int at )
        {
           return symbols.indexOf(sql.charAt(at)) >= 0
                   ? new SqlScanner.Token(SYMBOL, at, at + 1)
                   : null;
        }
    }

    /** JDBC-параметры ? и :name. В вызовах имя может состоять из цифр. */
    static final class Parameter implements TokenRecognizer {
        private final boolean oracle;
        private final boolean call;

        Parameter(boolean oracle, boolean call) {
            this.oracle = oracle;
            this.call = call;
        }

        @Override
        public SqlScanner.Token recognize(String sql, int at) {
            char ch = sql.charAt(at);
            if (ch == '?') {
                return new SqlScanner.Token(POSITIONAL_PARAMETER, at, at + 1);
            }
            if (ch != ':') return null;

            int start = at + 1;
            if (oracle && call) {
                while (start < sql.length() && Character.isWhitespace(sql.charAt(start))) start++;
            }
            if (start == sql.length()) return null;
            if (call && ScanChars.digit(sql.charAt(start))) {
                int end = ScanChars.digitsEnd(sql, start);
                if (end < sql.length() && ScanChars.identifierPart(sql.charAt(end), oracle))
                    return null;
                return new SqlScanner.Token(NAMED_PARAMETER, at, end);
            }
            if (!ScanChars.identifierStart(sql.charAt(start))) return null;
            int end = ScanChars.identifierEnd(sql, start, oracle);

            // Forms-style application parameters use qualified names such as
            // :BLOCK.ITEM. The complete name belongs to one bind parameter;
            // this is an established JInvCommon contract, not SQL navigation.
            while (end + 1 < sql.length()
                    && sql.charAt(end) == '.'
                    && ScanChars.identifierStart(sql.charAt(end + 1))) {
                end = ScanChars.identifierEnd(sql, end + 1, oracle);
            }

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
            return new SqlScanner.Token(
                    WORD,
                    at,
                    ScanChars.identifierEnd(sql, at, oracle)
            );
        }
    }
}
