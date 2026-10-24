package ru.inversion.utils.sparser.select;

import ru.inversion.utils.sparser.lexer.SqlScanner;
import ru.inversion.utils.sparser.jdbc.Parameter;
import ru.inversion.utils.sparser.jdbc.PreparedSql;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import static ru.inversion.utils.sparser.lexer.SqlScanner.Kind.*;

/**
 * Структурные изменения одного внешнего SELECT без дополнительной SQL-обёртки.
 *
 * Исходный запрос лексируется ровно один раз. Меняются только переданные
 * фрагменты. Готовая строка строится по требованию и кешируется до изменения.
 *
 * prepare() выполняет единую автопараметризацию собранного выражения
 * с использованием ранее распознанных токенов исходного запроса.
 */
public final class SqlSelect {

    private final String source;
    private final SqlScanner scanner;
    private final List<SqlScanner.Token> sourceTokens;
    private final int selectItemsEnd;
    private final int whereWordEnd;
    private final int wherePredicateEnd;
    private final int whereInsertion;
    private final int orderStart;
    private final int orderEnd;
    private final int orderInsertion;
    private final boolean compound;
    private final boolean orderSiblings;

    private List<String> predicates = Collections.emptyList();
    private String orderBy;
    private String markExpression;
    private String markAlias;
    private String builtSql;
    private PreparedSql preparedSql;

    public SqlSelect(String sql, SqlScanner scanner) {
        source = Objects.requireNonNull(sql, "sql");
        this.scanner = Objects.requireNonNull(scanner, "scanner");
        sourceTokens = scanner.scan(sql);
        Layout layout = inspect();
        selectItemsEnd = layout.selectItemsEnd;
        whereWordEnd = layout.whereWordEnd;
        wherePredicateEnd = layout.wherePredicateEnd;
        whereInsertion = layout.whereInsertion;
        orderStart = layout.orderStart;
        orderEnd = layout.orderEnd;
        orderInsertion = layout.orderInsertion;
        compound = layout.compound;
        orderSiblings = layout.orderSiblings;
    }

    public void setPredicates(List<String> values) {
        Objects.requireNonNull(values, "values");
        List<String> copy = new ArrayList<>(values.size());
        for (String value : values) {
            String part = requiredFragment(value, "predicate");
            checkFragment(part);
            copy.add(part);
        }
        if (!copy.isEmpty()) requireSimple("WHERE predicates");
        predicates = Collections.unmodifiableList(copy);
        invalidate();
    }

    /** null означает отсутствие runtime-сортировки: исходный ORDER BY сохраняется. */
    public void setOrderBy(String expression) {
        String value = optionalFragment(expression);
        if (value != null) {
            requireSimple("ORDER BY replacement");
            if (orderSiblings) {
                throw new UnsupportedOperationException(
                        "Replacing ORDER SIBLINGS BY is not supported");
            }
            checkFragment(value);
        }
        orderBy = value;
        invalidate();
    }

    /** Mark всегда добавляется последним элементом SELECT list. null удаляет mark. */
    public void setMark(String expression, String alias) {
        String value = optionalFragment(expression);
        if (value != null) {
            requireSimple("SELECT mark");
            checkFragment(value);
            if (alias == null) throw new IllegalArgumentException("mark alias is required");
            String actualAlias = alias.trim();
            List<SqlScanner.Token> aliasTokens = scanner.scan(actualAlias);
            if (aliasTokens.size() != 1 ||
                    (aliasTokens.get(0).getKind() != WORD &&
                     aliasTokens.get(0).getKind() != QUOTED_IDENTIFIER)) {
                throw new IllegalArgumentException("mark alias must be one SQL identifier");
            }
            markAlias = actualAlias;
        } else {
            markAlias = null;
        }
        markExpression = value;
        invalidate();
    }

    /** Исходная строка возвращается byte-for-byte, пока не задано изменений. */
    public String buildSql() {
        if (builtSql != null) return builtSql;
        if (predicates.isEmpty() && orderBy == null && markExpression == null) {
            return builtSql = source;
        }

        List<Edit> edits = edits();
        Collections.sort(edits, Comparator.comparingInt((Edit e) -> e.start)
                .thenComparingInt(e -> e.priority));

        StringBuilder result = new StringBuilder(source.length() + 128);
        int copiedUntil = 0;
        for (Edit edit : edits) {
            if (edit.start < copiedUntil) {
                throw new IllegalStateException("Overlapping SQL edits at " + edit.start);
            }
            result.append(source, copiedUntil, edit.start);
            result.append(edit.replacement);
            copiedUntil = edit.end;
        }
        result.append(source, copiedUntil, source.length());
        return builtSql = result.toString();
    }

