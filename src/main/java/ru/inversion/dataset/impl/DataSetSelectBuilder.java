package ru.inversion.dataset.impl;

import ru.inversion.dataset.IParameters;
import ru.inversion.dataset.ISQLExpression;
import ru.inversion.dataset.parser.lexer.SqlKeyword;
import ru.inversion.db.dialect.SqlDialect;
import ru.inversion.dataset.parser.jdbc.Parameter;
import ru.inversion.dataset.parser.jdbc.PreparedSql;
import ru.inversion.dataset.parser.SqlScannerFactory;
import ru.inversion.dataset.parser.lexer.SqlScanner;
import ru.inversion.dataset.parser.select.SqlSelect;
import ru.inversion.utils.Checks;
import ru.inversion.utils.S;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.IntPredicate;

/**
 * Основной builder одного SELECT для DataSet.
 * <p>
 * Владелец predicates, их типов и жизненного цикла — SQLDataSet.
 * Builder принимает текущий набор SQL-частей и не хранит бизнес-состояние DataSet.
 * <p>
 * Не является API построения агрегатных, INSERT/DELETE или mark-operation запросов.
 */
public class DataSetSelectBuilder {

    private final String sourceSql;

    private final SqlDialect dialect;
    private final SqlScanner scanner;
    private final boolean useLegacySourceShape;
    private final String tableAlias;
    private SqlSelect.DataSetSource dataSetSource;

    private final List<Part> predicates = new ArrayList<>();

    private Part sorting;

    private String afterOrderBy;

    private Integer rowLimit;

    private String mark;

    private String queryAlias;

    private String optimizerHint;

    private PreparedSql prepared;

    public DataSetSelectBuilder(String sql, SqlDialect dialect)
    {
        this(sql, dialect, false, null);
    }

    /** Для текстовых SELECT, полученных из SQLDataSet. */
    public static DataSetSelectBuilder forDataSet(String sql, SqlDialect dialect) {
        return new DataSetSelectBuilder(sql, dialect, true, null);
    }

    /** Для SQL, уже построенного по аннотации @Table. */
    public static DataSetSelectBuilder forDataSetTable(String sql, SqlDialect dialect, String tableAlias) {
        return new DataSetSelectBuilder(sql, dialect, false, tableAlias);
    }

    private DataSetSelectBuilder(String sql, SqlDialect dialect, boolean useLegacySourceShape, String tableAlias)
    {
        this.sourceSql = Checks.Require.text  ( sql, "sql");
        this.dialect   = Checks.Require.object( dialect, "dialect");
        this.useLegacySourceShape = useLegacySourceShape;
        this.tableAlias = tableAlias;

        this.scanner   = SqlScannerFactory.create(dialect);
    }

    /** Добавить строковый predicate с параметрами из общего контекста DataSet. */
    public DataSetSelectBuilder addPredicate(String sql) {

        if( !S.isNullOrEmpty(sql) )
        {
            predicates.add(new Part(sql, null));
            invalidate();
        }
        return this;
    }

    /** Добавить predicate с собственными fallback-значениями параметров. */
    public DataSetSelectBuilder addPredicate(ISQLExpression expression)
    {
        Checks.Require.object(expression, "expression");
        String sql = Checks.Require.text( expression.getSQL(), "expression.sql");

        predicates.add( new Part(sql, expression) );

        invalidate();

        return this;
    }

    /**
     * Задаёт alias обёртки. Для SQLDataSet необходимость обёртки
     * определяется по форме исходного запроса.
     */
    public DataSetSelectBuilder queryAlias(String alias) {
        String value = alias == null ? null : alias.trim();
        if( value != null && value.isEmpty() )
            value = null;

        if( value != null )
            validateAlias(value, scanner);

        queryAlias = value;
        dataSetSource = null;
        invalidate();

        return this;
    }

