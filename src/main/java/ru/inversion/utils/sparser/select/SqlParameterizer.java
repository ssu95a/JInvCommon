package ru.inversion.utils.sparser.select;

import ru.inversion.utils.sparser.jdbc.Parameter;
import ru.inversion.utils.sparser.jdbc.PreparedSql;
import ru.inversion.utils.sparser.lexer.SqlScanner;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static ru.inversion.utils.sparser.lexer.SqlScanner.Kind.*;

/**
 * Единая параметризация уже собранной SQL-строки.
 * Не получает значения пользовательских параметров: это обязанность DataSet.
 */
final class SqlParameterizer {
    private final String source;
    private final List<SqlScanner.Token> tokens;
    private final boolean oracle;

    private SqlParameterizer(String source, List<SqlScanner.Token> tokens, boolean oracle) {
        this.source = Objects.requireNonNull(source, "source");
        this.tokens = Objects.requireNonNull(tokens, "tokens");
        this.oracle = oracle;
    }

    static PreparedSql prepare(String source, List<SqlScanner.Token> tokens, boolean oracle) {
        return new SqlParameterizer(source, tokens, oracle).compile();
    }

    private PreparedSql compile() {
        StringBuilder sql = new StringBuilder();
        List<Parameter> parameters = new ArrayList<>();
        ArrayDeque<Integer> typeScopes = new ArrayDeque<>();
        ArrayDeque<Integer> orderScopes = new ArrayDeque<>();
        boolean[] legacyMarkers = legacyMarkers(tokens);
        boolean[] legacyConcatMarkers = legacyConcatMarkers(tokens);
        boolean enabled = true;
        boolean legacyOff = false;
        int depth = 0;

        for (int i = 0; i < tokens.size(); i++) {
            SqlScanner.Token current = tokens.get(i);
            SqlScanner.Kind kind = current.getKind();
            String text = current.text(source);

            // Директивы — обычные SQL-комментарии для драйвера, но управляющие
            // маркеры для нашей автопараметризации.
            if (kind == PARAMETERIZE_OFF) enabled = false;
            if (kind == PARAMETERIZE_ON) enabled = true;

            if (legacyMarkers[i]) {
                legacyOff = !legacyOff;
                continue; // парные legacy-разделители не попадают в JDBC SQL
            }
            if (legacyConcatMarkers[i]) {
                // PostgreSQL lexer объединяет ~|| и ||~ в один OPERATOR.
                // Только для подтверждённой пары оставляем || и убираем ~.
                sql.append("||");
                legacyOff = !legacyOff;
                continue;
            }

            if (symbol(current, ")")) {
                while (!typeScopes.isEmpty() && typeScopes.peek() == depth) typeScopes.pop();
                while (!orderScopes.isEmpty() && orderScopes.peek() >= depth) orderScopes.pop();
                depth--;
            }

            if (!orderScopes.isEmpty() && depth == orderScopes.peek()
                    && isOrderTerminator(tokens, i)) {
                orderScopes.pop();
            }

            if (word(current, "ORDER") && isOrderStart(tokens, i)) {
                orderScopes.push(depth);
            }
            if (symbol(current, "(")) {
                depth++;
                int previous = previousSignificant(tokens, i - 1);
                if (isTypeModifier(tokens, previous)
                        || isTypePrecisionName(tokens, previous)) {
                    typeScopes.push(depth);
                }
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
                    parameters.add(Parameter.numbered(parseNumberedPosition(text)));
                    break;
                case STRING:
                    if (enabled && !legacyOff && typeScopes.isEmpty()
                            && orderScopes.isEmpty()
                            && canParameterizeString(i, current, text)) {
                        Object literal = literalValue(kind, text);
                        if (literal != null) {
                            sql.append('?');
                            parameters.add(Parameter.literal(literal));
                            break;
                        }
                    }
                    sql.append(text);
                    break;
                case NUMBER:
                    if (enabled && !legacyOff && typeScopes.isEmpty()
                            && orderScopes.isEmpty()) {
                        Object literal = literalValue(kind, text);
                        if (literal != null) {
                            sql.append('?');
                            parameters.add(Parameter.literal(literal));
                            break;
                        }
                    }
                    sql.append(text);
                    break;
                default:
                    sql.append(text);
            }
        }
        if (depth != 0) throw new IllegalArgumentException("Unbalanced SQL parentheses");
        return new PreparedSql(sql.toString(), parameters);
    }

