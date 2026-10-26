package ru.inversion.dataset.impl;

import ru.inversion.dataset.SQLDataSet;
import ru.inversion.db.dialect.SqlDialect;
import ru.inversion.tc.TaskContext;
import ru.inversion.utils.S;
import ru.inversion.utils.sparser.lexer.OracleSqlScanner;
import ru.inversion.utils.sparser.lexer.PostgresSqlScanner;
import ru.inversion.utils.sparser.lexer.SqlScanner;

import javax.persistence.Table;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Compatibility facade for the former EntitySelectBuilder.
 *
 * Ordinary SELECT construction is delegated to NextSelectBuilder.
 * SQLDataSet itself does not depend on this class.
 */
public class EntitySelectBuilder extends SelectBuilder {

    private final Class entityClass;
    private final TaskContext taskContext;
    private final SqlDialect explicitDialect;

    public EntitySelectBuilder( Class entityClass ) {
        this.entityClass = Objects.requireNonNull(entityClass, "entityClass is null");
        this.taskContext = null;
        this.explicitDialect = null;
    }

    public EntitySelectBuilder( Class entityClass, TaskContext tc ) {
        this.entityClass = Objects.requireNonNull(entityClass, "entityClass is null");
        this.taskContext = Objects.requireNonNull(tc, "TaskContext is null");
        this.explicitDialect = null;
    }

    /** Internal constructor for DB-independent package tests. */
    EntitySelectBuilder( Class entityClass, SqlDialect dialect ) {
        this.entityClass = Objects.requireNonNull(entityClass, "entityClass is null");
        this.taskContext = null;
        this.explicitDialect = Objects.requireNonNull(dialect, "dialect is null");
    }

    @Override
    public String build() {
        if( additionalTags )
            throw new UnsupportedOperationException(
                    "additionalTags is not supported by the new SELECT pipeline");

        if( aggrList != null && !aggrList.isEmpty() )
            return buildAggregate();

        NextSelectBuilder builder =
                new NextSelectBuilder(buildEntitySource(), dialect());

        if( predicateList != null )
            for( String predicate : predicateList )
                builder.addPredicate(predicate);

        builder.orderBy(orderBy);
        builder.mark(mark);

        String result = builder.buildSql();
        clear();
        return result;
    }

    @Override
    public String getFromObj() {
        return S.isNullOrEmpty(queryAlias) ? tableName() : queryAlias;
    }

    private String buildEntitySource() {
        SqlDialect dialect = dialect();
        validateAlias(dialect);

        List<String> columns = Arrays.stream(entityClass.getMethods())
                .filter(SQLDataSet.methodFilter::test)
                .map((Method method) ->
                        SQLDataSet.columnMapper.apply(method, queryAlias, dialect))
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

    private String buildAggregate() {
        validateAlias(dialect());

        String tableFor;
        if( S.isNotNullOrEmpty(aggrTable) )
            tableFor = aggrTable;
        else
            tableFor = tableName()
                    + (S.isNullOrEmpty(queryAlias) ? "" : " " + queryAlias);

        String result = prepareAggrExpression(tableFor);
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