    /**
     * Добавляет optimizer hint после внешнего top-level SELECT.
     * Текст задаётся без delimiters комментария; если нужен Oracle-style hint,
     * caller передаёт строку, начинающуюся с '+'.
     */
    public DataSetSelectBuilder optimizerHint(String hint) {
        String value = hint == null ? null : hint.trim();
        if (value != null && value.isEmpty()) value = null;
        if (value != null && value.contains("*/"))
            throw new IllegalArgumentException("optimizerHint contains comment terminator");
        optimizerHint = value;
        invalidate();
        return this;
    }

    /** */
    public DataSetSelectBuilder orderBy(String sql) {
        sorting = S.isNullOrEmpty(sql) ? null : new Part( sql, null );
        invalidate();
        return this;
    }

    /** ORDER BY может иметь собственные fallback-параметры; DataSet всегда приоритетнее. */
    public DataSetSelectBuilder orderBy(ISQLExpression expression) {

        if (expression == null)
            return orderBy((String) null);
        String sql = Checks.Require.text( expression.getSQL(), "orderBy.sql");
        sorting = new Part(sql, expression);
        invalidate();
        return this;
    }

    /** Задаёт окончание SELECT после сортировки вместо ограничения rowLimit. */
    public DataSetSelectBuilder afterOrderBy(String sql) {
        String fragment = sql == null ? null : sql.trim();
        afterOrderBy = S.isNullOrEmpty(fragment) ? null : fragment;
        invalidate();
        return this;
    }

    /** Ограничить количество строк внешнего SELECT. null снимает override. */
    public DataSetSelectBuilder rowLimit(Integer value) {

        if( value != null && value < 0 )
            throw new IllegalArgumentException("rowLimit must be >= 0");

        rowLimit = value;
        invalidate();
        return this;
    }

    /** Выражение mark добавляется последним SELECT-столбцом с alias MARK. */
    public DataSetSelectBuilder mark( String expression )
    {
        mark = S.isNullOrEmpty( expression) ? null : expression;
        invalidate();
        return this;
    }

    /** Вернуть собранный текст без замены литералов и параметров. */
    public String buildSql() {
        return createSelect().buildSql();
    }

    /**
     * Создать и кешировать SQL + упорядоченные параметры для JDBC.
     * Значения IParameters считываются только при выполнении запроса.
     */
    public PreparedSql prepare() {
        if (prepared == null) {
            prepared = prepare(createSelect(), localPredicates(), localOrderBy());
        }
        return prepared;
    }

    private IntPredicate localPredicates() {
        return predicates.stream().anyMatch(part -> part.expression != null)
                ? index -> predicates.get(index).expression != null : null;
    }

    private boolean localOrderBy() {
        return sorting != null && sorting.expression != null;
    }

    private static PreparedSql prepare(SqlSelect select, IntPredicate localPredicate, boolean localOrderBy) {
        return localPredicate != null || localOrderBy
                ? select.prepare(localPredicate, localOrderBy) : select.prepare();
    }

    /** Готовит запрос reader с возможностью переиспользовать тело при смене сортировки. */
    public PreparedQuery prepareQuery(PreparedQuery previous) {
        if (getClass() != DataSetSelectBuilder.class)
            return new PreparedQuery(prepare(), null, null, null, this);

        OrderCache cached = previous == null ? null : previous.orderCache;
        String expression = sorting == null ? null : sorting.sql.trim();
        if (S.isNullOrEmpty(expression))
            expression = null;
        boolean localOrderBy = localOrderBy();
        int orderSourceId = localOrderBy ? predicates.size() : Parameter.GLOBAL_SOURCE;

        if (cached != null && cached.matches(this)) {
            SqlSelect.PreparedOrderBy order = cached.order.withOrderBy(expression, scanner, orderSourceId);
            if (order == null)
                return new PreparedQuery(prepare(), cached.predicates, sorting, null, null);
            OrderCache next = order == cached.order ? cached : new OrderCache(this, order, cached.predicates);
            return new PreparedQuery(order.sql(), cached.predicates, sorting, next, null);
        }

        IntPredicate localPredicate = localPredicates();
        List<Part> parts = predicates.isEmpty() ? Collections.emptyList() : new ArrayList<>(predicates);
        SqlSelect select = createSelect(true);
        if (select == null)
            return new PreparedQuery(prepare(), parts, sorting, null, null);

        SqlSelect.PreparedOrderBy order;
        try {
            order = select.prepareOrderBy(localPredicate);
        } catch (IllegalArgumentException ex) {
            if (expression == null)
                throw ex;
            // Новая сортировка может заменить фрагмент, который не проходит параметризацию.
            order = null;
        }
        if (order != null)
            order = order.withOrderBy(expression, scanner, orderSourceId);
        OrderCache cache = order != null && order.canReplace() ? new OrderCache(this, order, parts) : null;
        PreparedSql sql = order == null ? prepare(select, localPredicate, localOrderBy) : order.sql();
        return new PreparedQuery(sql, parts, sorting, cache, null);
    }

