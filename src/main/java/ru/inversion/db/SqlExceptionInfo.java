package ru.inversion.db;

import ru.inversion.db.dialect.SqlDialect.DialectId;

import java.io.Serializable;
import java.sql.SQLException;
import java.util.Objects;

/** Диагностика одного выбранного исключения JDBC. */
public final class SqlExceptionInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    public enum Kind {
        AUTHENTICATION_FAILED,
        USER_NOT_FOUND,
        PASSWORD_REQUIRED,
        PASSWORD_EXPIRED,
        PASSWORD_CHANGE_REQUIRED,
        ACCOUNT_LOCKED,
        AUTH_METHOD_MISMATCH,
        CONNECTION_FAILURE,
        INVALID_CONNECTION_STRING,
        UNKNOWN,
        CONNECTION_ALIAS_UNRESOLVED,
        NAMING_CONFIGURATION_ERROR
    }

    private final DialectId dialect;
    private final Kind kind;
    private final String sqlState;
    private final int vendorCode;
    private final String message;
    private final String dbObjectName;

    private SqlExceptionInfo(DialectId dialect, Kind kind, SQLException matched, String dbObjectName) {
        this.dialect = dialect;
        this.kind = Objects.requireNonNull(kind, "kind");
        this.sqlState = matched.getSQLState();
        this.vendorCode = matched.getErrorCode();
        this.message = matched.getMessage();
        this.dbObjectName = dbObjectName;
    }

    public static SqlExceptionInfo of(DialectId dialect, Kind kind, SQLException matched, String dbObjectName) {
        return new SqlExceptionInfo(dialect, kind, matched, dbObjectName);
    }

    /** Может быть null, когда вызывающий код не знает диалект. */
    public DialectId getDialect() { return dialect; }

    public Kind getKind() { return kind; }

    public String getSqlState() { return sqlState; }

    public int getVendorCode() { return vendorCode; }

    public String getMessage() { return message; }

    public String getDbObjectName() { return dbObjectName; }
}