    private static int parseNumberedPosition(String text) {
        try {
            int number = Integer.parseInt(text.substring(1));
            if (number < 1) throw new NumberFormatException("non-positive");
            return number;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid numbered parameter: " + text, ex);
        }
    }

    private boolean isOrderStart(List<SqlScanner.Token> tokens, int i) {
        int next = nextSignificant(tokens, i + 1);
        if (next < tokens.size() && word(tokens.get(next), "BY")) return true;
        return next < tokens.size() && word(tokens.get(next), "SIBLINGS")
                && wordAt(tokens, nextSignificant(tokens, next + 1), "BY");
    }

    private boolean isOrderTerminator(List<SqlScanner.Token> tokens, int i) {
        SqlScanner.Token t = tokens.get(i);
        return symbol(t, ";") || word(t, "LIMIT") || word(t, "OFFSET")
                || word(t, "FETCH") || word(t, "UNION") || word(t, "INTERSECT")
                || word(t, "EXCEPT") || word(t, "MINUS")
                || (word(t, "FOR") && (wordAt(tokens, nextSignificant(tokens, i + 1), "UPDATE")
                || wordAt(tokens, nextSignificant(tokens, i + 1), "SHARE")
                || wordAt(tokens, nextSignificant(tokens, i + 1), "KEY")
                || wordAt(tokens, nextSignificant(tokens, i + 1), "NO")));
    }

    /**
     * Защита чисел только внутри модификатора SQL-типа:
     * CAST(... AS numeric(10,2)), ::numeric(10,2),
     * character varying(32), bit varying(16), double precision(...).
     *
     * Само выражение CAST(...) намеренно НЕ защищается: его значения
     * должны параметризоваться как обычные SQL-литералы.
     */
    private boolean isTypeModifier(List<SqlScanner.Token> tokens, int typeName) {
        if (typeName < 0 || !isIdentifier(tokens.get(typeName))) return false;

        int p = previousSignificant(tokens, typeName - 1);

        // schema.type(...)
        while (p >= 0 && symbol(tokens.get(p), ".")) {
            p = previousSignificant(tokens, p - 1);
            if (p < 0 || !isIdentifier(tokens.get(p))) return false;
            p = previousSignificant(tokens, p - 1);
        }

        if (p >= 0 && (symbol(tokens.get(p), "::") || word(tokens.get(p), "AS"))) {
            return true;
        }

        // character varying(...), bit varying(...), double precision(...)
        if (p >= 0 && (word(tokens.get(typeName), "VARYING")
                || word(tokens.get(typeName), "PRECISION"))) {
            int preceding = p;
            boolean multiWordType =
                    (word(tokens.get(typeName), "VARYING")
                            && (wordAt(tokens, preceding, "CHARACTER")
                            || wordAt(tokens, preceding, "BIT")))
                    || (word(tokens.get(typeName), "PRECISION")
                            && wordAt(tokens, preceding, "DOUBLE"));

            if (multiWordType) {
                int prefix = previousSignificant(tokens, preceding - 1);
                return prefix >= 0
                        && (symbol(tokens.get(prefix), "::")
                        || word(tokens.get(prefix), "AS"));
            }
        }

        return false;
    }

