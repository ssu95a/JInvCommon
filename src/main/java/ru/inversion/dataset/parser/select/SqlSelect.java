package ru.inversion.dataset.parser.select;

import ru.inversion.dataset.parser.lexer.SqlKeyword;
import ru.inversion.dataset.parser.lexer.SqlScanner;
import ru.inversion.dataset.parser.SqlScannerFactory;
import ru.inversion.dataset.parser.lexer.OracleSqlScanner;
import ru.inversion.db.dialect.SqlDialect;
import ru.inversion.dataset.parser.jdbc.PreparedSql;
import ru.inversion.dataset.parser.select.SqlParameterizer.NamedSourceRange;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.IntPredicate;

import static ru.inversion.dataset.parser.lexer.SqlScanner.Kind.*;
import static ru.inversion.dataset.parser.lexer.SqlKeyword.*;

/**
 * Структурные изменения одного внешнего SELECT без дополнительной SQL-обёртки.
 *
 * Структура исходного SELECT разбирается при создании. Изменённый SQL
 * и PreparedSql кешируются до следующей структурной правки.
 * prepare() повторно сканирует собранный SQL только при сбросе кеша.
 */
public final class SqlSelect {

    private static final String DEFAULT_SUBQUERY_ALIAS = "qrslt";

    private final String source;
    private final SqlScanner scanner;

    private final List<SqlScanner.Token> sourceTokens;

    private final Layout layout;

    private List<String> predicates = Collections.emptyList();

    private String orderBy;

    private String afterOrderBy;

    private Integer rowLimit;

    private String markExpression;

    private String markAlias;

    private String builtSql;

    private PreparedSql preparedSql;

