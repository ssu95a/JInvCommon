package ru.inversion.utils.sparser.lexer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Небольшой SQL lexer для структурных операций sparser.
 *
 * Это не универсальный Oracle/PostgreSQL lexer. Поддерживается только синтаксис,
 * который нужен текущему pipeline; неизвестные формы отвергаются fail-fast.
 *
 * Каждый символ принадлежит ровно одному токену; диапазоны [start, end).
 */
public abstract class SqlScanner {

    public enum Kind {
        WORD,
        NUMBER,
        STRING,
        QUOTED_IDENTIFIER,
        COMMENT,
        HINT,
        PARAMETERIZE_OFF,
        PARAMETERIZE_ON,
        WHITESPACE,
        POSITIONAL_PARAMETER,
        NAMED_PARAMETER,
        NUMBERED_PARAMETER,
        OPERATOR,
        SYMBOL
    }

    public static final class Token {

        private final Kind kind;
        private final int start;
        private final int end;

        public Token(Kind kind, int start, int end) {
            this.kind = Objects.requireNonNull(kind, "kind");

            if( start < 0 || end <= start )
                throw new IllegalArgumentException(
                        "Invalid token range: " + start + ".." + end);

            this.start = start;
            this.end = end;
        }

        public Kind kind() { return kind; }
        public int start() { return start; }
        public int end() { return end; }

        public String text(String source) {
            return source.substring(start, end);
        }

        public boolean isWord(String source, String expected) {
            return kind == Kind.WORD
                    && end - start == expected.length()
                    && source.regionMatches(
                    true, start, expected, 0, expected.length());
        }

        public SqlKeyword keyword(String source) {
            return kind == Kind.WORD
                    ? SqlKeyword.of(source, start, end)
                    : null;
        }

        public boolean isKeyword(String source, SqlKeyword keyword) {
            return kind == Kind.WORD
                    && keyword != null
                    && keyword.matches(source, start, end);
        }

        @Override
        public String toString() {
            return kind + "[" + start + ", " + end + ")";
        }
    }

    private static final String PG_OPERATOR_CHARS =
            "+-*/<>=~!@#%^&|`?";

    private static final String ORACLE_SINGLE_OPERATORS =
            "+-*/<>=";

    private static final String[] ORACLE_DOUBLE_OPERATORS = {
            "<=", ">=", "<>", "!=", "^=", "~=", "||", "**", "=>"
    };

    private static final String SYMBOLS = "()[],.;~";

    private static final String PARAMETERIZE = "@parameterize";

    private final boolean oracle;

    SqlScanner(boolean oracle) {
        this.oracle = oracle;
    }

    public final List<Token> scan(String sql) {
        Objects.requireNonNull(sql, "sql");

        List<Token> result = new ArrayList<>();
        int offset = 0;

        while( offset < sql.length() ) {
            Token token = recognize(sql, offset);

            if( token == null )
                throw unsupported(sql, offset);

            if( token.start() != offset || token.end() > sql.length() )
                throw new IllegalStateException(
                        "Invalid token at " + offset + ": " + token);

            result.add(token);
            offset = token.end();
        }

        return Collections.unmodifiableList(result);
    }

    private Token recognize(String sql, int at) {
        char ch = sql.charAt(at);

        if( Character.isWhitespace(ch) )
            return whitespace(sql, at);

        if( ch == '-' && follows(sql, at, '-') )
            return lineComment(sql, at);

        if( ch == '/' && follows(sql, at, '*') ) {
            Token directive = parameterizeDirective(sql, at);
            if( directive != null )
                return directive;

            Kind kind = at + 2 < sql.length()
                    && sql.charAt(at + 2) == '+'
                    ? Kind.HINT
                    : Kind.COMMENT;

            return blockComment(sql, at, !oracle, kind);
        }

        if( !oracle && ch == '$' ) {
            Token numbered = numberedParameter(sql, at);
            if( numbered != null )
                return numbered;
        }

        if( ch == '\'' )
            return new Token(
                    Kind.STRING, at, quotedEnd(sql, at, '\''));

        if( ch == '"' )
            return new Token(
                    Kind.QUOTED_IDENTIFIER, at, quotedEnd(sql, at, '"'));

        Token operator = operator(sql, at);
        if( operator != null )
            return operator;

        if( ch == '?' )
            return new Token(Kind.POSITIONAL_PARAMETER, at, at + 1);

        if( ch == ':' ) {
            Token named = namedParameter(sql, at);
            if( named != null )
                return named;
        }

        if( numberStart(sql, at) )
            return number(sql, at);

        if( identifierStart(ch) )
            return word(sql, at);

        if( SYMBOLS.indexOf(ch) >= 0 )
            return new Token(Kind.SYMBOL, at, at + 1);

        return null;
    }

