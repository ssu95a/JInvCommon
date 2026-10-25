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
        ArrayDeque<Integer> castScopes = new ArrayDeque<>();
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
                while (!castScopes.isEmpty() && castScopes.peek() == depth) castScopes.pop();
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
                if ((previous >= 0 && word(tokens.get(previous), "CAST"))
                        || isPostgresTypeModifier(tokens, previous)
                        || isTypePrecisionName(tokens, previous)) {
                    castScopes.push(depth);
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
                case NUMBER:
                    if (enabled && !legacyOff && castScopes.isEmpty()
                            && orderScopes.isEmpty() && !typedLiteral(tokens, i)) {
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
     * Защита чисел в модификаторах PostgreSQL-типа: ::numeric(10, 2),
     * ::schema.numeric(10, 2), ::character varying(32), ::bit varying(16).
     * Без неё подстановка ? меняет SQL type syntax.
     */
    private boolean isPostgresTypeModifier(List<SqlScanner.Token> tokens, int typeName) {
        if (typeName < 0 || !isIdentifier(tokens.get(typeName))) return false;
        int p = previousSignificant(tokens, typeName - 1);
        while (p >= 0 && symbol(tokens.get(p), ".")) {
            p = previousSignificant(tokens, p - 1);
            if (p < 0 || !isIdentifier(tokens.get(p))) return false;
            p = previousSignificant(tokens, p - 1);
        }
        if (p >= 0 && symbol(tokens.get(p), "::")) return true;
        // PostgreSQL: ::character varying(32), ::bit varying(16),
        // ::double precision(...). Перед :: находится первое слово типа.
        if (p >= 0 && (word(tokens.get(typeName), "VARYING")
                || word(tokens.get(typeName), "PRECISION"))) {
            int preceding = p;
            if ((word(tokens.get(typeName), "VARYING")
                    && (wordAt(tokens, preceding, "CHARACTER") || wordAt(tokens, preceding, "BIT")))
                    || (word(tokens.get(typeName), "PRECISION")
                    && wordAt(tokens, preceding, "DOUBLE"))) {
                int cast = previousSignificant(tokens, preceding - 1);
                return cast >= 0 && symbol(tokens.get(cast), "::");
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

    private boolean typedLiteral(List<SqlScanner.Token> tokens, int i) {
        int previous = previousSignificant(tokens, i - 1);
        if (previous < 0) return false;
        SqlScanner.Token p = tokens.get(previous);
        if (word(p, "DATE") || word(p, "TIME") || word(p, "TIMESTAMP")
                || word(p, "INTERVAL") || word(p, "UESCAPE")) {
            return true;
        }
        // PostgreSQL B'1010' / X'FF' и другие префиксные литералы без пробела.
        if ((word(p, "B") || word(p, "X") || word(p, "N"))
                && p.getEnd() == tokens.get(i).getStart()) return true;
        // TIME [WITH|WITHOUT] TIME ZONE '...'; TIMESTAMP ...
        if (symbol(p, ")")) {
            return isDateTimeTypeBefore(tokens, previous) || isIntervalTypeBefore(tokens, previous);
        }
        if (word(p, "ZONE")) {
            int timeWord = previousSignificant(tokens, previous - 1);
            int qualifier = previousSignificant(tokens, timeWord - 1);
            int typeEnd = previousSignificant(tokens, qualifier - 1);
            return wordAt(tokens, timeWord, "TIME")
                    && (wordAt(tokens, qualifier, "WITH") || wordAt(tokens, qualifier, "WITHOUT"))
                    && isDateTimeTypeBefore(tokens, typeEnd);
        }
        return false;
    }

    /** TIME / TIMESTAMP, с необязательной точностью (3) перед WITH TIME ZONE. */
    private boolean isDateTimeTypeBefore(List<SqlScanner.Token> tokens, int last) {
        if (wordAt(tokens, last, "TIME") || wordAt(tokens, last, "TIMESTAMP")) {
            return true;
        }
        if (last < 0 || !symbol(tokens.get(last), ")")) return false;
        int opening = matchingOpeningParen(tokens, last);
        if (opening < 0) return false;
        int name = previousSignificant(tokens, opening - 1);
        return wordAt(tokens, name, "TIME") || wordAt(tokens, name, "TIMESTAMP");
    }

    private boolean isIntervalTypeBefore(List<SqlScanner.Token> tokens, int last) {
        if (last < 0 || !symbol(tokens.get(last), ")")) return false;
        int opening = matchingOpeningParen(tokens, last);
        return opening >= 0 && wordAt(tokens, previousSignificant(tokens, opening - 1), "INTERVAL");
    }

    private int matchingOpeningParen(List<SqlScanner.Token> tokens, int close) {
        int depth = 0;
        for (int i = close; i >= 0; i--) {
            if (symbol(tokens.get(i), ")")) depth++;
            else if (symbol(tokens.get(i), "(") && --depth == 0) return i;
        }
        return -1;
    }

    /**
     * Неизвестную строковую форму оставляем дословно, без попытки угадать
     * правила декодирования (особенно E'', U&'', backslash escapes).
     */
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
        if (text.length() < 2) return null;
        if (text.charAt(0) == '\'') {
            // standard_conforming_strings на PostgreSQL может быть OFF.
            // Без доступа к серверной настройке обратный слэш неоднозначен.
            if (text.indexOf('\\') >= 0) return null;
            return text.substring(1, text.length() - 1).replace("''", "'");
        }
        // N'...' и NQ'...' содержат национальные строки Oracle.
        // Parameter не умеет передавать NCHAR/NVARCHAR2 JDBC type: не меняем тип.
        if (text.charAt(0) == 'N' || text.charAt(0) == 'n') return null;
        // Oracle q'/nq' quoting. Внутренние кавычки не экранированы.
        int quote = text.indexOf('\'');
        if (quote >= 0 && quote + 1 < text.length() && (text.charAt(0) == 'q'
                || text.charAt(0) == 'Q' || ((text.charAt(0) == 'n' || text.charAt(0) == 'N')
                && text.length() > 1 && (text.charAt(1) == 'q' || text.charAt(1) == 'Q')))) {
            return text.substring(quote + 2, text.length() - 2);
        }
        // PostgreSQL dollar-quoted string: $tag$...$tag$ / $$...$$.
        if (text.charAt(0) == '$') {
            int end = text.indexOf('$', 1);
            if (end > 0) return text.substring(end + 1, text.length() - (end + 1));
        }
        return null;
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
