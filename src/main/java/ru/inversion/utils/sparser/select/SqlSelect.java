package ru.inversion.utils.sparser.select;

import ru.inversion.utils.sparser.lexer.SqlKeyword;
import ru.inversion.utils.sparser.lexer.SqlScanner;
import ru.inversion.utils.sparser.SqlScannerFactory;
import ru.inversion.utils.sparser.lexer.OracleSqlScanner;
import ru.inversion.db.dialect.SqlDialect;
import ru.inversion.utils.sparser.jdbc.PreparedSql;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import static ru.inversion.utils.sparser.lexer.SqlScanner.Kind.*;
import static ru.inversion.utils.sparser.lexer.SqlKeyword.*;

/**
 * Структурные изменения одного внешнего SELECT без дополнительной SQL-обёртки.
 *
 * Структура исходного SELECT разбирается при создании. Изменённый SQL
 * и PreparedSql кешируются до следующей структурной правки.
 * prepare() повторно сканирует собранный SQL только при сбросе кеша.
 */
public final class SqlSelect {

    private final String source;
    private final SqlScanner scanner;
    private final List<SqlScanner.Token> sourceTokens;
    private final Layout layout;

    private List<String> predicates = Collections.emptyList();

    private String orderBy;
    private String markExpression;
    private String markAlias;
    private String builtSql;

    private PreparedSql preparedSql;

    /** Прикладной API: детали выбора lexer скрыты за диалектом. */
    public SqlSelect(String sql, SqlDialect dialect) {
        this(sql, SqlScannerFactory.create(dialect));
    }

    /** Совместимость с прежним внутренним API. */
    @Deprecated
    public SqlSelect(String sql, SqlScanner scanner) {
        source = Objects.requireNonNull(sql, "sql");
        this.scanner = Objects.requireNonNull(scanner, "scanner");
        sourceTokens = scanner.scan(sql);
        this.layout = inspect();
    }

    public void setPredicates(List<String> values) {
        Objects.requireNonNull(values, "values");
        List<String> copy = new ArrayList<>(values.size());
        for (String value : values) {
            String part = requiredFragment(value, "predicate");
            checkFragment(part);
            copy.add(part);
        }
        if (!copy.isEmpty() && layout.legacyWhereMarkerStart < 0)
            requireSimple("WHERE predicates");
        predicates = Collections.unmodifiableList(copy);
        invalidate();
    }