    private static Token whitespace(String sql, int at) {
        int end = at + 1;
        while( end < sql.length()
                && Character.isWhitespace(sql.charAt(end)) ) {
            end++;
        }
        return new Token(Kind.WHITESPACE, at, end);
    }

    private static Token lineComment(String sql, int at) {
        int end = at + 2;
        while( end < sql.length()
                && sql.charAt(end) != '\n'
                && sql.charAt(end) != '\r' ) {
            end++;
        }
        return new Token(Kind.COMMENT, at, end);
    }

    private static Token blockComment(
            String sql,
            int at,
            boolean nested,
            Kind kind
    ) {
        int depth = 1;
        int i = at + 2;

        while( i + 1 < sql.length() ) {
            if( nested
                    && sql.charAt(i) == '/'
                    && sql.charAt(i + 1) == '*' ) {
                depth++;
                i += 2;
            }
            else if( sql.charAt(i) == '*'
                    && sql.charAt(i + 1) == '/' ) {
                depth--;
                i += 2;

                if( depth == 0 )
                    return new Token(kind, at, i);
            }
            else {
                i++;
            }
        }

        throw unterminated("block comment", at);
    }

    private static Token parameterizeDirective(String sql, int at) {
        int i = skipSpaces(sql, at + 2);

        if( i >= sql.length() || sql.charAt(i) != '@' )
            return null;

        if( !sql.regionMatches(
                true, i, PARAMETERIZE, 0, PARAMETERIZE.length()) ) {
            return null;
        }

        i += PARAMETERIZE.length();
        i = skipSpaces(sql, i);

        if( i >= sql.length() || sql.charAt(i) != ':' )
            throw invalidDirective(at);

        i = skipSpaces(sql, i + 1);

        final Kind kind;

        if( sql.regionMatches(true, i, "off", 0, 3) ) {
            kind = Kind.PARAMETERIZE_OFF;
            i += 3;
        }
        else if( sql.regionMatches(true, i, "on", 0, 2) ) {
            kind = Kind.PARAMETERIZE_ON;
            i += 2;
        }
        else {
            throw invalidDirective(at);
        }

        i = skipSpaces(sql, i);

        if( i + 1 >= sql.length()
                || sql.charAt(i) != '*'
                || sql.charAt(i + 1) != '/' ) {
            throw invalidDirective(at);
        }

        return new Token(kind, at, i + 2);
    }

    private static int skipSpaces(String sql, int at) {
        int i = at;
        while( i < sql.length()
                && Character.isWhitespace(sql.charAt(i)) ) {
            i++;
        }
        return i;
    }

    private static IllegalArgumentException invalidDirective(int at) {
        return new IllegalArgumentException(
                "Invalid parameterize directive at offset " + at);
    }

    private Token operator(String sql, int at) {
        if( at + 1 < sql.length()
                && sql.charAt(at) == ':'
                && (sql.charAt(at + 1) == ':'
                || sql.charAt(at + 1) == '=') ) {
            return new Token(Kind.OPERATOR, at, at + 2);
        }

        return oracle
                ? oracleOperator(sql, at)
                : postgresOperator(sql, at);
    }

    private static Token oracleOperator(String sql, int at) {
        for( String candidate : ORACLE_DOUBLE_OPERATORS ) {
            if( at + candidate.length() <= sql.length()
                    && sql.regionMatches(
                    at, candidate, 0, candidate.length()) ) {
                return new Token(
                        Kind.OPERATOR, at, at + candidate.length());
            }
        }

        return ORACLE_SINGLE_OPERATORS.indexOf(sql.charAt(at)) >= 0
                ? new Token(Kind.OPERATOR, at, at + 1)
                : null;
    }