    /**
     * Готовит SQL для JDBC. Результат (SQL + порядок источников значений)
     * неизменяем и кешируется до любой структурной правки.
     * Runtime-значения IParameters не хранятся и не разрешаются здесь.
     */
    public PreparedSql prepare() {
        if (preparedSql == null) {
            preparedSql = SqlParameterizer.prepare(assembledTokens(edits()));
        }
        return preparedSql;
    }

    private void invalidate() {
        builtSql = null;
        preparedSql = null;
    }

    /** Список изменений используется и в buildSql(), и в prepare(). */
    private List<Edit> edits() {
        List<Edit> edits = new ArrayList<>(4);
        if (markExpression != null) {
            edits.add(new Edit(selectItemsEnd, selectItemsEnd,
                    ", (" + markExpression + "\n) AS " + markAlias + " ", 0));
        }

        if (!predicates.isEmpty()) {
            String joined = joinPredicates();
            if (whereWordEnd >= 0) {
                edits.add(new Edit(whereWordEnd, whereWordEnd,
                        " (" + joined + ") AND (", 10));
                edits.add(new Edit(wherePredicateEnd, wherePredicateEnd, ") ", 10));
            } else {
                edits.add(new Edit(whereInsertion, whereInsertion,
                        " WHERE (" + joined + ")\n", 10));
            }
        }

        if (orderBy != null) {
            if (orderStart >= 0) {
                edits.add(new Edit(orderStart, orderEnd, "ORDER BY " + orderBy + "\n", 20));
            } else {
                edits.add(new Edit(orderInsertion, orderInsertion,
                        " ORDER BY " + orderBy + "\n", 20));
            }
        }
        Collections.sort(edits, Comparator.comparingInt((Edit e) -> e.start)
                .thenComparingInt(e -> e.priority));
        return edits;
    }

    /**
     * Исходные токены НЕ лексируются повторно. Сканируются только замены,
     * после чего выполняется единый проход по итоговой последовательности.
     */
    private List<SqlParameterizer.Lexeme> assembledTokens(List<Edit> edits) {
        List<SqlParameterizer.Lexeme> result = new ArrayList<>(sourceTokens.size() + 32);
        int consumed = 0;
        int tokenIndex = 0;
        for (Edit edit : edits) {
            if (edit.start < consumed) throw new IllegalStateException("Overlapping SQL edits");
            tokenIndex = appendSourceTokens(result, consumed, edit.start, tokenIndex);
            for (SqlScanner.Token token : scanner.scan(edit.replacement)) {
                result.add(new SqlParameterizer.Lexeme(edit.replacement, token));
            }
            consumed = edit.end;
        }
        appendSourceTokens(result, consumed, source.length(), tokenIndex);
        return result;
    }

    private int appendSourceTokens(List<SqlParameterizer.Lexeme> result,
                                   int start, int end, int tokenIndex) {
        while (tokenIndex < sourceTokens.size()
                && sourceTokens.get(tokenIndex).getEnd() <= start) {
            tokenIndex++;
        }
        while (tokenIndex < sourceTokens.size()
                && sourceTokens.get(tokenIndex).getStart() < end) {
            SqlScanner.Token token = sourceTokens.get(tokenIndex++);
            if (token.getStart() < start || token.getEnd() > end) {
                throw new IllegalStateException("Edit is not aligned to token boundary: " + token);
            }
            result.add(new SqlParameterizer.Lexeme(source, token));
        }
        return tokenIndex;
    }

    private String joinPredicates() {
        StringBuilder result = new StringBuilder();
        for (String predicate : predicates) {
            if (result.length() > 0) result.append(" AND ");
            result.append('(').append(predicate).append("\n)");
        }
        return result.toString();
    }

    private void requireSimple(String action) {
        if (compound) {
            throw new UnsupportedOperationException(action
                    + " for UNION/INTERSECT/EXCEPT/MINUS is not supported");
        }
    }

    /** Не допускаем второй statement в пользовательском фрагменте. */
    private void checkFragment(String fragment) {
        int depth = 0;
        for (SqlScanner.Token token : scanner.scan(fragment)) {
            if (symbol(fragment, token, "(")) depth++;
            else if (symbol(fragment, token, ")")) {
                if (--depth < 0) throw new IllegalArgumentException("Unbalanced SQL fragment");
            } else if (depth == 0 && symbol(fragment, token, ";")) {
                throw new IllegalArgumentException("SQL fragment contains a semicolon");
            }
        }
        if (depth != 0) throw new IllegalArgumentException("Unbalanced SQL fragment");
    }

