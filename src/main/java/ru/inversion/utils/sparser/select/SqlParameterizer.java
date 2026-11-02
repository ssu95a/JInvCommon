package ru.inversion.utils.sparser.select;

import ru.inversion.utils.sparser.jdbc.Parameter;
import ru.inversion.utils.sparser.jdbc.PreparedSql;
import ru.inversion.utils.sparser.lexer.SqlKeyword;
import ru.inversion.utils.sparser.lexer.SqlScanner;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static ru.inversion.utils.sparser.lexer.SqlScanner.Kind.*;
import static ru.inversion.utils.sparser.lexer.SqlKeyword.*;

/**
 * Единая параметризация уже собранной SQL-строки.
 * Не получает значения пользовательских параметров: это обязанность DataSet.
 */
final class SqlParameterizer {

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

    static PreparedSql prepare(
            String source,
            List<SqlScanner.Token> tokens,
            boolean oracle
    ) {
        return new SqlParameterizer(source, tokens, oracle).compile();
    }

    private PreparedSql compile() {
        StringBuilder sql = new StringBuilder();
        List<Parameter> parameters = new ArrayList<>();

        boolean[] legacyMarkers = legacyMarkers();
        boolean[] legacyConcatMarkers = legacyConcatMarkers();

        boolean parameterize = true;
        boolean legacyProtected = false;

        int depth = 0;
        int typeDepth = -1;
        int orderDepth = -1;

        for (int i = 0; i < tokens.size(); i++) {
            SqlScanner.Token token = tokens.get(i);
            SqlScanner.Kind kind = token.kind();
            String text = token.text(source);

            if (kind == PARAMETERIZE_OFF)
                parameterize = false;
            else if (kind == PARAMETERIZE_ON)
                parameterize = true;

            if (legacyMarkers[i]) {
                legacyProtected = !legacyProtected;
                continue;
            }

            if (legacyConcatMarkers[i]) {
                sql.append("||");
                legacyProtected = !legacyProtected;
                continue;
            }

            if (symbol(token, ")")) {
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
                    sql.append('?');
                    parameters.add(Parameter.named(text.substring(1)));
                    break;

                case NUMBERED_PARAMETER:
                    sql.append('?');
                    parameters.add(Parameter.numbered(
                            parseNumberedPosition(text)));
                    break;

                case STRING:
                case NUMBER:
                    if (parameterize
                            && !legacyProtected
                            && typeDepth < 0
                            && orderDepth < 0) {
                        sql.append('?');
                        parameters.add(Parameter.literal(
                                literalValue(kind, text)));
                    }
                    else {
                        sql.append(text);
                    }
                    break;

                default:
                    sql.append(text);
            }
        }

        if (depth != 0)
            throw new IllegalArgumentException("Unbalanced SQL parentheses");

        return new PreparedSql(sql.toString(), parameters);
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
     * Legacy ~...~ protection.
     * Oracle treats standalone ~ only as our legacy marker.
     * PostgreSQL also has a real ~ operator, so marker recognition is contextual.
     */
    private boolean[] legacyMarkers() {
        boolean[] markers = new boolean[tokens.size()];
        int opening = -1;

        if (oracle) {
            for (int i = 0; i < tokens.size(); i++) {
                if (!tilde(tokens.get(i)))
                    continue;

                markers[i] = true;
                opening = opening < 0 ? i : -1;
            }

            if (opening >= 0) {
                throw new IllegalArgumentException(
                        "Unpaired Oracle legacy ~ marker at offset "
                                + tokens.get(opening).start());
            }

            return markers;
        }

        for (int i = 0; i < tokens.size(); i++) {
            if (!tilde(tokens.get(i)))
                continue;

            if (opening >= 0 && legacyClosing(i)) {
                markers[opening] = true;
                markers[i] = true;
                opening = -1;
            }
            else if (opening < 0 && legacyOpening(i)) {
                opening = i;
            }
        }

        return markers;
    }

    /**
     * PostgreSQL lexer keeps ~|| and ||~ as whole operator tokens.
     * For a confirmed pair we keep || and strip only the legacy ~ marker.
     */
    private boolean[] legacyConcatMarkers() {
        boolean[] markers = new boolean[tokens.size()];

        if (oracle)
            return markers;

        int opening = -1;

        for (int i = 0; i < tokens.size(); i++) {
            SqlScanner.Token token = tokens.get(i);

            if (symbol(token, "~||")) {
                if (opening < 0)
                    opening = i;
            }
            else if (symbol(token, "||~") && opening >= 0) {
                markers[opening] = true;
                markers[i] = true;
                opening = -1;
            }
        }

        return markers;
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
                && token.text(source).equals(expected);
    }
}