    /** */
    public List<Object> values(IParameters primary) {
        return values(primary, 0);
    }

    /**
     * Resolve this SELECT when it is embedded after preceding positional
     * parameters in a larger PreparedSql.
     *
     * Only primary '?' indexes are shifted. Expression fallback indexes and
     * explicit numbered parameters ($n) keep their own established namespace.
     */
    public List<Object> values( IParameters primary, int positionalOffset )
    {
        return values(prepare(), predicates, sorting, primary, positionalOffset);
    }

    private static List<Object> values(PreparedSql plan, List<Part> predicates, Part sorting,
                                       IParameters primary, int positionalOffset)
    {
        Checks.Require.object( primary, "primary");
        Checks.Numeric.positiveOrZero(positionalOffset,"positionalOffset");

        List<ISQLExpression> fallbacks = fallbacks(predicates, sorting);

        List<Object> values = new ArrayList<>(plan.getParameters().size());
        int positionalIndex = 0;

        for( Parameter parameter : plan.getParameters() )
        {
            Object value;

            switch (parameter.getKind()) {
                case LITERAL:
                    value = parameter.getValue();
                    break;

                case NAMED:
                    value = resolveNamed(
                            primary,
                            fallbacks,
                            parameter,
                            predicates,
                            sorting
                    );
                    break;

                case NUMBERED:
                    value = resolveIndexed(
                            primary,
                            fallbacks,
                            parameter.getNumber() - 1
                    );
                    break;

                case POSITIONAL:
                    int localIndex = positionalIndex++;
                    value = resolveIndexed(
                            primary,
                            fallbacks,
                            positionalOffset + localIndex,
                            localIndex
                    );
                    break;

                default:
                    throw new IllegalStateException(
                            "Unknown parameter kind: " + parameter.getKind());
            }

            values.add(value);
        }

        return Collections.unmodifiableList(values);
    }

    /** Значения, готовые для вызова через JDBC */
    public List<Object> jdbcValues(IParameters primary) {
        return PreparedSqlValues.jdbc( prepare(), values(primary) );
    }

    /** Исходный SQL, без предикатов и других настроек билдера. */
    public String sourceSql() {
        return sourceSql;
    }

    /** Фрагмент сортировки, заданный поверх исходного SQL. */
    public String orderBySql() {
        return sorting == null ? null : sorting.sql;
    }

    /** Квалификатор для генерируемого предиката, с точкой при наличии алиаса. */
    public String predicateQualifier() {
        if (useLegacySourceShape)
            return dataSetSource().predicateQualifier();
        String alias = queryAlias == null ? tableAlias : queryAlias;
        return alias == null ? "" : alias + ".";
    }

    private SqlSelect.DataSetSource dataSetSource() {
        if (dataSetSource == null)
            dataSetSource = new SqlSelect(sourceSql, dialect).dataSetSource(queryAlias, false);
        return dataSetSource;
    }

    /** */
    private SqlSelect createSelect()
    {
        return createSelect(false);
    }

