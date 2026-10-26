package ru.inversion.dataset.impl;

import ru.inversion.db.dialect.SqlDialect;
import ru.inversion.db.dialect.SqlDialectFactory;
import ru.inversion.utils.S;

import java.util.Objects;

/**
 * Compatibility facade for the former SQLSelectBuilder.
 *
 * Ordinary SELECT construction is delegated to NextSelectBuilder.
 * The former implementation and parser hierarchy are not restored.
 */
public class SQLSelectBuilder extends SelectBuilder {

    private String select;
    private SqlDialect dialect;

    public SQLSelectBuilder() {
        this.queryAlias = "qrslt";
    }

    public SQLSelectBuilder( String select ) {
        this.select = select;
        this.queryAlias = "qrslt";
    }

    /** Optional explicit dialect for dialect-specific SQL. */
    public SQLSelectBuilder( String select, SqlDialect dialect ) {
        this.select = select;
        this.dialect = Objects.requireNonNull(dialect, "dialect");
        this.queryAlias = "qrslt";
    }

    /** Optional explicit dialect for dialect-specific SQL. */
    public SQLSelectBuilder dialect( SqlDialect dialect ) {
        this.dialect = Objects.requireNonNull(dialect, "dialect");
        return this;
    }

    public SQLSelectBuilder select( String select ) {
        this.select = select;
        return this;
    }

    @Override
    public void clear() {
        super.clear();
        select = null;
    }

    @Override
    public String build() {
        if( aggrList != null && !aggrList.isEmpty() )
            return buildAggr();
        return buildSelect();
    }

    public String buildAggr() {
        String source = Objects.requireNonNull(select, "select");
        String alias = getFromObj();

        String result = prepareAggrExpression(
                S.isNullOrEmpty(aggrTable)
                        ? "( " + source + ") " + alias
                        : aggrTable
        );

        clear();
        return result;
    }

    public String buildSelect() {
        if( additionalTags )
            throw new UnsupportedOperationException(
                    "additionalTags is not supported by the new SELECT pipeline");

        String source = Objects.requireNonNull(select, "select");
        String alias = getFromObj();

        StringBuilder outer = new StringBuilder("SELECT ");
        if( S.isNotNullOrEmpty(hint) )
            outer.append("/*").append(hint).append("*/ ");

        outer.append(alias)
             .append(".* FROM (\n")
             .append(source)
             .append("\n) ")
             .append(alias);

        String result = buildWithResolvedDialect(outer.toString());
        clear();
        return result;
    }

    @Override
    public String getFromObj() {
        return S.isNullOrEmpty(queryAlias) ? "qrslt" : queryAlias;
    }

    private String buildWithResolvedDialect( String source ) {
        if( dialect != null )
            return build(source, dialect);

        String oracle = null;
        String postgres = null;
        RuntimeException oracleError = null;
        RuntimeException postgresError = null;

        try {
            oracle = build(source, SqlDialectFactory.ORACLE);
        }
        catch( RuntimeException ex ) {
            oracleError = ex;
        }

        try {
            postgres = build(source, SqlDialectFactory.POSTGRES);
        }
        catch( RuntimeException ex ) {
            postgresError = ex;
        }

        if( oracle != null && postgres != null ) {
            if( oracle.equals(postgres) )
                return oracle;

            throw new IllegalStateException(
                    "SQL dialect is ambiguous for SQLSelectBuilder; call dialect(SqlDialect)");
        }

        if( oracle != null )
            return oracle;

        if( postgres != null )
            return postgres;

        IllegalArgumentException failure = new IllegalArgumentException(
                "SQL is unsupported by both Oracle and PostgreSQL compatibility scanners");
        if( oracleError != null )
            failure.addSuppressed(oracleError);
        if( postgresError != null )
            failure.addSuppressed(postgresError);
        throw failure;
    }

    private String build( String source, SqlDialect actualDialect ) {
        NextSelectBuilder builder = new NextSelectBuilder(source, actualDialect);

        if( predicateList != null )
            for( String predicate : predicateList )
                builder.addPredicate(predicate);

        builder.orderBy(orderBy);
        builder.mark(mark);

        return builder.buildSql();
    }
}
