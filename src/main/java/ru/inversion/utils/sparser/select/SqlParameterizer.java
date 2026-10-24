package ru.inversion.utils.sparser.select;

import ru.inversion.utils.sparser.jdbc.Parameter;
import ru.inversion.utils.sparser.jdbc.PreparedSql;
import ru.inversion.utils.sparser.lexer.SqlScanner;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import static ru.inversion.utils.sparser.lexer.SqlScanner.Kind.*;

/**
 * Единая параметризация СОБРАННОЙ последовательности токенов.
 * Не выполняет повторное сканирование неизменной основы SELECT.
 * Не получает значения пользовательских параметров: это обязанность DataSet.
 */
final class SqlParameterizer {
    private SqlParameterizer() { }

    /** Ссылка на исходный текст токена; не копирует текст большого SELECT. */
    static final class Lexeme {
        final String source;
        final SqlScanner.Token token;

        Lexeme(String source, SqlScanner.Token token) {
            this.source = source;
            this.token = token;
        }

        SqlScanner.Kind kind() { return token.getKind(); }
        String text() { return token.text(source); }
        boolean word(String expected) { return token.isWord(source, expected); }
        boolean symbol(String expected) {
            return (kind() == SYMBOL || kind() == OPERATOR) && text().equals(expected);
        }
    }

