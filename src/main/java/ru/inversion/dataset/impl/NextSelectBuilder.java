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
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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
     * Представление единого пространства параметров. primary — параметры SQLDataSet,
     * включая callback приоритеты, предоставленные самим DataSet.
     *
     * ВАЖНО: hasParameter() должен различать отсутствие значения и явный null.
     */
    public IParameters parameters(final IParameters primary) {
        Objects.requireNonNull(primary, "primary");
        PreparedSql plan = prepare();
        final List<ISQLExpression> expressions = expressions();
        final Map<Integer, List<ISQLExpression>> numberedOwners = numberedOwners();
        final List<LocalPositional> localPositions = localPositionals(plan);

        Set<String> namedReferences = new LinkedHashSet<>();
        Set<Integer> indexedReferences = new LinkedHashSet<>();
        for (Parameter parameter : plan.getParameters()) {
            switch (parameter.getKind()) {
                case NAMED: namedReferences.add(parameter.getName()); break;
                case NUMBERED: indexedReferences.add(parameter.getNumber() - 1); break;
                default: break;
            }
        }
        for (int i = 0; i < localPositions.size(); i++)
            if (localPositions.get(i) != null)
                indexedReferences.add(i);

        // Противоречивое состояние обнаруживаем до создания JDBC statement.
        for (String name : namedReferences) {
            if (!primary.hasParameter(name)) findUniqueNamed(expressions, name);
        }
        for (Integer index : indexedReferences) {
            if (primary.hasParameter(index)) continue;
            LocalPositional pos = index < localPositions.size()
                    ? localPositions.get(index) : null;
            if (pos != null) {
                if (!pos.expression.hasParameter(pos.localIndex)) throw missing("?" + (index + 1));
                requireNoNumberedConflict(numberedOwners, index, pos.expression);
            } else {
                findUniqueIndexed(expressions, index);
            }
        }

        return new IParameters() {
            @Override public Object getParameter(String name) {
                if (primary.hasParameter(name)) return primary.getParameter(name);
                return findUniqueNamed(expressions, name).getParameter(name);
            }

            @Override public Object getParameter(int index) {
                if (primary.hasParameter(index)) return primary.getParameter(index);
                LocalPositional pos = index < localPositions.size()
                        ? localPositions.get(index) : null;
                if (pos != null) {
                    if (!pos.expression.hasParameter(pos.localIndex)) throw missing("?" + (index + 1));
                    requireNoNumberedConflict(numberedOwners, index, pos.expression);
                    return pos.expression.getParameter(pos.localIndex);
                }
                return findUniqueIndexed(expressions, index).getParameter(index);
            }

            @Override public boolean hasParameter(String name) {
                if (primary.hasParameter(name)) return true;
                for (ISQLExpression expr : expressions) if (expr.hasParameter(name)) return true;
                return false;
            }

            @Override public boolean hasParameter(int index) {
                if (primary.hasParameter(index)) return true;
                LocalPositional local = index < localPositions.size()
                        ? localPositions.get(index) : null;
                if (local != null) return local.expression.hasParameter(local.localIndex);
                for (ISQLExpression expr : expressions) if (expr.hasParameter(index)) return true;
                return false;
            }
        };
    }

    private SqlSelect createSelect() {
        SqlSelect select = new SqlSelect(sourceForSelect(), dialect);
        List<String> predicatesSql = new ArrayList<>(predicates.size());
        List<Integer> sourceIds = new ArrayList<>(predicates.size());

        for (int i = 0; i < predicates.size(); i++) {
            Part part = predicates.get(i);
            predicatesSql.add(part.sql);
            sourceIds.add(part.expression == null ? Parameter.GLOBAL_SOURCE : i);
        }

        select.setPredicates(predicatesSql, sourceIds);

        if (sorting == null) {
            select.setOrderBy(null);
        } else {
            int sourceId = sorting.expression == null
                    ? Parameter.GLOBAL_SOURCE : predicates.size();
            select.setOrderBy(sorting.sql, sourceId);
        }

        if (mark != null) select.setMark(mark, "MARK");
        return select;
    }

    /**
     * POSITIONAL parameters are indexed in final SQL order.
     * sourceId tells which local ISQLExpression owns a particular '?'.
     */
    private List<LocalPositional> localPositionals(PreparedSql plan) {
        List<ISQLExpression> sources = sourceExpressions();
        List<LocalPositional> result = new ArrayList<>();
        Map<Integer,Integer> localIndexes = new HashMap<>();

        for (Parameter parameter : plan.getParameters()) {
            if (parameter.getKind() != Parameter.Kind.POSITIONAL)
                continue;

            int sourceId = parameter.getSourceId();
            if (sourceId == Parameter.GLOBAL_SOURCE) {
                result.add(null);
                continue;
            }

            if (sourceId < 0 || sourceId >= sources.size()
                    || sources.get(sourceId) == null) {
                throw new IllegalStateException(
                        "Unknown local SQL parameter source: " + sourceId);
            }

            Integer localIndex = localIndexes.get(sourceId);
            int index = localIndex == null ? 0 : localIndex;
            localIndexes.put(sourceId, index + 1);

            result.add(new LocalPositional(sources.get(sourceId), index));
        }

        return result;
    }

    private List<ISQLExpression> sourceExpressions() {
        List<ISQLExpression> result = new ArrayList<>(predicates.size() + 1);
        for (Part part : predicates)
            result.add(part.expression);
        if (sorting != null)
            result.add(sorting.expression);
        return result;
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

    /** Только явно использующие $n expressions могут конфликтовать с локальным ? в этой позиции. */
    private Map<Integer, List<ISQLExpression>> numberedOwners() {
        Map<Integer, List<ISQLExpression>> result = new HashMap<>();
        List<Part> parts = new ArrayList<>(predicates);
        if (sorting != null) parts.add(sorting);
        for (Part part : parts) {
            if (part.expression == null) continue;
            for (SqlScanner.Token token : scanner.scan(part.sql)) {
                if (token.kind() != SqlScanner.Kind.NUMBERED_PARAMETER) continue;
                int number = Integer.parseInt(token.text(part.sql).substring(1));
                if (number <= 0) throw new IllegalArgumentException("Invalid numbered parameter $" + number);
                int index = number - 1;
                List<ISQLExpression> owners = result.computeIfAbsent(index, k -> new ArrayList<>());
                addIfAbsent(owners, part.expression);
            }
        }
        return result;
    }

    private static void requireNoNumberedConflict(Map<Integer, List<ISQLExpression>> numbered,
                                                   int globalIndex, ISQLExpression local) {
        List<ISQLExpression> owners = numbered.get(globalIndex);
        if (owners == null) return;
        for (ISQLExpression owner : owners) {
            if (owner != local && owner.hasParameter(globalIndex)) {
                throw conflict("? / $" + (globalIndex + 1));
            }
        }
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

    private static final class LocalPositional {
        final ISQLExpression expression;
        final int localIndex;
        LocalPositional(ISQLExpression expression, int localIndex) {
            this.expression = expression;
            this.localIndex = localIndex;
        }
    }
}
