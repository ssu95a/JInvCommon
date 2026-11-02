package ru.inversion.dataset.impl;

import ru.inversion.dataset.IParameters;
import ru.inversion.dataset.ISQLExpression;
import ru.inversion.db.dialect.SqlDialect;
import ru.inversion.utils.sparser.jdbc.Parameter;
import ru.inversion.utils.sparser.jdbc.PreparedSql;
import ru.inversion.utils.sparser.SqlScannerFactory;
import ru.inversion.utils.sparser.lexer.SqlScanner;
import ru.inversion.utils.sparser.select.SqlSelect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Сборка одного SELECT для DataSet.
 *
 * Владелец predicates, их типов и жизненного цикла — SQLDataSet.
 * Builder принимает текущий набор SQL-частей и не хранит бизнес-состояние DataSet.
 *
 * Не является API построения агрегатных, INSERT/DELETE или mark-operation запросов.
 */
public final class NextSelectBuilder {

    private final String sourceSql;
    private final SqlDialect dialect;
    private final SqlScanner scanner;
    private final List<Part> predicates = new ArrayList<>();
    private Part sorting;
    private Integer rowLimit;
    private String mark;
    private String queryAlias;
    private String optimizerHint;

    private PreparedSql prepared;

    public NextSelectBuilder(String sql, SqlDialect dialect) {
        this.sourceSql = Objects.requireNonNull(sql, "sql");
        this.dialect = Objects.requireNonNull(dialect, "dialect");
        this.scanner = SqlScannerFactory.create(dialect);
    }

    /** Добавить строковый predicate с параметрами из общего контекста DataSet. */
    public NextSelectBuilder addPredicate(String sql) {
        if (sql != null && !sql.trim().isEmpty()) {
            predicates.add(new Part(sql, null));
            invalidate();
        }
        return this;
    }

    /** Добавить predicate с собственными fallback-значениями параметров. */
    public NextSelectBuilder addPredicate(ISQLExpression expression) {
        Objects.requireNonNull(expression, "expression");
        String sql = Objects.requireNonNull(expression.getSQL(), "expression.sql");
        if (sql.trim().isEmpty()) throw new IllegalArgumentException("Empty predicate expression");
        predicates.add(new Part(sql, expression));
        invalidate();
        return this;
    }

    /**
     * Оборачивает исходный SELECT внешним SELECT с alias.
     * Predicates, mark и ORDER BY применяются к внешнему SELECT.
     */
    public NextSelectBuilder queryAlias(String alias) {
        String value = alias == null ? null : alias.trim();
        queryAlias = value == null || value.isEmpty() ? null : value;
        if (queryAlias != null) validateAlias(queryAlias);
        invalidate();
        return this;
    }

    /**
     * Добавляет optimizer hint после внешнего top-level SELECT.
     * Текст задаётся без delimiters комментария; если нужен Oracle-style hint,
     * caller передаёт строку, начинающуюся с '+'.
     */
    public NextSelectBuilder optimizerHint(String hint) {
        String value = hint == null ? null : hint.trim();
        if (value != null && value.isEmpty()) value = null;
        if (value != null && value.contains("*/"))
            throw new IllegalArgumentException("optimizerHint contains comment terminator");
        optimizerHint = value;
        invalidate();
        return this;
    }

    public NextSelectBuilder orderBy(String sql) {
        sorting = sql == null || sql.trim().isEmpty() ? null : new Part(sql, null);
        invalidate();
        return this;
    }

    /** ORDER BY может иметь собственные fallback-параметры; DataSet всегда приоритетнее. */
    public NextSelectBuilder orderBy(ISQLExpression expression) {
        if (expression == null) return orderBy((String) null);
        String sql = Objects.requireNonNull(expression.getSQL(), "orderBy.sql");
        if (sql.trim().isEmpty()) throw new IllegalArgumentException("Empty ORDER BY expression");
        sorting = new Part(sql, expression);
        invalidate();
        return this;
    }

    /** Ограничить количество строк внешнего SELECT. null снимает override. */
    public NextSelectBuilder rowLimit(Integer value) {
        if( value != null && value < 0 )
            throw new IllegalArgumentException("rowLimit must be >= 0");

        rowLimit = value;
        invalidate();
        return this;
    }

