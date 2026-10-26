package ru.inversion.dataset.impl;

import ru.inversion.dataset.SQLDataSet;
import ru.inversion.dataset.aggr.AggrFuncEnum;
import ru.inversion.db.dialect.SqlDialect;
import ru.inversion.tc.TaskContext;
import ru.inversion.utils.S;
import ru.inversion.utils.Triplet;
import ru.inversion.utils.sparser.lexer.OracleSqlScanner;
import ru.inversion.utils.sparser.lexer.PostgresSqlScanner;
import ru.inversion.utils.sparser.lexer.SqlScanner;

import javax.persistence.Table;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Compatibility facade for the former EntitySelectBuilder.
 *
 * Ordinary SELECT construction is delegated to NextSelectBuilder.
 * The legacy SelectBuilder hierarchy is intentionally not restored.
 */
public class EntitySelectBuilder {

    private final Class entityClass;
    private final TaskContext taskContext;
    private final SqlDialect explicitDialect;

    private String hint;
    private String orderBy;
    private String mark;
    private String queryAlias;
    private String aggrTable;
    private boolean additionalTags;

    private List<String> predicateList;
    private List<Triplet<String,AggrFuncEnum,String>> aggrList;

    /** Compatibility constructor. TaskContext is required when build() is called. */
    public EntitySelectBuilder( Class entityClass ) {
        this.entityClass = Objects.requireNonNull(entityClass, "entityClass is null");
        this.taskContext = null;
        this.explicitDialect = null;
    }

    /** Compatibility constructor. */
    public EntitySelectBuilder( Class entityClass, TaskContext tc ) {
        this.entityClass = Objects.requireNonNull(entityClass, "entityClass is null");
        this.taskContext = Objects.requireNonNull(tc, "TaskContext is null");
        this.explicitDialect = null;
    }

    /** Internal constructor for package tests and DB-independent composition. */
    EntitySelectBuilder( Class entityClass, SqlDialect dialect ) {
        this.entityClass = Objects.requireNonNull(entityClass, "entityClass is null");
        this.taskContext = null;
        this.explicitDialect = Objects.requireNonNull(dialect, "dialect is null");
    }

    public EntitySelectBuilder additionalTags( boolean value ) {
        this.additionalTags = value;
        return this;
    }

    public EntitySelectBuilder hint( String value ) {
        this.hint = value;
        return this;
    }

    public String getAggrTable() {
        return aggrTable;
    }

    public EntitySelectBuilder aggrTable( String value ) {
        this.aggrTable = value;
        return this;
    }

    public String getQueryAlias() {
        return queryAlias;
    }

    public EntitySelectBuilder queryAlias( String value ) {
        String alias = value == null ? null : value.trim();
        this.queryAlias = S.isNullOrEmpty(alias) ? null : alias;
        return this;
    }

    public EntitySelectBuilder orderBy( String value ) {
        this.orderBy = value;
        return this;
    }

    public EntitySelectBuilder mark( String value ) {
        this.mark = value;
        return this;
    }

    public EntitySelectBuilder addPredicat( String predicate ) {
        if( S.isNotNullOrEmpty(predicate) ) {
            if( predicateList == null )
                predicateList = new ArrayList<>();
            predicateList.add(predicate);
        }
        return this;
    }

    public EntitySelectBuilder addPredicat( Collection<String> predicates ) {
        if( predicates != null && !predicates.isEmpty() ) {
            if( predicateList == null )
                predicateList = new ArrayList<>();
            predicateList.addAll(predicates);
        }
        return this;
    }

    public EntitySelectBuilder addAggregator( String columnExpr, AggrFuncEnum func, String alias ) {
        if( aggrList == null )
            aggrList = new ArrayList<>();
        aggrList.add( Triplet.makeTriplet(columnExpr, func, alias) );
        return this;
    }

    /** Keeps queryAlias, matching the former SelectBuilder.clear() behavior. */
    public void clear() {
        hint = null;
        orderBy = null;
        mark = null;
        predicateList = null;
        aggrList = null;
        additionalTags = false;
        aggrTable = null;
    }