    private SqlSelect createSelect(boolean reuseOrder)
    {
        String source;
        String sql;
        SqlSelect select;
        if (useLegacySourceShape) {
            SqlSelect.DataSetSource dataSet = dataSetSource();
            dataSetSource = null;
            select = dataSet.select();
            source = dataSet.sql();
            sql = source;
        } else {
            source = queryAlias == null ? sourceSql
                    : "SELECT " + queryAlias + ".* FROM (\n" + sourceSql + "\n) " + queryAlias;
            select = new SqlSelect(source, dialect);
            if (reuseOrder && select.isCompound())
                return null;
            sql = select.wrapCompoundForOuterClauses(!predicates.isEmpty(), sorting != null);
        }
        if (optimizerHint != null)
            sql = addOptimizerHint(sql);
        if (!source.equals(sql))
            select = new SqlSelect(sql, dialect);

        List<String> predicatesSql = new ArrayList<>(predicates.size());

        for( Part part : predicates )
            predicatesSql.add(part.sql);

        select.setPredicates(predicatesSql);
        select.setOrderBy(sorting == null ? null : sorting.sql);
        select.setRowLimit(afterOrderBy == null ? rowLimit : null);
        select.setAfterOrderBy(afterOrderBy);

        if( mark != null )
            select.setMark(mark, "MARK");

        return select;
    }

    /** */
    private String addOptimizerHint(String source) {

        int depth = 0;

        for (SqlScanner.Token token : scanner.scan(source))
        {
            if( token.kind() == SqlScanner.Kind.SYMBOL)
            {
                String text = token.text(source);

                if ("(".equals(text)) {
                    depth++;
                    continue;
                }
                if (")".equals(text)) {
                    depth--;
                    continue;
                }
            }

            if( depth == 0 && token.isKeyword(source, SqlKeyword.SELECT) )
            {
                return source.substring(0, token.end()) + " /*" + optimizerHint + "*/ " + source.substring(token.end());
            }
        }

        throw new IllegalArgumentException( "Top-level SELECT not found for optimizerHint");
    }

    /** Проверяет alias, который SQLDataSet подставляет после имени таблицы. */
    public static void validateTableAlias(String alias, SqlDialect dialect) {
        validateAlias(alias, SqlScannerFactory.create(dialect));
    }

    private static void validateAlias(String alias, SqlScanner scanner) {

        final List<SqlScanner.Token> tokens = scanner.scan(alias);

        if( tokens.size() != 1
            ||
            (
                tokens.get(0).kind() != SqlScanner.Kind.WORD
                &&
                tokens.get(0).kind() != SqlScanner.Kind.QUOTED_IDENTIFIER)
            )
        {
            throw new IllegalArgumentException("queryAlias must be one SQL identifier");
        }
    }

    /** */
    private static List<ISQLExpression> fallbacks(List<Part> predicates, Part sorting) {

        List<ISQLExpression> result = new ArrayList<>();

        for( Part part : predicates )
             addIfAbsent(result, part.expression);

        if( sorting != null )
            addIfAbsent(result, sorting.expression);

        return result;
    }

    /** */
    private static void addIfAbsent( List<ISQLExpression> values, ISQLExpression candidate )
    {
        if( candidate == null )
            return;

        for( ISQLExpression existing : values )
            if( existing == candidate )
                return;

        values.add(candidate);
    }

    /** */
    private static Object resolveNamed(IParameters primary, List<ISQLExpression> fallbacks, Parameter parameter,
                                       List<Part> predicates, Part sorting)
    {
        String name = parameter.getName();
        if( primary.hasParameter(name) )
            return primary.getParameter(name);

        int sourceId = parameter.getSourceId();
        if (sourceId >= 0 && sourceId <= predicates.size()) {
            Part source = sourceId == predicates.size() ? sorting : predicates.get(sourceId);
            if (source != null && source.expression != null && source.expression.hasParameter(name))
                return source.expression.getParameter(name);
        }

        boolean found = false;
        Object value = null;

        for (ISQLExpression fallback : fallbacks)
        {
            if (!fallback.hasParameter(name))
                continue;

            if( found )
                throw conflict(":" + name);

            found = true;

            value = fallback.getParameter(name);
        }

        if (!found)
            throw missing(":" + name);

        return value;
    }

    /** */
    private static Object resolveIndexed( IParameters primary, List<ISQLExpression> fallbacks, int index )
    {
        return resolveIndexed( primary, fallbacks, index, index );
    }