    /**
     * Числа внутри модификаторов точности не являются значениями SQL-выражения.
     * Это намеренно консервативное правило: неизвестная функция YEAR(2025)
     * может остаться непараметризованной (false negative), но SQL не испортится.
     */
    private boolean isTypePrecisionName(List<SqlScanner.Token> tokens, int i) {
        return wordAt(tokens, i, "TIME") || wordAt(tokens, i, "TIMESTAMP")
                || wordAt(tokens, i, "INTERVAL")
                || wordAt(tokens, i, "CURRENT_TIME")
                || wordAt(tokens, i, "CURRENT_TIMESTAMP")
                || wordAt(tokens, i, "LOCALTIME")
                || wordAt(tokens, i, "LOCALTIMESTAMP")
                || wordAt(tokens, i, "YEAR") || wordAt(tokens, i, "MONTH")
                || wordAt(tokens, i, "DAY") || wordAt(tokens, i, "HOUR")
                || wordAt(tokens, i, "MINUTE") || wordAt(tokens, i, "SECOND");
    }

    private boolean isIdentifier(SqlScanner.Token lexeme) {
        return lexeme.getKind() == WORD || lexeme.getKind() == QUOTED_IDENTIFIER;
    }

    /**
     * Параметризуем только обычный SQL literal '...'.
     * Диалектные/prefixed forms (N'...', q'...', E'...', U&'...', $tag$...$)
     * и typed literals (DATE '...', TIMESTAMP '...', INTERVAL '...') оставляем
     * byte-for-byte: bind на их месте может изменить или сломать SQL grammar.
     */
    private boolean canParameterizeString(int index, SqlScanner.Token token, String text) {
        if (text.length() < 2 || text.charAt(0) != '\''
                || text.charAt(text.length() - 1) != '\'') {
            return false;
        }

        int previous = previousSignificant(tokens, index - 1);
        if (previous < 0) return true;

        SqlScanner.Token prev = tokens.get(previous);

        // B'...', X'...' and other prefix forms that scanner represents
        // as WORD immediately followed by a plain STRING token.
        if (prev.getKind() == WORD && prev.getEnd() == token.getStart())
            return false;

        return !(word(prev, "DATE")
                || word(prev, "TIME")
                || word(prev, "TIMESTAMP")
                || word(prev, "INTERVAL"));
    }