    static PreparedSql prepare(List<Lexeme> tokens) {
        StringBuilder sql = new StringBuilder();
        List<Parameter> parameters = new ArrayList<>();
        ArrayDeque<Integer> castScopes = new ArrayDeque<>();
        ArrayDeque<Integer> orderScopes = new ArrayDeque<>();
        boolean[] legacyMarkers = legacyMarkers(tokens);
        boolean enabled = true;
        boolean legacyOff = false;
        int depth = 0;

        for (int i = 0; i < tokens.size(); i++) {
            Lexeme current = tokens.get(i);
            SqlScanner.Kind kind = current.kind();
            String text = current.text();

            // Директивы — обычные SQL-комментарии для драйвера, но управляющие
            // маркеры для нашей автопараметризации.
            if (kind == PARAMETERIZE_OFF) enabled = false;
            if (kind == PARAMETERIZE_ON) enabled = true;

            if (legacyMarkers[i]) {
                legacyOff = !legacyOff;
                continue; // парные legacy-разделители не попадают в JDBC SQL
            }

            if (current.symbol(")")) {
                while (!castScopes.isEmpty() && castScopes.peek() == depth) castScopes.pop();
                while (!orderScopes.isEmpty() && orderScopes.peek() >= depth) orderScopes.pop();
                depth--;
            }

            if (!orderScopes.isEmpty() && depth == orderScopes.peek()
                    && isOrderTerminator(tokens, i)) {
                orderScopes.pop();
            }

            if (current.word("ORDER") && isOrderStart(tokens, i)) {
                orderScopes.push(depth);
            }
            if (current.symbol("(")) {
                depth++;
                int previous = previousSignificant(tokens, i - 1);
                if ((previous >= 0 && tokens.get(previous).word("CAST"))
                        || isPostgresTypeModifier(tokens, previous)
                        || isTimeTypeName(tokens, previous)) {
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

    private static boolean isOrderStart(List<Lexeme> tokens, int i) {
        int next = nextSignificant(tokens, i + 1);
        if (next < tokens.size() && tokens.get(next).word("BY")) return true;
        return next < tokens.size() && tokens.get(next).word("SIBLINGS")
                && wordAt(tokens, nextSignificant(tokens, next + 1), "BY");
    }

    private static boolean isOrderTerminator(List<Lexeme> tokens, int i) {
        Lexeme t = tokens.get(i);
        return t.symbol(";") || t.word("LIMIT") || t.word("OFFSET")
                || t.word("FETCH") || t.word("UNION") || t.word("INTERSECT")
                || t.word("EXCEPT") || t.word("MINUS")
                || (t.word("FOR") && (wordAt(tokens, nextSignificant(tokens, i + 1), "UPDATE")
                || wordAt(tokens, nextSignificant(tokens, i + 1), "SHARE")
                || wordAt(tokens, nextSignificant(tokens, i + 1), "KEY")
                || wordAt(tokens, nextSignificant(tokens, i + 1), "NO")));
    }

    /**
     * Защита чисел внутри ::numeric(10, 2) и ::schema.numeric(10, 2).
     * Без неё подстановка ? меняет грамматику PostgreSQL type modifier.
     */
    private static boolean isPostgresTypeModifier(List<Lexeme> tokens, int typeName) {
        if (typeName < 0 || !isIdentifier(tokens.get(typeName))) return false;
        int p = previousSignificant(tokens, typeName - 1);
        while (p >= 0 && tokens.get(p).symbol(".")) {
            p = previousSignificant(tokens, p - 1);
            if (p < 0 || !isIdentifier(tokens.get(p))) return false;
            p = previousSignificant(tokens, p - 1);
        }
        return p >= 0 && tokens.get(p).symbol("::");
    }

    private static boolean isTimeTypeName(List<Lexeme> tokens, int i) {
        return wordAt(tokens, i, "TIME") || wordAt(tokens, i, "TIMESTAMP")
                || wordAt(tokens, i, "INTERVAL");
    }

    private static boolean isIdentifier(Lexeme lexeme) {
        return lexeme.kind() == WORD || lexeme.kind() == QUOTED_IDENTIFIER;
    }

    private static boolean typedLiteral(List<Lexeme> tokens, int i) {
        int previous = previousSignificant(tokens, i - 1);
        if (previous < 0) return false;
        Lexeme p = tokens.get(previous);
        if (p.word("DATE") || p.word("TIME") || p.word("TIMESTAMP") || p.word("INTERVAL")) {
            return true;
        }
        // PostgreSQL B'1010' / X'FF' и другие префиксные литералы без пробела.
        if ((p.word("B") || p.word("X") || p.word("N"))
                && p.token.getEnd() == tokens.get(i).token.getStart()
                && p.source == tokens.get(i).source) return true;
        // TIME [WITH|WITHOUT] TIME ZONE '...'; TIMESTAMP ...
        if (p.symbol(")")) {
            int balance = 0;
            for (int j = previous; j >= 0; j--) {
                if (tokens.get(j).symbol(")")) balance++;
                else if (tokens.get(j).symbol("(")) {
                    if (--balance == 0) {
                        return isTimeTypeName(tokens, previousSignificant(tokens, j - 1));
                    }
                }
            }
        }
        if (p.word("ZONE")) {
            int timeWord = previousSignificant(tokens, previous - 1);
            int qualifier = previousSignificant(tokens, timeWord - 1);
            int type = previousSignificant(tokens, qualifier - 1);
            return wordAt(tokens, timeWord, "TIME")
                    && (wordAt(tokens, qualifier, "WITH") || wordAt(tokens, qualifier, "WITHOUT"))
                    && (wordAt(tokens, type, "TIME") || wordAt(tokens, type, "TIMESTAMP"));
        }
        return false;
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

    private static int nextSignificant(List<Lexeme> tokens, int from) {
        int i = from;
        while (i < tokens.size() && isTrivia(tokens.get(i).kind())) i++;
        return i;
    }

    private static int previousSignificant(List<Lexeme> tokens, int from) {
        int i = from;
        while (i >= 0 && isTrivia(tokens.get(i).kind())) i--;
        return i;
    }

    private static boolean wordAt(List<Lexeme> tokens, int i, String word) {
        return i >= 0 && i < tokens.size() && tokens.get(i).word(word);
    }

    private static boolean isTrivia(SqlScanner.Kind kind) {
        return kind == WHITESPACE || kind == COMMENT || kind == HINT
                || kind == PARAMETERIZE_OFF || kind == PARAMETERIZE_ON;
    }

    /**
     * ~...~ — только при явном начале фрагмента. Это предотвращает разрушение
     * регулярных выражений PostgreSQL вида "column ~ 'pattern'".
     * Одиночный ~ и обычные SQL-операторы не изменяются.
     */
    private static boolean[] legacyMarkers(List<Lexeme> tokens) {
        boolean[] markers = new boolean[tokens.size()];
        for (int i = 0; i < tokens.size(); i++) {
            if (!tilde(tokens.get(i)) || !legacyOpening(tokens, i)) continue;
            for (int j = i + 1; j < tokens.size(); j++) {
                if (tilde(tokens.get(j)) && legacyClosing(tokens, j)) {
                    markers[i] = markers[j] = true;
                    i = j;
                    break;
                }
            }
        }
        return markers;
    }

    private static boolean tilde(Lexeme token) {
        return token.symbol("~");
    }

    private static boolean legacyOpening(List<Lexeme> tokens, int i) {
        int previous = previousSignificant(tokens, i - 1);
        if (previous < 0) return true;
        Lexeme p = tokens.get(previous);
        return p.symbol("(") || p.symbol(",") || p.symbol("=")
                || p.word("BY") || p.word("WHERE") || p.word("AND")
                || p.word("OR") || p.word("SELECT") || p.word("HAVING");
    }

    private static boolean legacyClosing(List<Lexeme> tokens, int i) {
        int next = nextSignificant(tokens, i + 1);
        if (next == tokens.size()) return true;
        Lexeme n = tokens.get(next);
        return n.symbol(")") || n.symbol(",") || n.symbol(";")
                || n.word("LIMIT") || n.word("OFFSET") || n.word("FETCH")
                || n.word("AND") || n.word("OR") || n.word("ORDER")
                || n.word("GROUP") || n.word("WHERE");
    }
}
