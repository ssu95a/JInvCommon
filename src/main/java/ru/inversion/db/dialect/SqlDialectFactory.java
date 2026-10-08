package ru.inversion.db.dialect;

import ru.inversion.tc.TaskContext;

/** */
public final class SqlDialectFactory {

    public static final SqlDialect ORACLE = new OracleDialect();

    public static final SqlDialect POSTGRES = new PostgresDialect();

    private SqlDialectFactory() {}

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
