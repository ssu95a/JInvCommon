package ru.inversion.utils.sparser;

import ru.inversion.db.dialect.SqlDialect;
import ru.inversion.utils.sparser.lexer.OracleSqlScanner;
import ru.inversion.utils.sparser.lexer.PostgresSqlScanner;
import ru.inversion.utils.sparser.lexer.SqlScanner;

import java.util.Objects;

/** Единая точка выбора SQL-сканнера по диалекту. */
public final class SqlScannerFactory {

    private SqlScannerFactory() {
    }

    public static SqlScanner create(SqlDialect dialect) {
        switch (Objects.requireNonNull(dialect, "dialect").type()) {
            case ORACLE:
                return new OracleSqlScanner();
            case POSTGRES:
                return new PostgresSqlScanner();
            default:
                throw new IllegalArgumentException("Unsupported SQL dialect: " + dialect);
        }
    }
}
