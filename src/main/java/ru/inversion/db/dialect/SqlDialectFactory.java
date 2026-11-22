package ru.inversion.db.dialect;

import ru.inversion.tc.TaskContext;

/** */
public final class SqlDialectFactory {

    public static final SqlDialect ORACLE = new OracleDialect();

    public static final SqlDialect POSTGRES = new PostgresDialect();

    private SqlDialectFactory() {}

    /** Диалект прямого JDBC URL, либо null для неизвестного формата. */
    public static SqlDialect fromJdbcUrl(String jdbcUrl) {
        if (jdbcUrl == null)
            return null;
        if (jdbcUrl.startsWith("jdbc:oracle:"))
            return ORACLE;
        if (jdbcUrl.startsWith("jdbc:postgresql:"))
            return POSTGRES;
        return null;
    }

    public static SqlDialect from(TaskContext tc) {

        if( tc.isOracle() ) {
            return ORACLE;
        }
        if (tc.isPostgreSql()) {
            return POSTGRES;
        }
        throw new IllegalStateException(
                "Unsupported database dialect in TaskContext: " + tc
        );
    }
}
