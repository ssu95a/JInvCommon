package ru.inversion.dataset.parser.select;

import ru.inversion.dataset.parser.jdbc.Parameter;
import ru.inversion.dataset.parser.jdbc.PreparedSql;
import ru.inversion.dataset.parser.lexer.SqlKeyword;
import ru.inversion.dataset.parser.lexer.SqlScanner;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static ru.inversion.dataset.parser.lexer.SqlScanner.Kind.*;
import static ru.inversion.dataset.parser.lexer.SqlKeyword.*;

/**
 * Единая параметризация уже собранной SQL-строки.
 * Не получает значения пользовательских параметров: это обязанность DataSet.
 */
public final class SqlParameterizer {

    private final String source;
    private final List<SqlScanner.Token> tokens;
    private final boolean oracle;

    private SqlParameterizer(
            String source,
            List<SqlScanner.Token> tokens,
            boolean oracle
    ) {
        this.source = Objects.requireNonNull(source, "source");
        this.tokens = Objects.requireNonNull(tokens, "tokens");
        this.oracle = oracle;
    }

    /**
     * Parameterizes supported SQL/JDBC text using the supplied dialect scanner.
     * Unlike {@link SqlSelect}, this entry point does not require a SELECT and
     * performs no structural changes.
     */
    public static PreparedSql prepare(String source, SqlScanner scanner) {
        Objects.requireNonNull(scanner, "scanner");
        return prepare(
                source,
                scanner.scan(source),
                scanner instanceof ru.inversion.dataset.parser.lexer.OracleSqlScanner
        );
    }

    static PreparedSql prepare(
            String source,
            List<SqlScanner.Token> tokens,
            boolean oracle
    ) {
        return prepare(source, tokens, oracle, Collections.emptyList());
    }

    static PreparedSql prepare(String source, List<SqlScanner.Token> tokens, boolean oracle,
                               List<NamedSourceRange> namedSources) {
        return prepare(source, tokens, oracle, namedSources, null);
    }

    static PreparedSql prepare(String source, List<SqlScanner.Token> tokens, boolean oracle,
                               List<NamedSourceRange> namedSources, OrderOffsets order) {
        return new SqlParameterizer(source, tokens, oracle).compile(namedSources, order);
    }

    /** Директивы и старые маркеры могут связывать параметризацию соседних фрагментов. */
    static boolean mayChangeParameterization(String source, SqlScanner.Token token) {
        SqlScanner.Kind kind = token.kind();
        return kind == PARAMETERIZE_ON || kind == PARAMETERIZE_OFF
                || ((kind == OPERATOR || kind == SYMBOL)
                    && (source.charAt(token.start()) == '~' || source.charAt(token.end() - 1) == '~'));
    }