    /** Прикладной API: детали выбора lexer скрыты за диалектом. */
    public SqlSelect( String sql, SqlDialect dialect ) {
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

    /** Возвращает SQL с внешним SELECT, если составному запросу нужны фильтр или сортировка. */
    public String wrapCompoundForOuterClauses(boolean hasPredicates, boolean replaceOrderBy) {
        if (!layout.compound || (!replaceOrderBy && (!hasPredicates || layout.legacyWhereMarkerStart >= 0)))
            return source;

        return wrapCompoundSource(DEFAULT_SUBQUERY_ALIAS, replaceOrderBy);
    }

    /** Готовит источник строк текстового запроса DataSet. */
    public String wrapLegacyDataSetSource(String queryAlias, boolean replaceOrderBy) {
        return dataSetSource(queryAlias, replaceOrderBy).sql();
    }

    public DataSetSource dataSetSource(String queryAlias, boolean replaceOrderBy) {
        String alias = queryAlias == null ? DEFAULT_SUBQUERY_ALIAS : queryAlias;
        // Предикаты при <w> вставляются в исходный запрос, где внешнего алиаса нет.
        String outerAlias = layout.legacyWhereMarkerStart < 0 ? alias : null;
        if (layout.compound)
            return new DataSetSource(new SqlSelect(wrapCompoundSource(alias, replaceOrderBy), scanner), outerAlias);

        int table = simpleDataSetTable();
        if (table == -1)
            return new DataSetSource(new SqlSelect(wrapSource(source.substring(0, layout.statementEnd), alias), scanner), outerAlias);
        if (table == sourceTokens.size()) {
            if (queryAlias != null)
                throw new IllegalArgumentException("queryAlias requires a FROM source");
            return new DataSetSource(this, null);
        }

        int next = nextSignificant(table + 1);
        if (!endOfDataSetFrom(next)) {
            int existing = keyword(next, AS) ? nextSignificant(next + 1) : next;
            String currentAlias = sourceTokens.get(existing).text(source);
            if (queryAlias != null && !identifierName(currentAlias).equals(identifierName(queryAlias)))
                throw new IllegalArgumentException("queryAlias " + queryAlias
                        + " conflicts with SQL source alias " + currentAlias);
            return new DataSetSource(this, currentAlias);
        }
        if (queryAlias == null)
            return new DataSetSource(this, null);

        checkTableReferences(table, queryAlias);
        int end = sourceTokens.get(table).end();
        String aliased = source.substring(0, end) + " " + queryAlias + source.substring(end);
        return new DataSetSource(new SqlSelect(aliased, scanner), queryAlias);
    }

    private String wrapCompoundSource(String alias, boolean replaceOrderBy) {
        String inner = source.substring(0, layout.statementEnd);
        if (layout.orderStart >= 0 && !layout.hasRowSelection)
            inner = source.substring(0, layout.orderStart) + source.substring(layout.orderEnd, layout.statementEnd);

        String sql = wrapSource(inner, alias);
        if (!replaceOrderBy && layout.orderStart >= 0)
            sql += "\n" + source.substring(layout.orderStart, layout.orderEnd);
        return sql;
    }

    private static String wrapSource(String sql, String alias) {
        return "SELECT " + alias + ".* FROM (\n" + sql + "\n) " + alias;
    }

    /** Индекс последней части имени таблицы, -1 для SFS, размер списка для SELECT без FROM. */
    private int simpleDataSetTable() {
        int from = -1;
        boolean hasWhere = false;
        for (int i = 0; i < sourceTokens.size(); i++) {
            if (keyword(i, FROM)) {
                if (from >= 0)
                    return -1;
                from = i;
            }
            if (keyword(i, WHERE)) {
                if (hasWhere)
                    return -1;
                hasWhere = true;
            }
        }
        if (from < 0)
            return sourceTokens.size();

        int table = nextSignificant(from + 1);
        if (!identifier(table))
            return -1;
        int next = nextSignificant(table + 1);
        while (next < sourceTokens.size() && symbol(source, sourceTokens.get(next), ".")) {
            table = nextSignificant(next + 1);
            if (!identifier(table))
                return -1;
            next = nextSignificant(table + 1);
        }
        if (endOfDataSetFrom(next))
            return table;
        if (keyword(next, AS))
            next = nextSignificant(next + 1);
        return identifier(next) && endOfDataSetFrom(nextSignificant(next + 1)) ? table : -1;
    }

    /** Алиас скрывает прежнее имя таблицы. Ссылки внутри исходного SQL сохраняем. */
    private void checkTableReferences(int table, String alias) {
        String tableName = identifierName(sourceTokens.get(table).text(source));
        boolean sameName = tableName.equals(identifierName(alias));
        for (int i = 0, previous = -1; i < sourceTokens.size(); i = nextSignificant(i + 1)) {
            if (i != table && identifier(i) && tableName.equals(identifierName(sourceTokens.get(i).text(source)))) {
                boolean schemaQualified = previous >= 0 && symbol(source, sourceTokens.get(previous), ".");
                int next = nextSignificant(i + 1);
                boolean qualifier = next < sourceTokens.size() && symbol(source, sourceTokens.get(next), ".");
                if (schemaQualified || !sameName && (qualifier || !(scanner instanceof OracleSqlScanner)))
                    throw new IllegalArgumentException("queryAlias " + alias
                            + " would hide SQL source name " + sourceTokens.get(table).text(source)
                            + ". Declare the alias in the source SQL.");
            }
            previous = i;
        }
    }

    private String identifierName(String value) {
        if (value.startsWith("\""))
            return value.substring(1, value.length() - 1).replace("\"\"", "\"");
        return scanner instanceof OracleSqlScanner ? value.toUpperCase(Locale.ROOT) : value.toLowerCase(Locale.ROOT);
    }

    private boolean identifier(int index) {
        if (index >= sourceTokens.size())
            return false;
        SqlScanner.Kind kind = sourceTokens.get(index).kind();
        return kind == WORD || kind == QUOTED_IDENTIFIER;
    }

    private boolean endOfDataSetFrom(int index) {
        return index >= sourceTokens.size()
                || sourceTokens.get(index).start() >= layout.statementContentEnd
                || sourceTokens.get(index).start() == layout.legacyWhereMarkerStart
                || in(keyword(index), WHERE, HAVING, WINDOW, QUALIFY, MODEL)
                || keywords(index, GROUP, BY) || orderClauseAt(index) || tailAt(index)
                || keywords(index, START, WITH) || keywords(index, CONNECT, BY);
    }

    /** */
    public void setPredicates( List<String> values ) {
        Objects.requireNonNull(values, "values");

        List<String> copy = new ArrayList<>(values.size());

        for( String value : values ) {
            String part = requiredFragment(value, "predicate");
            checkFragment(part);
            copy.add(part);
        }

        if (!copy.isEmpty() && layout.legacyWhereMarkerStart < 0)
            requireSimple("WHERE predicates");

        predicates = Collections.unmodifiableList(copy);
        invalidate();
    }

    /**
     * Compatibility overload. Parameter ownership is global now;
     * sourceIds are ignored.
     */
    @Deprecated
    public void setPredicates(List<String> values, List<Integer> sourceIds) {
        Objects.requireNonNull(values, "values");

        if (sourceIds != null && sourceIds.size() != values.size())
            throw new IllegalArgumentException("predicate sourceIds size mismatch");

        setPredicates(values);
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

    /** Compatibility overload. Parameter ownership is global now. */
    @Deprecated
    public void setOrderBy(String expression, int sourceId) {
        setOrderBy(expression);
    }

    /** Добавляет фрагмент в конец внешнего SELECT перед завершающей точкой с запятой. */
    public void setAfterOrderBy(String expression) {
        String value = optionalFragment(expression);
        if (value != null)
            checkFragment(value);
        afterOrderBy = value;
        invalidate();
    }

    /**
     * Ограничить количество строк внешнего SELECT.
     * null снимает override и сохраняет row limit исходного SQL.
     */
    public void setRowLimit(Integer value) {
        if( value != null ) {
            if( value < 0 )
                throw new IllegalArgumentException("rowLimit must be >= 0");

            requireSimple("row limit");

            if( layout.rowLimitRestriction != null )
                throw new IllegalArgumentException(layout.rowLimitRestriction);
        }

        rowLimit = value;
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
                    (aliasTokens.get(0).kind() != WORD &&
                     aliasTokens.get(0).kind() != QUOTED_IDENTIFIER)) {
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
        if (builtSql == null)
            builtSql = renderSql(null, false, null);
        return builtSql;
    }

    private String renderSql(IntPredicate localPredicate, boolean localOrderBy, List<NamedSourceRange> namedSources) {
        if (predicates.isEmpty() && orderBy == null && afterOrderBy == null && rowLimit == null
                && markExpression == null && layout.legacyWhereMarkerStart < 0) {
            return source;
        }

        List<Edit> edits = edits(localPredicate, localOrderBy);
        StringBuilder result = new StringBuilder(source.length() + 128);
        int copiedUntil = 0;
        for (Edit edit : edits) {
            if (edit.start < copiedUntil) {
                throw new IllegalStateException("Overlapping SQL edits at " + edit.start);
            }
            result.append(source, copiedUntil, edit.start);
            if (!edit.namedSources.isEmpty()) {
                int base = result.length();
                for (NamedSourceRange range : edit.namedSources)
                    namedSources.add(new NamedSourceRange(base + range.start, base + range.end, range.sourceId));
            }
            result.append(edit.replacement);
            copiedUntil = edit.end;
        }

        result.append(source, copiedUntil, source.length());
        return result.toString();
    }

    /**
     * Готовит SQL для JDBC. Результат (SQL + порядок параметров)
     * неизменяем и кешируется до любой структурной правки.
     * Runtime-значения IParameters не хранятся и не разрешаются здесь.
     */
    public PreparedSql prepare() {
        if (preparedSql == null) {
            String sql = buildSql();
            preparedSql = SqlParameterizer.prepare(
                    sql,
                    scanner.scan(sql),
                    scanner instanceof OracleSqlScanner
            );
        }
        return preparedSql;
    }

    /**
     * Готовит SQL с источниками значений именованных параметров.
     * Источник предиката обозначен его индексом, источник сортировки — числом предикатов.
     * {@code localPredicate == null} означает, что у предикатов нет локальных источников.
     */
    public PreparedSql prepare(IntPredicate localPredicate, boolean localOrderBy) {
        List<NamedSourceRange> namedSources = new ArrayList<>();
        String sql = renderSql(localPredicate, localOrderBy, namedSources);
        return SqlParameterizer.prepare(sql, scanner.scan(sql), scanner instanceof OracleSqlScanner, namedSources);
    }

    private void invalidate() {
        builtSql = null;
        preparedSql = null;
    }

    /** Структурные изменения исходного SQL. */
    private List<Edit> edits(IntPredicate localPredicate, boolean localOrderBy) {
        List<Edit> edits = new ArrayList<>(5);

        if (markExpression != null) {
            edits.add(new Edit(
                    layout.selectItemsEnd,
                    layout.selectItemsEnd,
                    ", (" + markExpression + "\n) AS " + markAlias + " ",
                    0
            ));
        }

        if (layout.legacyWhereMarkerStart >= 0) {
            edits.add(predicateEdit(
                    layout.legacyWhereMarkerStart,
                    layout.legacyWhereMarkerEnd,
                    "", "", true, localPredicate
            ));
        }
        else if (!predicates.isEmpty()) {
            if (layout.whereWordEnd >= 0) {
                edits.add(predicateEdit(
                        layout.whereWordEnd,
                        layout.whereWordEnd,
                        " (", ") AND (", false, localPredicate
                ));

                edits.add(new Edit(
                        layout.wherePredicateEnd,
                        layout.wherePredicateEnd,
                        ") ",
                        10
                ));
            }
            else {
                edits.add(predicateEdit(
                        layout.whereInsertion,
                        layout.whereInsertion,
                        " WHERE (", ")\n", false, localPredicate
                ));
            }
        }

        if (orderBy != null) {
            boolean replace = layout.orderStart >= 0;
            String prefix = replace ? "ORDER BY " : " ORDER BY ";
            List<NamedSourceRange> namedSources = localOrderBy
                    ? Collections.singletonList(new NamedSourceRange(prefix.length(),
                            prefix.length() + orderBy.length(), predicates.size()))
                    : Collections.emptyList();
            edits.add(new Edit(replace ? layout.orderStart : layout.orderInsertion,
                    replace ? layout.orderEnd : layout.orderInsertion,
                    prefix + orderBy + "\n", 20, namedSources));
        }

        if( rowLimit != null ) {
            String clause = rowLimitClause(rowLimit);

            if( layout.rowLimitStart >= 0 ) {
                edits.add(new Edit(
                        layout.rowLimitStart,
                        layout.rowLimitEnd,
                        clause,
                        30
                ));
            }
            else {
                edits.add(new Edit(
                        layout.rowLimitInsertion,
                        layout.rowLimitInsertion,
                        " " + clause + " ",
                        30
                ));
            }
        }

        if (afterOrderBy != null) {
            edits.add(new Edit(layout.statementContentEnd, layout.statementContentEnd,
                    "\n" + afterOrderBy + "\n", 40));
        }

        edits.sort(
                Comparator.comparingInt((Edit edit) -> edit.start)
                        .thenComparingInt(edit -> edit.priority)
        );

        return edits;
    }

    private String rowLimitClause(int value) {
        return scanner instanceof OracleSqlScanner
                ? "FETCH FIRST " + value + " ROWS ONLY"
                : "LIMIT " + value;
    }

    private Edit predicateEdit(int start, int end, String prefix, String suffix,
                               boolean legacy, IntPredicate localPredicate) {
        StringBuilder result = new StringBuilder(prefix);
        List<NamedSourceRange> namedSources = localPredicate == null
                ? Collections.emptyList() : new ArrayList<>();

        for (int i = 0; i < predicates.size(); i++) {
            if (legacy || i > 0)
                result.append(" AND ");

            String predicate = predicates.get(i);
            result.append('(');
            if (localPredicate != null && localPredicate.test(i))
                namedSources.add(new NamedSourceRange(result.length(), result.length() + predicate.length(), i));
            result.append(predicate).append("\n)");
        }

        return new Edit(start, end, result.append(suffix).toString(), 10, namedSources);
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

    /** */
    private static String optionalFragment( String fragment )
    {
        if( fragment == null)
            return null;

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
        int openingMarker = legacyTilde(firstSignificant) ? firstSignificant : -1;
        if (openingMarker >= 0)
            firstSignificant = nextSignificant(openingMarker + 1);

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

        int statementEnd = statementEndIndex < sourceTokens.size()
                ? sourceTokens.get(statementEndIndex).start() : source.length();
        if (openingMarker >= 0) {
            // Маркеры остаются в исходном SQL до параметризации.
            int closingMarker = lastSignificant(firstSignificant + 1, statementEndIndex);
            if (!legacyTilde(closingMarker))
                throw new IllegalArgumentException("Unpaired legacy ~ marker at offset "
                        + sourceTokens.get(openingMarker).start());
            statementEndIndex = closingMarker;
        }

        int where = -1, order = -1, tail = -1;
        int limit = -1, fetch = -1, offset = -1, rowLock = -1;
        boolean hasGroupBy = false;
        boolean hasHaving = false;
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
            boolean rowLockClause = rowLockClauseAt(i);
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
            if (keyword(i, HAVING)) hasHaving = true;
            if (orderClause && order < 0) {
                order = i;
                siblings = keywords(i, ORDER, SIBLINGS, BY);
            }
            if (keyword(i, LIMIT) && limit < 0) limit = i;
            if (keyword(i, FETCH) && fetch < 0) fetch = i;
            if (keyword(i, OFFSET) && offset < 0) offset = i;
            if (rowLockClause && rowLock < 0) rowLock = i;
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
        if (hasGroupBy || hasHaving) {
            markRestriction = "GROUP BY/HAVING";
        } else if (hasTopLevelDistinct(selectIndex + 1, itemsBoundary)) {
            markRestriction = DISTINCT.name();
        }
        int listLastToken = lastSignificant(selectIndex + 1, itemsBoundary);
        if (listLastToken < 0) throw new IllegalArgumentException("Empty SELECT list");

        Layout result = new Layout();
        result.selectItemsEnd = sourceTokens.get(listLastToken).end();
        result.compound = compoundQuery;
        result.orderSiblings = siblings;
        result.markRestriction = markRestriction;
        result.statementEnd = statementEnd;
        result.hasRowSelection = limit >= 0 || offset >= 0 || fetch >= 0;

        int[] legacyWhereMarker = findLegacyWhereMarker();
        if (legacyWhereMarker != null) {
            result.legacyWhereMarkerStart = legacyWhereMarker[0];
            result.legacyWhereMarkerEnd = legacyWhereMarker[1];
        }

        int mainContentEnd = lastSignificantEnd(selectIndex + 1, statementEndIndex);
        result.statementContentEnd = mainContentEnd;

        if (where >= 0) {
            int afterWhere = nextSignificant(where + 1);
            if (afterWhere >= firstFollowingWhere) {
                throw new IllegalArgumentException("Empty WHERE clause");
            }
            result.whereWordEnd = sourceTokens.get(where).end();
            result.wherePredicateEnd = lastSignificantEnd(afterWhere, firstFollowingWhere);
        } else {
            // Граница уже найдена основным проходом по внешним SQL clauses.
            result.whereInsertion = firstWhereBoundary == statementEndIndex
                    ? mainContentEnd : sourceTokens.get(firstWhereBoundary).start();
        }
        if (order >= 0) {
            int actualEnd = firstFollowingOrder;
            result.orderStart = sourceTokens.get(order).start();
            int bodyStart = nextSignificant(order + (siblings ? 3 : 2));
            if (bodyStart >= actualEnd) throw new IllegalArgumentException("Empty ORDER BY clause");
            result.orderEnd = lastSignificantEnd(bodyStart, actualEnd);
        } else {
            result.orderInsertion = tail >= 0
                    ? sourceTokens.get(tail).start() : mainContentEnd;
        }

        boolean oracle = scanner instanceof OracleSqlScanner;

        if( oracle ) {
            if( limit >= 0 )
                result.rowLimitRestriction =
                        "Oracle LIMIT clause is not supported";

            if( rowLock >= 0 )
                result.rowLimitRestriction =
                        "Oracle row limit with row locking is not supported";

            if( fetch >= 0 ) {
                result.rowLimitStart = sourceTokens.get(fetch).start();

                int end = rowLock > fetch ? rowLock : statementEndIndex;
                result.rowLimitEnd = lastSignificantEnd(fetch, end);
            }
            else {
                result.rowLimitInsertion = rowLock >= 0
                        ? sourceTokens.get(rowLock).start()
                        : mainContentEnd;
            }
        }
        else {
            if( limit >= 0 && fetch >= 0 )
                result.rowLimitRestriction =
                        "Multiple row limit clauses are not supported";

            int existing = limit >= 0 ? limit : fetch;

            if( existing >= 0 ) {
                result.rowLimitStart = sourceTokens.get(existing).start();

                int end = statementEndIndex;
                if( offset > existing )
                    end = Math.min(end, offset);
                if( rowLock > existing )
                    end = Math.min(end, rowLock);

                result.rowLimitEnd = lastSignificantEnd(existing, end);
            }
            else {
                result.rowLimitInsertion = offset >= 0
                        ? sourceTokens.get(offset).start()
                        : rowLock >= 0
                        ? sourceTokens.get(rowLock).start()
                        : mainContentEnd;
            }
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

            if (open.end() != word.start()
                    || word.end() != close.start()) {
                continue;
            }

            boolean openBracket =
                    (open.kind() == SqlScanner.Kind.OPERATOR
                            || open.kind() == SqlScanner.Kind.SYMBOL)
                    && "<".equals(open.text(source));

            boolean markerWord =
                    word.kind() == SqlScanner.Kind.WORD
                    && word.isWord(source, "W");

            boolean closeBracket =
                    (close.kind() == SqlScanner.Kind.OPERATOR
                            || close.kind() == SqlScanner.Kind.SYMBOL)
                    && ">".equals(close.text(source));

            if (openBracket && markerWord && closeBracket) {
                return new int[] { open.start(), close.end() };
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

    private boolean orderClauseAt(int i) {
        return keywords(i, ORDER, BY) || keywords(i, ORDER, SIBLINGS, BY);
    }

    private boolean rowLockClauseAt(int i) {
        return keywords(i, FOR, UPDATE)
                || keywords(i, FOR, SHARE)
                || keywords(i, FOR, KEY)
                || keywords(i, FOR, NO);
    }

    private boolean tailAt(int i) {
        return in(keyword(i), LIMIT, OFFSET, FETCH)
                || rowLockClauseAt(i);
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
        switch (token.kind()) {
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
        return sourceTokens.get(i).end();
    }

    private boolean legacyTilde(int index) {
        if (index < 0 || index >= sourceTokens.size())
            return false;
        SqlScanner.Token token = sourceTokens.get(index);
        return (token.kind() == SYMBOL || token.kind() == OPERATOR)
                && token.end() == token.start() + 1 && source.charAt(token.start()) == '~';
    }

    private static boolean symbol(String sql, SqlScanner.Token token, String value) {
        return token.kind() == SYMBOL && token.text(sql).equals(value);
    }

    public static final class DataSetSource {
        private final SqlSelect select;
        private final String predicateAlias;

        private DataSetSource(SqlSelect select, String predicateAlias) {
            this.select = select;
            this.predicateAlias = predicateAlias;
        }

        public String sql() {
            return select.source;
        }

        public SqlSelect select() {
            return select;
        }

        public String predicateQualifier() {
            return predicateAlias == null ? "" : predicateAlias + ".";
        }
    }

    /** */
    private static final class Layout {

        int selectItemsEnd;

        int whereWordEnd = -1;

        int wherePredicateEnd = -1;

        int whereInsertion = -1;

        int legacyWhereMarkerStart = -1;
        int legacyWhereMarkerEnd = -1;

        int orderStart = -1;
        int orderEnd = -1;

        int orderInsertion = -1;

        int rowLimitStart = -1;
        int rowLimitEnd = -1;
        int rowLimitInsertion = -1;

        String rowLimitRestriction;

        boolean compound;
        int statementEnd;
        int statementContentEnd;
        boolean hasRowSelection;
        boolean orderSiblings;

        String markRestriction;
    }

   /** */
   private static final class Edit {

      final int start;
      final int end;

      final String replacement;

      final int priority;

      final List<NamedSourceRange> namedSources;

        Edit( int start, int end, String replacement, int priority )
        {
            this(start, end, replacement, priority, Collections.emptyList());
        }

        Edit(int start, int end, String replacement, int priority, List<NamedSourceRange> namedSources)
        {
            this.start = start;
            this.end = end;
            this.replacement = replacement;
            this.priority = priority;
            this.namedSources = namedSources;
        }
    }


}