    public String build() {
        if( additionalTags )
            throw new UnsupportedOperationException(
                    "additionalTags is not supported by the new SELECT pipeline");

        if( aggrList != null && !aggrList.isEmpty() )
            return buildAggregate();

        NextSelectBuilder builder = new NextSelectBuilder(buildEntitySource(), dialect());

        if( predicateList != null )
            for( String predicate : predicateList )
                builder.addPredicate(predicate);

        builder.orderBy(orderBy);
        builder.mark(mark);

        String result = builder.buildSql();
        clear();
        return result;
    }

    public String getFromObj() {
        return S.isNullOrEmpty(queryAlias) ? tableName() : queryAlias;
    }

    private String buildEntitySource() {
        SqlDialect dialect = dialect();
        validateAlias(dialect);

        List<String> columns = Arrays.stream(entityClass.getMethods())
                .filter(SQLDataSet.methodFilter::test)
                .map((Method method) -> SQLDataSet.columnMapper.apply(method, queryAlias, dialect))
                .sorted()
                .collect(Collectors.toList());

        if( columns.isEmpty() )
            throw new IllegalStateException(
                    "No persistent columns found for " + entityClass.getName());

        StringBuilder sql = new StringBuilder("SELECT ");

        if( S.isNotNullOrEmpty(hint) )
            sql.append("/*").append(hint).append("*/ ");

        sql.append(String.join(",", columns))
           .append(" FROM ")
           .append(tableName());

        if( S.isNotNullOrEmpty(queryAlias) )
            sql.append(' ').append(queryAlias);

        return sql.toString();
    }

    /**
     * Aggregate compatibility is kept local to the facade.
     * SQLDataSet itself does not depend on this class.
     */
    private String buildAggregate() {
        validateAlias(dialect());

        String tableFor;
        if( S.isNotNullOrEmpty(aggrTable) )
            tableFor = aggrTable;
        else
            tableFor = tableName()
                    + (S.isNullOrEmpty(queryAlias) ? "" : " " + queryAlias);

        StringBuilder sql = new StringBuilder("SELECT ");
        int index = 0;

        for( Triplet<String,AggrFuncEnum,String> aggregate : aggrList ) {
            if( index++ > 0 )
                sql.append(',');

            if( aggregate.second != AggrFuncEnum.NONE )
                sql.append(aggregate.second);

            sql.append(" (")
               .append(aggregate.first)
               .append(") ");

            if( S.isNullOrEmpty(aggregate.third) )
                sql.append('"').append("A$").append(aggregate.first).append('"');
            else
                sql.append(aggregate.third);
        }

        sql.append(" FROM ").append(tableFor);

        if( predicateList != null && !predicateList.isEmpty() ) {
            sql.append(" WHERE null is null");
            for( String predicate : predicateList )
                sql.append(" AND (").append(predicate).append(')');
        }

        String result = sql.toString();
        clear();
        return result;
    }

    private String tableName() {
        Table table = (Table)entityClass.getAnnotation(Table.class);
        if( table == null || S.isNullOrEmpty(table.name()) )
            throw new IllegalStateException(
                    "@Table(name) is required for " + entityClass.getName());
        return table.name();
    }

    private SqlDialect dialect() {
        if( explicitDialect != null )
            return explicitDialect;
        if( taskContext == null )
            throw new IllegalStateException(
                    "TaskContext is required to build entity SQL");
        return taskContext.dialect();
    }

    private void validateAlias( SqlDialect dialect ) {
        if( S.isNullOrEmpty(queryAlias) )
            return;

        SqlScanner scanner;
        switch( dialect.type() ) {
            case ORACLE:
                scanner = new OracleSqlScanner();
                break;
            case POSTGRES:
                scanner = new PostgresSqlScanner();
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported SQL dialect: " + dialect);
        }

        List<SqlScanner.Token> tokens = scanner.scan(queryAlias);
        if( tokens.size() != 1
                || (tokens.get(0).getKind() != SqlScanner.Kind.WORD
                && tokens.get(0).getKind() != SqlScanner.Kind.QUOTED_IDENTIFIER) ) {
            throw new IllegalArgumentException(
                    "queryAlias must be one SQL identifier");
        }
    }
}