    private PreparedSql compile(List<NamedSourceRange> namedSources, OrderOffsets order) {
        StringBuilder sql = new StringBuilder();
        List<Parameter> parameters = new ArrayList<>();
        int namedSourceIndex = 0;

        boolean parameterize = true;

        int legacyClose = -1;
        int legacyConcatClose = -1;

        int depth = 0;
        int typeDepth = -1;
        int orderDepth = -1;

        for (int i = 0; i < tokens.size(); i++) {
            SqlScanner.Token token = tokens.get(i);
            SqlScanner.Kind kind = token.kind();

            if (order != null && order.reusable) {
                order.reusable = !mayChangeParameterization(source, token);
                order.capture(token.start(), token.end(), kind == WHITESPACE, sql.length(), parameters.size());
                if (!order.hasTail && token.start() >= order.end && !isTrivia(kind)) {
                    order.hasTail = true;
                    order.reusable &= isOrderTerminator(i);
                }
            }

            if (kind == PARAMETERIZE_OFF)
                parameterize = false;
            else if (kind == PARAMETERIZE_ON)
                parameterize = true;

            if (i == legacyClose) {
                legacyClose = -1;
                continue;
            }

            if (i == legacyConcatClose) {
                legacyConcatClose = -1;
                sql.append("||");
                continue;
            }

            if (legacyClose < 0 && tilde(token)) {
                int close = oracle
                        ? nextTilde(i + 1)
                        : legacyOpening(i)
                            ? nextLegacyClosingTilde(i + 1)
                            : -1;

                if (oracle && close < 0) {
                    throw new IllegalArgumentException(
                            "Unpaired Oracle legacy ~ marker at offset "
                                    + token.start());
                }

                if (close >= 0) {
                    legacyClose = close;
                    continue;
                }
            }

            if (!oracle
                    && legacyConcatClose < 0
                    && symbol(token, "~||")) {
                int close = nextSymbol(i + 1, "||~");

                if (close >= 0) {
                    legacyConcatClose = close;
                    sql.append("||");
                    continue;
                }
            }

            if (symbol(token, ")")) {
                if (depth == 0)
                    throw new IllegalArgumentException(
                            "Unbalanced SQL parentheses at offset "
                                    + token.start());

                if (typeDepth == depth)
                    typeDepth = -1;

                if (orderDepth == depth)
                    orderDepth = -1;

                depth--;
            }

            if (orderDepth == depth && isOrderTerminator(i))
                orderDepth = -1;

            if (orderDepth < 0
                    && keyword(token, ORDER)
                    && isOrderStart(i)) {
                orderDepth = depth;
            }

            if (symbol(token, "(")) {
                int previous = previousSignificant(i - 1);

                if (unsupportedPrecisionCall(previous)) {
                    throw new IllegalArgumentException(
                            "Unsupported SQL precision syntax at offset "
                                    + token.start());
                }

                depth++;

                if (typeDepth < 0 && isTypeArgumentsStart(previous))
                    typeDepth = depth;
            }

            switch (kind) {
                case POSITIONAL_PARAMETER:
                    sql.append('?');
                    parameters.add(Parameter.positional());
                    break;

                case NAMED_PARAMETER:
                    int offset = token.start();
                    while (namedSourceIndex < namedSources.size()
                            && namedSources.get(namedSourceIndex).end <= offset)
                        namedSourceIndex++;
                    int sourceId = Parameter.GLOBAL_SOURCE;
                    if (namedSourceIndex < namedSources.size()) {
                        NamedSourceRange range = namedSources.get(namedSourceIndex);
                        if (range.start <= offset)
                            sourceId = range.sourceId;
                    }
                    sql.append('?');
                    parameters.add(Parameter.named(source.substring(offset + 1, token.end()), sourceId));
                    break;

                case NUMBERED_PARAMETER:
                    sql.append('?');
                    parameters.add(Parameter.numbered(
                            parseNumberedPosition(token.text(source))));
                    break;

                case STRING:
                case NUMBER:
                    boolean legacyProtected =
                            (legacyClose >= 0) != (legacyConcatClose >= 0);

                    if (parameterize
                            && !legacyProtected
                            && typeDepth < 0
                            && orderDepth < 0) {
                        sql.append('?');
                        parameters.add(Parameter.literal(
                                literalValue(kind, token.text(source))));
                    }
                    else {
                        sql.append(source, token.start(), token.end());
                    }
                    break;

                default:
                    sql.append(source, token.start(), token.end());
            }
        }

        if (depth != 0)
            throw new IllegalArgumentException("Unbalanced SQL parentheses");

        if (order != null)
            order.capture(source.length(), source.length() + 1, false, sql.length(), parameters.size());

        return new PreparedSql(sql.toString(), parameters);
    }

    /** Границы сортировки в исходном SQL, JDBC-тексте и списке параметров. */
    static final class OrderOffsets {
        int start;
        int end;
        int sqlStart = -1;
        int sqlEnd = -1;
        int firstParameter;
        int afterLastParameter;
        boolean reusable = true;
        private boolean hasTail;

        void capture(int from, int to, boolean whitespace, int sqlLength, int parameterCount) {
            if (start >= from && start < to) {
                reusable &= start == from || whitespace;
                sqlStart = sqlLength + start - from;
                firstParameter = parameterCount;
            }
            if (end >= from && end < to) {
                reusable &= end == from || whitespace;
                sqlEnd = sqlLength + end - from;
                afterLastParameter = parameterCount;
            }
        }
    }

    /** Диапазон исходного фрагмента до преобразования SQL в JDBC-текст. */
    static final class NamedSourceRange {
        final int start;
        final int end;
        final int sourceId;

        NamedSourceRange(int start, int end, int sourceId) {
            this.start = start;
            this.end = end;
            this.sourceId = sourceId;
        }
    }

    private static int parseNumberedPosition(String text) {
        try {
            int number = Integer.parseInt(text.substring(1));

            if (number < 1)
                throw new NumberFormatException("non-positive");

            return number;
        }
        catch (NumberFormatException ex) {
            throw new IllegalArgumentException(
                    "Invalid numbered parameter: " + text, ex);
        }
    }

    private boolean isOrderStart(int i) {
        int next = nextSignificant(i + 1);

        if (next < tokens.size() && keyword(tokens.get(next), BY))
            return true;

        return next < tokens.size()
                && keyword(tokens.get(next), SIBLINGS)
                && keywordAt(nextSignificant(next + 1), BY);
    }

    private boolean isOrderTerminator(int i) {
        SqlScanner.Token token = tokens.get(i);

        return symbol(token, ";")
                || in(
                        keyword(token),
                        LIMIT, OFFSET, FETCH,
                        UNION, INTERSECT, EXCEPT, MINUS
                )
                || (keyword(token, FOR)
                && in(
                        keywordAt(nextSignificant(i + 1)),
                        UPDATE, SHARE, KEY, NO
                ));
    }

    /**
     * '(' starts SQL type arguments only when the type belongs to CAST(... AS ...)
     * or PostgreSQL ::type(...). Qualified and multi-word type names are allowed.
     */
    private boolean isTypeArgumentsStart(int typeName) {
        if (typeName < 0 || !isIdentifier(tokens.get(typeName)))
            return false;

        int i = previousSignificant(typeName - 1);

        while (i >= 0) {
            SqlScanner.Token token = tokens.get(i);

            if (symbol(token, "::") || keyword(token, AS))
                return true;

            if (symbol(token, ".") || isIdentifier(token)) {
                i = previousSignificant(i - 1);
                continue;
            }

            return false;
        }

        return false;
    }