    /** Decode a plain SQL string literal to the JDBC value it represents. */
    private static Object literalValue(SqlScanner.Kind kind, String text) {
        if (kind == NUMBER) {
            try {
                String value = text.replace("_", "");
                if (value.length() > 2 && value.charAt(0) == '0') {
                    char prefix = Character.toLowerCase(value.charAt(1));
                    int radix = prefix == 'x' ? 16 : prefix == 'b' ? 2 : prefix == 'o' ? 8 : 0;
                    if (radix != 0) return new BigInteger(value.substring(2), radix);
                }
                return value.indexOf('.') >= 0 || value.indexOf('e') >= 0 || value.indexOf('E') >= 0
                        ? new BigDecimal(value) : new BigInteger(value);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        if (text.length() < 2 || text.charAt(0) != '\''
                || text.charAt(text.length() - 1) != '\'') {
            return null;
        }
        return text.substring(1, text.length() - 1).replace("''", "'");
    }

    private int nextSignificant(List<SqlScanner.Token> tokens, int from) {
        int i = from;
        while (i < tokens.size() && isTrivia(tokens.get(i).getKind())) i++;
        return i;
    }

    private int previousSignificant(List<SqlScanner.Token> tokens, int from) {
        int i = from;
        while (i >= 0 && isTrivia(tokens.get(i).getKind())) i--;
        return i;
    }

    private boolean wordAt(List<SqlScanner.Token> tokens, int i, String word) {
        return i >= 0 && i < tokens.size() && word(tokens.get(i), word);
    }

    private static boolean isTrivia(SqlScanner.Kind kind) {
        return kind == WHITESPACE || kind == COMMENT || kind == HINT
                || kind == PARAMETERIZE_OFF || kind == PARAMETERIZE_ON;
    }

    /**
     * Парные legacy ~...~. Один линейный проход, без поиска закрывающего
     * маркера до конца SQL для каждого кандидата.
     *
     * В PostgreSQL одиночный ~ может быть оператором: маркер принимается
     * только в ожидаемых позициях открытия/закрытия выражения.
     */
    private boolean[] legacyMarkers(List<SqlScanner.Token> tokens) {
        boolean[] markers = new boolean[tokens.size()];
        int opening = -1;

        // В Oracle одиночный ~ не является SQL-оператором. Поэтому любой
        // самостоятельный ~ вне строк/комментариев — граница legacy-защиты.
        // Не привязываемся к соседним токенам: после литералов и JDBC '?'
        // разделители встречаются в реальных concat-выражениях.
        if (oracle) {
            for (int i = 0; i < tokens.size(); i++) {
                if (!tilde(tokens.get(i))) continue;
                markers[i] = true;
                if (opening < 0) opening = i;
                else opening = -1;
            }
            if (opening >= 0) {
                throw new IllegalArgumentException(
                        "Unpaired Oracle legacy ~ marker at offset "
                        + tokens.get(opening).getStart());
            }
            return markers;
        }

        // PostgreSQL: ~ и !~ — действительные операторы, поэтому
        // существующую контекстную эвристику сохраняем без изменений.
        for (int i = 0; i < tokens.size(); i++) {
            if (!tilde(tokens.get(i))) continue;
            if (opening >= 0 && legacyClosing(tokens, i)) {
                markers[opening] = true;
                markers[i] = true;
                opening = -1;
            } else if (opening < 0 && legacyOpening(tokens, i)) {
                opening = i;
            }
        }
        // Unmatched opening is left verbatim: it may be a PostgreSQL operator.
        return markers;
    }

    /**
     * PostgreSQL: специальный legacy-шаблон ~|| ... ||~.
     * Scanner правильно распознаёт их как цельные операторы, поэтому обычный
     * поиск отдельного ~ не срабатывает. Обрабатываем только парные маркеры.
     * Одиночные ~|| и ||~ могут быть пользовательскими PG-операторами:
     * оставляем их без изменений.
     */
    private boolean[] legacyConcatMarkers(List<SqlScanner.Token> tokens) {
        boolean[] markers = new boolean[tokens.size()];
        if (oracle) return markers;

        int opening = -1;
        for (int i = 0; i < tokens.size(); i++) {
            SqlScanner.Token token = tokens.get(i);
            if (symbol(token, "~||")) {
                if (opening < 0) opening = i;
            } else if (symbol(token, "||~") && opening >= 0) {
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

    private boolean legacyOpening(List<SqlScanner.Token> tokens, int i) {
        int previous = previousSignificant(tokens, i - 1);
        if (previous < 0) return true;
        SqlScanner.Token p = tokens.get(previous);
        return symbol(p, "(") || symbol(p, ",") || symbol(p, "=")
                || word(p, "BY") || word(p, "WHERE") || word(p, "AND")
                || word(p, "OR") || word(p, "SELECT") || word(p, "HAVING");
    }

    private boolean legacyClosing(List<SqlScanner.Token> tokens, int i) {
        int next = nextSignificant(tokens, i + 1);
        if (next == tokens.size()) return true;
        SqlScanner.Token n = tokens.get(next);
        return symbol(n, ")") || symbol(n, ",") || symbol(n, ";")
                || word(n, "AS") || word(n, "FROM")
                || word(n, "ASC") || word(n, "DESC") || word(n, "NULLS")
                || word(n, "COLLATE")
                || word(n, "LIMIT") || word(n, "OFFSET") || word(n, "FETCH")
                || word(n, "AND") || word(n, "OR") || word(n, "ORDER")
                || word(n, "GROUP") || word(n, "WHERE");
    }
    private boolean word(SqlScanner.Token token, String expected) {
        return token.isWord(source, expected);
    }

    private boolean symbol(SqlScanner.Token token, String expected) {
        SqlScanner.Kind kind = token.getKind();
        return (kind == SYMBOL || kind == OPERATOR) && token.text(source).equals(expected);
    }

}
