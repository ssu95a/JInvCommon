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
        if (prepared == null)
            prepared = createSelect().prepare();
        return prepared;
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
        Checks.Require.object( primary, "primary");
        Checks.Numeric.positiveOrZero(positionalOffset,"positionalOffset");

        PreparedSql plan = prepare();

        List<ISQLExpression> fallbacks = fallbacks();

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
                            parameter.getName()
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
        select.setRowLimit(rowLimit);

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
    private List<ISQLExpression> fallbacks() {

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
    private static Object resolveNamed ( IParameters primary, List<ISQLExpression> fallbacks, String name )
    {
        if( primary.hasParameter(name) )
            return primary.getParameter(name);

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

    private static final class Part {
        final String sql;
        final ISQLExpression expression;
        Part( String sql, ISQLExpression expression ) {
            this.sql = sql;
            this.expression = expression;
        }
    }
}
