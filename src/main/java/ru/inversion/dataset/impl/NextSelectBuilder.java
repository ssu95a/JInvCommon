package ru.inversion.dataset.impl;

import ru.inversion.dataset.IParameters;
import ru.inversion.dataset.ISQLExpression;
import ru.inversion.db.dialect.SqlDialect;
import ru.inversion.utils.sparser.jdbc.Parameter;
import ru.inversion.utils.sparser.jdbc.PreparedSql;
import ru.inversion.utils.sparser.lexer.OracleSqlScanner;
import ru.inversion.utils.sparser.lexer.PostgresSqlScanner;
import ru.inversion.utils.sparser.lexer.SqlScanner;
import ru.inversion.utils.sparser.select.SqlSelect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
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

    private static final String LOCAL_PARAMETER_PREFIX = "__sparser_local_pos_";

    private final String sourceSql;
    private final SqlDialect dialect;
    private final SqlScanner scanner;
    private final List<Part> predicates = new ArrayList<>();
    private Part sorting;
    private String mark;
    private String queryAlias;
    private String optimizerHint;

    private PreparedSql prepared;
    // Ключ — индекс среди POSITIONAL в финальном PreparedSql, а НЕ JDBC position.
    private Map<Integer, LocalPositional> localPositionals = Collections.emptyMap();

    public NextSelectBuilder(String sql, SqlDialect dialect) {
        this.sourceSql = Objects.requireNonNull(sql, "sql");
        this.dialect = Objects.requireNonNull(dialect, "dialect");
        switch (dialect.type()) {
            case ORACLE: scanner = new OracleSqlScanner(); break;
            case POSTGRES: scanner = new PostgresSqlScanner(); break;
            default: throw new IllegalArgumentException("Unsupported SQL dialect: " + dialect);
        }
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
     * Текст задаётся без /* */; если нужен Oracle-style hint,
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
        return createSelect(false, new LinkedHashMap<String, LocalPositional>()).buildSql();
    }

    /**
     * Создать и кешировать SQL + упорядоченные параметры для JDBC.
     * Значения IParameters считываются только при выполнении запроса.
     */
    public PreparedSql prepare() {
        if (prepared != null) return prepared;

        checkReservedParameters();
        Map<String, LocalPositional> locals = new LinkedHashMap<>();
        SqlSelect select = createSelect(true, locals);
        PreparedSql compiled = select.prepare();
        List<Parameter> parameters = new ArrayList<>(compiled.getParameters().size());
        Map<Integer, LocalPositional> positions = new HashMap<>();
        int positionalIndex = 0;

        for (Parameter parameter : compiled.getParameters()) {
            if (parameter.getKind() == Parameter.Kind.POSITIONAL) {
                parameters.add(parameter);
                positionalIndex++;
            } else if (parameter.getKind() == Parameter.Kind.NAMED
                    && locals.containsKey(parameter.getName())) {
                positions.put(positionalIndex++, locals.get(parameter.getName()));
                parameters.add(Parameter.positional());
            } else {
                parameters.add(parameter);
            }
        }

        // Commit only after compilation succeeded.
        PreparedSql result = new PreparedSql(compiled.getSql(), parameters);
        localPositionals = Collections.unmodifiableMap(positions);
        prepared = result;
        return result;
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
        final Map<Integer, LocalPositional> localPositions = localPositionals;
        final List<ISQLExpression> expressions = expressions();
        final Map<Integer, List<ISQLExpression>> numberedOwners = numberedOwners();

        Set<String> namedReferences = new LinkedHashSet<>();
        Set<Integer> indexedReferences = new LinkedHashSet<>();
        for (Parameter parameter : plan.getParameters()) {
            switch (parameter.getKind()) {
                case NAMED: namedReferences.add(parameter.getName()); break;
                case NUMBERED: indexedReferences.add(parameter.getNumber() - 1); break;
                default: break;
            }
        }
        for (Integer localIndex : localPositions.keySet()) indexedReferences.add(localIndex);

        // Противоречивое состояние обнаруживаем до создания JDBC statement.
        for (String name : namedReferences) {
            if (!primary.hasParameter(name)) findUniqueNamed(expressions, name);
        }
        for (Integer index : indexedReferences) {
            if (primary.hasParameter(index)) continue;
            if (localPositions.containsKey(index)) {
                LocalPositional pos = localPositions.get(index);
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
                LocalPositional pos = localPositions.get(index);
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
                LocalPositional local = localPositions.get(index);
                if (local != null) return local.expression.hasParameter(local.localIndex);
                for (ISQLExpression expr : expressions) if (expr.hasParameter(index)) return true;
                return false;
            }
        };
    }

    private SqlSelect createSelect(boolean withLocalBindings,
                                   Map<String, LocalPositional> bindings) {
        SqlSelect select = new SqlSelect(sourceForSelect(), dialect);
        List<String> predicatesSql = new ArrayList<>(predicates.size());
        int id = 0;
        for (Part part : predicates) {
            predicatesSql.add(withLocalBindings ? localize(part, id, bindings) : part.sql);
            id++;
        }
        select.setPredicates(predicatesSql);
        select.setOrderBy(sorting == null ? null :
                (withLocalBindings ? localize(sorting, id, bindings) : sorting.sql));
        if (mark != null) select.setMark(mark, "MARK");
        return select;
    }

    /**
     * Временные служебные имена создаются только для локальных ? во время сборки.
     * В финальном PreparedSql остаётся только обычный POSITIONAL.
     */
    private String localize(Part part, int partId, Map<String, LocalPositional> bindings) {
        if (part.expression == null) return part.sql;
        String sql = part.sql;
        StringBuilder text = new StringBuilder(sql.length() + 32);
        int copied = 0;
        int index = 0;
        for (SqlScanner.Token token : scanner.scan(sql)) {
            if (token.getKind() != SqlScanner.Kind.POSITIONAL_PARAMETER) continue;
            String name = LOCAL_PARAMETER_PREFIX + partId + "_" + index;
            text.append(sql, copied, token.getStart());
            text.append(':').append(name);
            bindings.put(name, new LocalPositional(part.expression, index));
            copied = token.getEnd();
            index++;
        }
        text.append(sql, copied, sql.length());
        return text.toString();
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
            if (token.getKind() == SqlScanner.Kind.SYMBOL) {
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
                    && token.getKind() == SqlScanner.Kind.WORD
                    && token.isWord(source, "SELECT")) {
                return source.substring(0, token.getEnd())
                        + " /*" + optimizerHint + "*/ "
                        + source.substring(token.getEnd());
            }
        }

        throw new IllegalArgumentException(
                "Top-level SELECT not found for optimizerHint");
    }

    private void validateAlias(String alias) {
        List<SqlScanner.Token> tokens = scanner.scan(alias);
        if (tokens.size() != 1
                || (tokens.get(0).getKind() != SqlScanner.Kind.WORD
                && tokens.get(0).getKind() != SqlScanner.Kind.QUOTED_IDENTIFIER)) {
            throw new IllegalArgumentException("queryAlias must be one SQL identifier");
        }
    }

    private void checkReservedParameters() {
        checkReserved(sourceForSelect());
        for (Part part : predicates) checkReserved(part.sql);
        if (sorting != null) checkReserved(sorting.sql);
        if (mark != null) checkReserved(mark);
    }

    private void checkReserved(String sql) {
        for (SqlScanner.Token token : scanner.scan(sql)) {
            if (token.getKind() == SqlScanner.Kind.NAMED_PARAMETER
                    && token.text(sql).substring(1).startsWith(LOCAL_PARAMETER_PREFIX)) {
                throw new IllegalArgumentException("Reserved SQL parameter prefix " + LOCAL_PARAMETER_PREFIX);
            }
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
                if (token.getKind() != SqlScanner.Kind.NUMBERED_PARAMETER) continue;
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
        localPositionals = Collections.emptyMap();
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