    private static String optionalFragment(String fragment) {
        if (fragment == null) return null;
        String trimmed = fragment.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String requiredFragment(String fragment, String label) {
        String trimmed = optionalFragment(fragment);
        if (trimmed == null) throw new IllegalArgumentException("Empty " + label);
        return trimmed;
    }

    private Layout inspect() {
        int depth = 0;
        int statementEndIndex = sourceTokens.size();
        int selectIndex = -1;
        int firstSignificant = nextSignificant(0);
        if (firstSignificant >= sourceTokens.size() ||
                (!word(firstSignificant, "SELECT") && !word(firstSignificant, "WITH"))) {
            throw new IllegalArgumentException("Expected SELECT or WITH");
        }

        // Заголовок WITH может содержать вложенные SELECT внутри CTE.
        for (int i = 0; i < sourceTokens.size(); i++) {
            SqlScanner.Token token = sourceTokens.get(i);
            if (symbol(source, token, "(")) depth++;
            else if (symbol(source, token, ")")) {
                if (--depth < 0) throw new IllegalArgumentException("Unbalanced parentheses");
            } else if (depth == 0 && symbol(source, token, ";")) {
                statementEndIndex = i;
                break;
            } else if (depth == 0 && selectIndex < 0 && word(i, "SELECT")) {
                selectIndex = i;
            }
        }
        if (depth != 0) throw new IllegalArgumentException("Unbalanced parentheses");
        if (selectIndex < 0) throw new IllegalArgumentException("Top-level SELECT not found");
        if (statementEndIndex < sourceTokens.size() &&
                nextSignificant(statementEndIndex + 1) < sourceTokens.size()) {
            throw new IllegalArgumentException("Multiple SQL statements are not supported");
        }
        // Убрали отбор до SELECT: прочие начальные варианты сейчас не поддерживаем.
        if (selectIndex != firstSignificant && !word(firstSignificant, "WITH")) {
            throw new IllegalArgumentException("Unsupported SELECT prefix");
        }

        int from = -1, where = -1, group = -1, order = -1, tail = -1;
        int firstFollowingWhere = statementEndIndex;
        int firstFollowingOrder = statementEndIndex;
        int firstSelectBoundary = statementEndIndex;
        boolean compoundQuery = false;
        boolean siblings = false;
        depth = 0;
        for (int i = selectIndex + 1; i < statementEndIndex; i++) {
            SqlScanner.Token token = sourceTokens.get(i);
            if (symbol(source, token, "(")) { depth++; continue; }
            if (symbol(source, token, ")")) { depth--; continue; }
            if (depth != 0 || trivia(token)) continue;

            if (word(i, "UNION") || word(i, "INTERSECT") ||
                    word(i, "EXCEPT") || word(i, "MINUS")) {
                compoundQuery = true;
            }
            boolean orderClause = orderClauseAt(i);
            boolean groupClause = wordPair(i, "GROUP", "BY");
            boolean whereBoundary = orderClause || groupClause || word(i, "HAVING") ||
                    word(i, "WINDOW") || word(i, "QUALIFY") ||
                    wordPair(i, "START", "WITH") ||
                    wordPair(i, "CONNECT", "BY") ||
                    word(i, "MODEL") || tailAt(i);
            if (word(i, "FROM") && from < 0 && firstSelectBoundary == statementEndIndex) {
                from = i;
                firstSelectBoundary = i;
            }
            if (whereBoundary && firstSelectBoundary == statementEndIndex) firstSelectBoundary = i;
            if (word(i, "WHERE") && where < 0) {
                where = i;
                if (firstSelectBoundary == statementEndIndex) firstSelectBoundary = i;
            }
            if (groupClause && group < 0) group = i;
            if (orderClause && order < 0) {
                order = i;
                siblings = wordPair3(i, "ORDER", "SIBLINGS", "BY");
            }
            if (tailAt(i) && tail < 0) tail = i;
            if (where >= 0 && i > where && whereBoundary) {
                firstFollowingWhere = Math.min(firstFollowingWhere, i);
            }
            if (order >= 0 && i > order && tailAt(i)) {
                firstFollowingOrder = Math.min(firstFollowingOrder, i);
            }
        }

        int itemsBoundary = firstSelectBoundary;
        int listLastToken = lastSignificant(selectIndex + 1, itemsBoundary);
        if (listLastToken < 0) throw new IllegalArgumentException("Empty SELECT list");

        Layout result = new Layout();
        result.selectItemsEnd = sourceTokens.get(listLastToken).getEnd();
        result.compound = compoundQuery;
        result.orderSiblings = siblings;
        result.whereWordEnd = -1;
        result.wherePredicateEnd = -1;
        result.orderStart = -1;
        result.orderEnd = -1;
        int mainContentEnd = lastSignificantEnd(selectIndex + 1, statementEndIndex);

        if (where >= 0) {
            int afterWhere = nextSignificant(where + 1);
            if (afterWhere >= firstFollowingWhere) {
                throw new IllegalArgumentException("Empty WHERE clause");
            }
            result.whereWordEnd = sourceTokens.get(where).getEnd();
            result.wherePredicateEnd = lastSignificantEnd(afterWhere, firstFollowingWhere);
        } else {
            // WHERE должен появиться перед первым clause, который идёт после WHERE.
            int boundary = statementEndIndex;
            depth = 0;
            for (int i = selectIndex + 1; i < statementEndIndex; i++) {
                SqlScanner.Token token = sourceTokens.get(i);
                if (symbol(source, token, "(")) { depth++; continue; }
                if (symbol(source, token, ")")) { depth--; continue; }
                if (depth == 0 && (orderClauseAt(i) || wordPair(i, "GROUP", "BY") ||
                        word(i, "HAVING") || word(i, "WINDOW") ||
                        word(i, "QUALIFY") || wordPair(i, "START", "WITH") ||
                        wordPair(i, "CONNECT", "BY") || word(i, "MODEL") || tailAt(i))) {
                    boundary = i;
                    break;
                }
            }
            result.whereInsertion = boundary == statementEndIndex
                    ? mainContentEnd : sourceTokens.get(boundary).getStart();
        }
        if (order >= 0) {
            int actualEnd = firstFollowingOrder;
            result.orderStart = sourceTokens.get(order).getStart();
            int bodyStart = nextSignificant(order + (siblings ? 3 : 2));
            if (bodyStart >= actualEnd) throw new IllegalArgumentException("Empty ORDER BY clause");
            result.orderEnd = lastSignificantEnd(bodyStart, actualEnd);
        } else {
            result.orderInsertion = tail >= 0
                    ? sourceTokens.get(tail).getStart() : mainContentEnd;
        }
        return result;
    }

    private boolean orderClauseAt(int i) {
        return wordPair(i, "ORDER", "BY") || wordPair3(i, "ORDER", "SIBLINGS", "BY");
    }

    private boolean tailAt(int i) {
        return word(i, "LIMIT") || word(i, "OFFSET") ||
                word(i, "FETCH") || wordPair(i, "FOR", "UPDATE") ||
                wordPair(i, "FOR", "SHARE") || wordPair(i, "FOR", "KEY") ||
                wordPair(i, "FOR", "NO");
    }

    private boolean wordPair(int i, String first, String second) {
        return word(i, first) && word(nextSignificant(i + 1), second);
    }

    private boolean wordPair3(int i, String a, String b, String c) {
        return word(i, a) && wordPair(nextSignificant(i + 1), b, c);
    }

    private boolean word(int i, String text) {
        return i >= 0 && i < sourceTokens.size() && sourceTokens.get(i).isWord(source, text);
    }

    private int nextSignificant(int start) {
        int i = start;
        while (i < sourceTokens.size() && trivia(sourceTokens.get(i))) i++;
        return i;
    }

    private static boolean trivia(SqlScanner.Token token) {
        switch (token.getKind()) {
            case WHITESPACE:
            case COMMENT:
            case HINT:
            case PARAMETERIZE_OFF:
            case PARAMETERIZE_ON:
                return true;
            default:
                return false;
        }
    }

    private int lastSignificant(int first, int exclusiveEnd) {
        for (int i = exclusiveEnd - 1; i >= first; i--) {
            if (!trivia(sourceTokens.get(i))) return i;
        }
        return -1;
    }

    private int lastSignificantEnd(int first, int exclusiveEnd) {
        int i = lastSignificant(first, exclusiveEnd);
        if (i < 0) throw new IllegalArgumentException("Missing SQL expression");
        return sourceTokens.get(i).getEnd();
    }

    private static boolean symbol(String sql, SqlScanner.Token token, String value) {
        return token.getKind() == SYMBOL && token.text(sql).equals(value);
    }

    private static final class Layout {
        int selectItemsEnd;
        int whereWordEnd;
        int wherePredicateEnd;
        int whereInsertion;
        int orderStart;
        int orderEnd;
        int orderInsertion;
        boolean compound;
        boolean orderSiblings;
    }

    private static final class Edit {
        final int start;
        final int end;
        final String replacement;
        final int priority;

        Edit(int start, int end, String replacement, int priority) {
            this.start = start;
            this.end = end;
            this.replacement = replacement;
            this.priority = priority;
        }
    }
}