    /** null означает отсутствие runtime-сортировки: исходный ORDER BY сохраняется. */
    public void setOrderBy(String expression) {
        String value = optionalFragment(expression);
        if (value != null) {
            requireSimple("ORDER BY replacement");
            if (layout.orderSiblings) {
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
            if (layout.markRestriction != null) {
                throw new UnsupportedOperationException(
                        "SELECT mark is not supported for " + layout.markRestriction);
            }
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
        if (predicates.isEmpty() && orderBy == null && markExpression == null
                && layout.legacyWhereMarkerStart < 0) {
            return builtSql = source;
        }

        List<Edit> edits = edits();
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
            String sql = buildSql();
            preparedSql = SqlParameterizer.prepare(
                    sql, scanner.scan(sql), scanner instanceof OracleSqlScanner);
        }
        return preparedSql;
    }

    private void invalidate() {
        builtSql = null;
        preparedSql = null;
    }

    /** Структурные изменения исходного SQL. */
    private List<Edit> edits() {
        List<Edit> edits = new ArrayList<>(4);
        if (markExpression != null) {
            edits.add(new Edit(layout.selectItemsEnd, layout.selectItemsEnd,
                    ", (" + markExpression + "\n) AS " + markAlias + " ", 0));
        }

        if (layout.legacyWhereMarkerStart >= 0) {
            edits.add(new Edit(
                    layout.legacyWhereMarkerStart,
                    layout.legacyWhereMarkerEnd,
                    predicates.isEmpty() ? "" : joinLegacyWherePredicates(),
                    10
            ));
        }
        else if (!predicates.isEmpty()) {
            String joined = joinPredicates();
            if (layout.whereWordEnd >= 0) {
                edits.add(new Edit(layout.whereWordEnd, layout.whereWordEnd,
                        " (" + joined + ") AND (", 10));
                edits.add(new Edit(layout.wherePredicateEnd, layout.wherePredicateEnd, ") ", 10));
            } else {
                edits.add(new Edit(layout.whereInsertion, layout.whereInsertion,
                        " WHERE (" + joined + ")\n", 10));
            }
        }

        if (orderBy != null) {
            if (layout.orderStart >= 0) {
                edits.add(new Edit(layout.orderStart, layout.orderEnd, "ORDER BY " + orderBy + "\n", 20));
            } else {
                edits.add(new Edit(layout.orderInsertion, layout.orderInsertion,
                        " ORDER BY " + orderBy + "\n", 20));
            }
        }
        Collections.sort(edits, Comparator.comparingInt((Edit e) -> e.start)
                .thenComparingInt(e -> e.priority));
        return edits;
    }

    private String joinPredicates() {
        StringBuilder result = new StringBuilder();
        for (String predicate : predicates) {
            if (result.length() > 0) result.append(" AND ");
            result.append('(').append(predicate).append("\n)");
        }
        return result.toString();
    }

    /** Legacy <w> replacement used by former SQLSelectBuilder. */
    private String joinLegacyWherePredicates() {
        StringBuilder result = new StringBuilder();
        for (String predicate : predicates) {
            result.append(" AND (")
                  .append(predicate)
                  .append("\n)");
        }
        return result.toString();
    }

    private void requireSimple(String action) {
        if (layout.compound) {
            throw new UnsupportedOperationException(action
                    + " for UNION/INTERSECT/EXCEPT/MINUS is not supported");
        }
    }

    /** Не допускаем второй statement в пользовательском фрагменте. */
    private void checkFragment(String fragment) {
        List<SqlScanner.Token> tokens = scanner.scan(fragment);
        int depth = 0;
        for (SqlScanner.Token token : tokens) {
            if (symbol(fragment, token, "(")) depth++;
            else if (symbol(fragment, token, ")")) {
                if (--depth < 0) throw new IllegalArgumentException("Unbalanced SQL fragment");
            } else if (symbol(fragment, token, ";")) {
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
                (!keyword(firstSignificant, SELECT) && !keyword(firstSignificant, WITH))) {
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
            } else if (depth == 0 && selectIndex < 0 && keyword(i, SELECT)) {
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
        if (selectIndex != firstSignificant && !keyword(firstSignificant, WITH)) {
            throw new IllegalArgumentException("Unsupported SELECT prefix");
        }

        int where = -1, order = -1, tail = -1;
        boolean hasGroupBy = false;
        int firstFollowingWhere = statementEndIndex;
        int firstFollowingOrder = statementEndIndex;
        int firstSelectBoundary = statementEndIndex;
        int firstWhereBoundary = statementEndIndex;
        boolean compoundQuery = false;
        boolean siblings = false;
        depth = 0;
        for (int i = selectIndex + 1; i < statementEndIndex; i++) {
            SqlScanner.Token token = sourceTokens.get(i);
            if (symbol(source, token, "(")) { depth++; continue; }
            if (symbol(source, token, ")")) { depth--; continue; }
            if (depth != 0 || trivia(token)) continue;

            if (in(keyword(i), UNION, INTERSECT, EXCEPT, MINUS)) {
                compoundQuery = true;
            }
            boolean orderClause = orderClauseAt(i);
            boolean groupClause = keywords(i, GROUP, BY);
            boolean whereBoundary = orderClause || groupClause || keyword(i, HAVING) ||
                    keyword(i, WINDOW) || keyword(i, QUALIFY) ||
                    keywords(i, START, WITH) ||
                    keywords(i, CONNECT, BY) ||
                    keyword(i, MODEL) || tailAt(i);
            if (keyword(i, FROM) && firstSelectBoundary == statementEndIndex) {
                firstSelectBoundary = i;
            }
            if (whereBoundary && firstSelectBoundary == statementEndIndex) firstSelectBoundary = i;
            if (whereBoundary && firstWhereBoundary == statementEndIndex) firstWhereBoundary = i;
            if (keyword(i, WHERE) && where < 0) {
                where = i;
                if (firstSelectBoundary == statementEndIndex) firstSelectBoundary = i;
            }
            if (groupClause) hasGroupBy = true;
            if (orderClause && order < 0) {
                order = i;
                siblings = keywords(i, ORDER, SIBLINGS, BY);
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
        String markRestriction = null;
        if (hasGroupBy || hasOuterHaving(selectIndex, statementEndIndex)) {
            markRestriction = "GROUP BY/HAVING";
        } else if (hasTopLevelDistinct(selectIndex + 1, itemsBoundary)) {
            markRestriction = DISTINCT.name();
        }
        int listLastToken = lastSignificant(selectIndex + 1, itemsBoundary);
        if (listLastToken < 0) throw new IllegalArgumentException("Empty SELECT list");

        Layout result = new Layout();
        result.selectItemsEnd = sourceTokens.get(listLastToken).getEnd();
        result.compound = compoundQuery;
        result.orderSiblings = siblings;
        result.markRestriction = markRestriction;
        result.whereWordEnd = -1;
        result.wherePredicateEnd = -1;
        result.orderStart = -1;
        result.orderEnd = -1;
        result.whereInsertion = -1;
        result.orderInsertion = -1;
        result.legacyWhereMarkerStart = -1;
        result.legacyWhereMarkerEnd = -1;

        int[] legacyWhereMarker = findLegacyWhereMarker();
        if (legacyWhereMarker != null) {
            result.legacyWhereMarkerStart = legacyWhereMarker[0];
            result.legacyWhereMarkerEnd = legacyWhereMarker[1];
        }

        int mainContentEnd = lastSignificantEnd(selectIndex + 1, statementEndIndex);

        if (where >= 0) {
            int afterWhere = nextSignificant(where + 1);
            if (afterWhere >= firstFollowingWhere) {
                throw new IllegalArgumentException("Empty WHERE clause");
            }
            result.whereWordEnd = sourceTokens.get(where).getEnd();
            result.wherePredicateEnd = lastSignificantEnd(afterWhere, firstFollowingWhere);
        } else {
            // Граница уже найдена основным проходом по внешним SQL clauses.
            result.whereInsertion = firstWhereBoundary == statementEndIndex
                    ? mainContentEnd : sourceTokens.get(firstWhereBoundary).getStart();
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

    /**
     * Находит первый legacy marker <w> вне строк и комментариев.
     * Старый SQLSelectBuilder также поддерживал один такой insertion point.
     */
    private int[] findLegacyWhereMarker() {
        for (int i = 0; i + 2 < sourceTokens.size(); i++) {
            SqlScanner.Token open = sourceTokens.get(i);
            SqlScanner.Token word = sourceTokens.get(i + 1);
            SqlScanner.Token close = sourceTokens.get(i + 2);

            if (open.getEnd() != word.getStart()
                    || word.getEnd() != close.getStart()) {
                continue;
            }

            boolean openBracket =
                    (open.getKind() == SqlScanner.Kind.OPERATOR
                            || open.getKind() == SqlScanner.Kind.SYMBOL)
                    && "<".equals(open.text(source));

            boolean markerWord =
                    word.getKind() == SqlScanner.Kind.WORD
                    && word.isWord(source, "W");

            boolean closeBracket =
                    (close.getKind() == SqlScanner.Kind.OPERATOR
                            || close.getKind() == SqlScanner.Kind.SYMBOL)
                    && ">".equals(close.text(source));

            if (openBracket && markerWord && closeBracket) {
                return new int[] { open.getStart(), close.getEnd() };
            }
        }
        return null;
    }

    /** Ограничения на mark проверяются до изменения SELECT list. */
    private boolean hasTopLevelDistinct(int first, int end) {
        int depth = 0;
        for (int i = first; i < end; i++) {
            SqlScanner.Token t = sourceTokens.get(i);
            if (symbol(source, t, "(")) depth++;
            else if (symbol(source, t, ")")) depth--;
            else if (depth == 0 && keyword(i, DISTINCT)) return true;
        }
        return false;
    }

    private boolean hasOuterHaving(int first, int end) {
        int depth = 0;
        for (int i = first; i < end; i++) {
            SqlScanner.Token t = sourceTokens.get(i);
            if (symbol(source, t, "(")) depth++;
            else if (symbol(source, t, ")")) depth--;
            else if (depth == 0 && keyword(i, HAVING)) return true;
        }
        return false;
    }

    private boolean orderClauseAt(int i) {
        return keywords(i, ORDER, BY) || keywords(i, ORDER, SIBLINGS, BY);
    }

    private boolean tailAt(int i) {
        return in(keyword(i), LIMIT, OFFSET, FETCH)
                || keywords(i, FOR, UPDATE)
                || keywords(i, FOR, SHARE)
                || keywords(i, FOR, KEY)
                || keywords(i, FOR, NO);
    }

    private boolean keywords(int i, SqlKeyword ... keywords) {
        int index = i;

        for( SqlKeyword keyword : keywords ) {
            if( !keyword(index, keyword) )
                return false;

            index = nextSignificant(index + 1);
        }

        return true;
    }

    private SqlKeyword keyword(int i) {
        return i >= 0 && i < sourceTokens.size()
                ? sourceTokens.get(i).keyword(source)
                : null;
    }

    private boolean keyword(int i, SqlKeyword keyword) {
        return i >= 0
                && i < sourceTokens.size()
                && sourceTokens.get(i).isKeyword(source, keyword);
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

    /** */
    private static final class Layout {

        int selectItemsEnd;

        int whereWordEnd;

        int wherePredicateEnd;

        int whereInsertion;

        int legacyWhereMarkerStart;
        int legacyWhereMarkerEnd;

        int orderStart;
        int orderEnd;

        int orderInsertion;

        boolean compound;
        boolean orderSiblings;

        String markRestriction;
    }

   /** */
   private static final class Edit {

      final int start;
      final int end;

      final String replacement;

      final int priority;

        Edit( int start, int end, String replacement, int priority )
        {
            this.start = start;
            this.end = end;
            this.replacement = replacement;
            this.priority = priority;
        }
    }
}