    /** Выражение mark добавляется последним SELECT-столбцом с alias MARK. */
    public NextSelectBuilder mark(String expression) {
        mark = expression == null || expression.trim().isEmpty() ? null : expression;
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

    /**
     * Resolve runtime values directly in physical JDBC placeholder order.
     * primary (DataSet/callback) has priority over ISQLExpression fallbacks.
     */
    public List<Object> values(IParameters primary) {
        Objects.requireNonNull(primary, "primary");

        PreparedSql plan = prepare();
        List<ISQLExpression> expressions = expressions();

        List<Object> values = new ArrayList<>(plan.getParameters().size());
        int positionalIndex = 0;

        for (Parameter parameter : plan.getParameters()) {
            Object value;

            switch (parameter.getKind()) {
                case LITERAL:
                    value = parameter.getValue();
                    break;

                case NAMED:
                    if (primary.hasParameter(parameter.getName()))
                        value = primary.getParameter(parameter.getName());
                    else
                        value = findUniqueNamed(
                                expressions, parameter.getName())
                                .getParameter(parameter.getName());
                    break;

                case NUMBERED: {
                    int index = parameter.getNumber() - 1;
                    if (primary.hasParameter(index))
                        value = primary.getParameter(index);
                    else
                        value = findUniqueIndexed(expressions, index)
                                .getParameter(index);
                    break;
                }

                case POSITIONAL: {
                    int index = positionalIndex++;

                    if (primary.hasParameter(index))
                        value = primary.getParameter(index);
                    else
                        value = findUniqueIndexed(expressions, index)
                                .getParameter(index);

                    break;
                }

                default:
                    throw new IllegalStateException(
                            "Unknown parameter kind: " + parameter.getKind());
            }

            values.add(value);
        }

        return Collections.unmodifiableList(values);
    }

    /** JDBC-ready values with legacy conversion preserved. */
    public List<Object> jdbcValues(IParameters primary) {
        return PreparedSqlValues.jdbc(prepare(), values(primary));
    }

    private SqlSelect createSelect() {
        SqlSelect select = new SqlSelect(sourceForSelect(), dialect);
        List<String> predicatesSql = new ArrayList<>(predicates.size());

        for (Part part : predicates)
            predicatesSql.add(part.sql);

        select.setPredicates(predicatesSql);
        select.setOrderBy(sorting == null ? null : sorting.sql);
        select.setRowLimit(rowLimit);

        if (mark != null)
            select.setMark(mark, "MARK");

        return select;
    }

    private String sourceForSelect() {
        String source = queryAlias == null
                ? sourceSql
                : "SELECT " + queryAlias + ".* FROM (\n"
                    + sourceSql + "\n) " + queryAlias;

        return optimizerHint == null ? source : addOptimizerHint(source);
    }

    private String addOptimizerHint(String source) {
        int depth = 0;

        for (SqlScanner.Token token : scanner.scan(source)) {
            if (token.kind() == SqlScanner.Kind.SYMBOL) {
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

            if (depth == 0
                    && token.kind() == SqlScanner.Kind.WORD
                    && token.isWord(source, "SELECT")) {
                return source.substring(0, token.end())
                        + " /*" + optimizerHint + "*/ "
                        + source.substring(token.end());
            }
        }

        throw new IllegalArgumentException(
                "Top-level SELECT not found for optimizerHint");
    }

    private void validateAlias(String alias) {
        List<SqlScanner.Token> tokens = scanner.scan(alias);
        if (tokens.size() != 1
                || (tokens.get(0).kind() != SqlScanner.Kind.WORD
                && tokens.get(0).kind() != SqlScanner.Kind.QUOTED_IDENTIFIER)) {
            throw new IllegalArgumentException("queryAlias must be one SQL identifier");
        }
    }

    private List<ISQLExpression> expressions() {
        List<ISQLExpression> result = new ArrayList<>();
        for (Part part : predicates) addIfAbsent(result, part.expression);
        if (sorting != null) addIfAbsent(result, sorting.expression);
        return result;
    }

    private static void addIfAbsent(List<ISQLExpression> values, ISQLExpression candidate) {
        if (candidate == null) return;
        for (ISQLExpression existing : values) if (existing == candidate) return;
        values.add(candidate);
    }

    private static ISQLExpression findUniqueNamed(List<ISQLExpression> expressions, String name) {
        ISQLExpression owner = null;
        for (ISQLExpression expr : expressions) {
            if (expr.hasParameter(name)) {
                if (owner != null) throw conflict(":" + name);
                owner = expr;
            }
        }
        if (owner == null) throw missing(":" + name);
        return owner;
    }

    private static ISQLExpression findUniqueIndexed(List<ISQLExpression> expressions, int index) {
        ISQLExpression owner = null;
        for (ISQLExpression expr : expressions) {
            if (expr.hasParameter(index)) {
                if (owner != null) throw conflict("$" + (index + 1));
                owner = expr;
            }
        }
        if (owner == null) throw missing("$ / ?" + (index + 1));
        return owner;
    }

    private static IllegalStateException conflict(String name) {
        return new IllegalStateException("Conflicting SQL parameter " + name + " in ISQLExpression");
    }

    private static IllegalStateException missing(String name) {
        return new IllegalStateException("Missing SQL parameter " + name);
    }

    private void invalidate() {
        prepared = null;
    }

    private static final class Part {
        final String sql;
        final ISQLExpression expression;
        Part(String sql, ISQLExpression expression) {
            this.sql = sql;
            this.expression = expression;
        }
    }

}