    /**
     * Precision on SQL current-time expressions is valid SQL grammar, not
     * an application value. It is outside the intentionally small contract.
     */
    private boolean unsupportedPrecisionCall(int name) {
        return in(
                keywordAt(name),
                CURRENT_TIME,
                CURRENT_TIMESTAMP,
                LOCALTIME,
                LOCALTIMESTAMP
        );
    }

    private boolean isIdentifier(SqlScanner.Token token) {
        return token.kind() == WORD
                || token.kind() == QUOTED_IDENTIFIER;
    }

    private static Object literalValue(
            SqlScanner.Kind kind,
            String text
    ) {
        if (kind == STRING) {
            return text.substring(1, text.length() - 1)
                    .replace("''", "'");
        }

        try {
            return text.indexOf('.') >= 0
                    || text.indexOf('e') >= 0
                    || text.indexOf('E') >= 0
                    ? new BigDecimal(text)
                    : new BigInteger(text);
        }
        catch (NumberFormatException ex) {
            throw new IllegalArgumentException(
                    "Invalid numeric literal: " + text, ex);
        }
    }

    private int nextSignificant(int from) {
        int i = from;

        while (i < tokens.size()
                && isTrivia(tokens.get(i).kind()))
            i++;

        return i;
    }

    private int previousSignificant(int from) {
        int i = from;

        while (i >= 0
                && isTrivia(tokens.get(i).kind()))
            i--;

        return i;
    }

    private SqlKeyword keyword(SqlScanner.Token token) {
        return token == null
                ? null
                : token.keyword(source);
    }

    private SqlKeyword keywordAt(int i) {
        return i >= 0 && i < tokens.size()
                ? keyword(tokens.get(i))
                : null;
    }

    private boolean keywordAt(int i, SqlKeyword expected) {
        return i >= 0
                && i < tokens.size()
                && keyword(tokens.get(i), expected);
    }

    private static boolean isTrivia(SqlScanner.Kind kind) {
        return kind == WHITESPACE
                || kind == COMMENT
                || kind == HINT
                || kind == PARAMETERIZE_OFF
                || kind == PARAMETERIZE_ON;
    }

    /**
     * Find the matching marker only when a candidate opening is encountered.
     * This keeps real PostgreSQL ~ operators untouched unless a full legacy
     * pair is confirmed.
     */
    private int nextTilde(int from) {
        for (int i = from; i < tokens.size(); i++) {
            if (tilde(tokens.get(i)))
                return i;
        }

        return -1;
    }

    private int nextLegacyClosingTilde(int from) {
        for (int i = from; i < tokens.size(); i++) {
            if (tilde(tokens.get(i)) && legacyClosing(i))
                return i;
        }

        return -1;
    }

    private int nextSymbol(int from, String expected) {
        for (int i = from; i < tokens.size(); i++) {
            if (symbol(tokens.get(i), expected))
                return i;
        }

        return -1;
    }

    private boolean tilde(SqlScanner.Token token) {
        return symbol(token, "~");
    }

    private boolean legacyOpening(int i) {
        if (i + 1 < tokens.size()
                && tokens.get(i).end() == tokens.get(i + 1).start()
                && keywordAt(i + 1, WHERE)) {
            return true;
        }

        int previous = previousSignificant(i - 1);

        if (previous < 0)
            return true;

        SqlScanner.Token token = tokens.get(previous);

        return token.kind() == OPERATOR
                || symbol(token, "(")
                || symbol(token, ",")
                || symbol(token, "=")
                || keyword(token, BY)
                || keyword(token, WHERE)
                || keyword(token, AND)
                || keyword(token, OR)
                || keyword(token, SELECT)
                || keyword(token, HAVING);
    }

    private boolean legacyClosing(int i) {
        int next = nextSignificant(i + 1);

        if (next == tokens.size())
            return true;

        SqlScanner.Token token = tokens.get(next);

        return token.kind() == OPERATOR
                || symbol(token, ")")
                || symbol(token, ",")
                || symbol(token, ";")
                || in(
                        keyword(token),
                        AS, FROM, ASC, DESC, NULLS, COLLATE,
                        LIMIT, OFFSET, FETCH,
                        AND, OR, ORDER, GROUP, WHERE
                );
    }

    private boolean keyword(
            SqlScanner.Token token,
            SqlKeyword expected
    ) {
        return token.isKeyword(source, expected);
    }

    private boolean symbol(
            SqlScanner.Token token,
            String expected
    ) {
        SqlScanner.Kind kind = token.kind();

        return (kind == SYMBOL || kind == OPERATOR)
                && token.end() - token.start() == expected.length()
                && source.startsWith(expected, token.start());
    }
}