    private static Token postgresOperator(String sql, int at) {
        if( PG_OPERATOR_CHARS.indexOf(sql.charAt(at)) < 0 )
            return null;

        if( sql.charAt(at) == '?'
                && !follows(sql, at, '?') ) {
            return null;
        }

        int end = at;

        while( end < sql.length()
                && PG_OPERATOR_CHARS.indexOf(sql.charAt(end)) >= 0 ) {
            char ch = sql.charAt(end);

            if( ch == '-' && follows(sql, end, '-') )
                break;

            if( ch == '/' && follows(sql, end, '*') )
                break;

            if( ch == '?' ) {
                if( !follows(sql, end, '?') )
                    break;

                end += 2;
                continue;
            }

            end++;
        }

        return end == at
                ? null
                : new Token(Kind.OPERATOR, at, end);
    }

    private static Token numberedParameter(String sql, int at) {
        if( at + 1 >= sql.length()
                || !digit(sql.charAt(at + 1)) ) {
            return null;
        }

        int end = at + 2;
        while( end < sql.length() && digit(sql.charAt(end)) )
            end++;

        return new Token(Kind.NUMBERED_PARAMETER, at, end);
    }

    private Token namedParameter(String sql, int at) {
        if( at + 1 >= sql.length()
                || !identifierStart(sql.charAt(at + 1)) ) {
            return null;
        }

        int end = at + 2;
        while( end < sql.length()
                && identifierPart(sql.charAt(end), oracle) ) {
            end++;
        }

        return new Token(Kind.NAMED_PARAMETER, at, end);
    }

    private static boolean numberStart(String sql, int at) {
        char ch = sql.charAt(at);

        return digit(ch)
                || (ch == '.'
                && at + 1 < sql.length()
                && digit(sql.charAt(at + 1))
                && (at == 0 || sql.charAt(at - 1) != '.'));
    }

    private static Token number(String sql, int at) {
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
                    && digit(sql.charAt(exponent)) ) {
                end = digitsEnd(sql, exponent);
            }
        }

        if( end < sql.length()
                && identifierPart(sql.charAt(end), true) ) {
            throw new IllegalArgumentException(
                    "Unsupported numeric literal at offset " + at);
        }

        return new Token(Kind.NUMBER, at, end);
    }

    private Token word(String sql, int at) {
        int end = at + 1;

        while( end < sql.length()
                && identifierPart(sql.charAt(end), oracle) ) {
            end++;
        }

        if( end < sql.length() && sql.charAt(end) == '\'' )
            throw new IllegalArgumentException(
                    "Unsupported quoted literal prefix at offset " + at);

        if( end - at == 1
                && (sql.charAt(at) == 'u' || sql.charAt(at) == 'U')
                && end + 1 < sql.length()
                && sql.charAt(end) == '&'
                && (sql.charAt(end + 1) == '\''
                || sql.charAt(end + 1) == '"') ) {
            throw new IllegalArgumentException(
                    "Unsupported Unicode quoted literal at offset " + at);
        }

        return new Token(Kind.WORD, at, end);
    }

    private static int digitsEnd(String sql, int at) {
        int end = at;
        while( end < sql.length() && digit(sql.charAt(end)) )
            end++;
        return end;
    }

    private static boolean identifierStart(char ch) {
        return Character.isLetter(ch) || ch == '_';
    }

    private static boolean identifierPart(char ch, boolean oracle) {
        return Character.isLetterOrDigit(ch)
                || ch == '_'
                || ch == '$'
                || (oracle && ch == '#');
    }

    private static boolean follows(String sql, int at, char next) {
        return at + 1 < sql.length()
                && sql.charAt(at + 1) == next;
    }

    private static boolean digit(char ch) {
        return ch >= '0' && ch <= '9';
    }

    private static int quotedEnd(
            String sql,
            int quoteAt,
            char quote
    ) {
        for( int i = quoteAt + 1; i < sql.length(); i++ ) {
            if( sql.charAt(i) != quote )
                continue;

            if( i + 1 < sql.length()
                    && sql.charAt(i + 1) == quote ) {
                i++;
                continue;
            }

            return i + 1;
        }

        throw unterminated("quoted text", quoteAt);
    }

    private static IllegalArgumentException unterminated(
            String what,
            int start
    ) {
        return new IllegalArgumentException(
                "Unterminated " + what + " at offset " + start);
    }

    private static IllegalArgumentException unsupported(
            String sql,
            int offset
    ) {
        return new IllegalArgumentException(
                "Unsupported SQL syntax at offset "
                        + offset + ": '" + sql.charAt(offset) + "'");
    }
}