    /** */
    private static Object resolveIndexed (
        IParameters primary,
        List<ISQLExpression> fallbacks,
        int primaryIndex,
        int fallbackIndex
    )
    {
        if( primary.hasParameter(primaryIndex) )
            return primary.getParameter(primaryIndex);

        boolean found = false;
        Object value = null;

        for( ISQLExpression fallback : fallbacks ) {
            if( !fallback.hasParameter(fallbackIndex) )
                continue;

            if( found )
                throw conflict("index " + primaryIndex);

            found = true;
            value = fallback.getParameter(fallbackIndex);
        }

        if( !found )
            throw missing("index " + primaryIndex);

        return value;
    }

    /** */
    private static IllegalStateException conflict(String name) {
        return new IllegalStateException("Conflicting SQL parameter " + name + " in ISQLExpression");
    }

    /** */
    private static IllegalStateException missing(String name) {
        return new IllegalStateException("Missing SQL parameter " + name);
    }

    private void invalidate() {
        prepared = null;
    }

    /** План reader и источники актуальных значений параметров. */
    public static final class PreparedQuery {
        private final PreparedSql sql;
        private final List<Part> predicates;
        private final Part sorting;
        private final OrderCache orderCache;
        private final DataSetSelectBuilder customBuilder;

        private PreparedQuery(PreparedSql sql, List<Part> predicates, Part sorting, OrderCache orderCache,
                              DataSetSelectBuilder customBuilder) {
            this.sql = sql;
            this.predicates = predicates;
            this.sorting = sorting;
            this.orderCache = orderCache;
            this.customBuilder = customBuilder;
        }

        public PreparedSql sql() {
            return sql;
        }

        public List<Object> jdbcValues(IParameters primary) {
            if (customBuilder != null)
                return customBuilder.prepare() == sql
                        ? customBuilder.jdbcValues(primary) : PreparedSqlValues.jdbc(sql, primary);
            return PreparedSqlValues.jdbc(sql, values(sql, predicates, sorting, primary, 0));
        }
    }

    /** Настройки, от которых зависит подготовленный SQL вне сортировки. */
    private static final class OrderCache {
        final SqlSelect.PreparedOrderBy order;
        final List<Part> predicates;
        final String source;
        final SqlDialect dialect;
        final boolean legacyShape;
        final String tableAlias;
        final String queryAlias;
        final String hint;
        final String mark;
        final String afterOrderBy;
        final Integer rowLimit;

        OrderCache(DataSetSelectBuilder builder, SqlSelect.PreparedOrderBy order, List<Part> predicates) {
            this.order = order;
            this.predicates = predicates;
            source = builder.sourceSql;
            dialect = builder.dialect;
            legacyShape = builder.useLegacySourceShape;
            tableAlias = builder.tableAlias;
            queryAlias = builder.queryAlias;
            hint = builder.optimizerHint;
            mark = builder.mark;
            afterOrderBy = builder.afterOrderBy;
            rowLimit = builder.rowLimit;
        }

        boolean matches(DataSetSelectBuilder builder) {
            if (dialect != builder.dialect || legacyShape != builder.useLegacySourceShape
                    || !source.equals(builder.sourceSql) || !Objects.equals(tableAlias, builder.tableAlias)
                    || !Objects.equals(queryAlias, builder.queryAlias) || !Objects.equals(hint, builder.optimizerHint)
                    || !Objects.equals(mark, builder.mark) || !Objects.equals(afterOrderBy, builder.afterOrderBy)
                    || !Objects.equals(rowLimit, builder.rowLimit) || predicates.size() != builder.predicates.size())
                return false;

            for (int i = 0; i < predicates.size(); i++) {
                Part cached = predicates.get(i);
                Part current = builder.predicates.get(i);
                if (cached.expression != current.expression || !cached.sql.equals(current.sql))
                    return false;
            }
            return true;
        }
    }

    private static final class Part {
        final String sql;
        final ISQLExpression expression;
        Part( String sql, ISQLExpression expression ) {
            this.sql = sql;
            this.expression = expression;
        }
    }
}
