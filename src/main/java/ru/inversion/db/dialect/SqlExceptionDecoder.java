package ru.inversion.db.dialect;

import ru.inversion.db.SqlExceptionInfo;
import ru.inversion.db.SqlExceptionInfo.Kind;
import ru.inversion.db.dialect.SqlDialect.DialectId;

import java.sql.BatchUpdateException;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static ru.inversion.db.SqlExceptionInfo.Kind.*;

/** Общий обход и выбор JDBC-диагностики для декодеров диалектов. */
public final class SqlExceptionDecoder {

    private SqlExceptionDecoder() { }

    public static List<Throwable> chain(Throwable error) {
        List<Throwable> chain = new ArrayList<>();
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
        Deque<Throwable> pending = new ArrayDeque<>();

        if (error != null)
            pending.add(error);

        while (!pending.isEmpty()) {
            Throwable current = pending.remove();
            if (!visited.add(current))
                continue;

            chain.add(current);

            Throwable cause = current.getCause();
            if (cause != null)
                pending.add(cause);

            if (current instanceof SQLException) {
                SQLException next = ((SQLException) current).getNextException();
                if (next != null)
                    pending.add(next);
            }
        }

        return chain;
    }

    static SqlExceptionInfo decode(DialectId dialect, Throwable error,
                                   Function<SQLException, Kind> classify,
                                   Function<SQLException, String> objectName) {
        List<Throwable> errors = chain(error);
        SqlExceptionInfo specific = findRecognized(dialect, errors, classify, objectName);
        return specific != null ? specific : decodeCommon(dialect, errors, objectName);
    }

    static SqlExceptionInfo decodeCommon(DialectId dialect, Throwable error) {
        return decodeCommon(dialect, chain(error), sql -> null);
    }

    /** Совместимость со старыми вызовами входа без указания диалекта. */
    public static SqlExceptionInfo decodeWithoutDialect(List<Throwable> errors) {
        SqlExceptionInfo oracle = findRecognized(DialectId.ORACLE, errors,
                OracleDialect::classifyException, sql -> null);
        if (oracle != null)
            return oracle;

        SqlExceptionInfo postgres = findRecognized(DialectId.POSTGRES, errors,
                PostgresDialect::classifyException, PostgresDialect::dbObjectName);
        if (postgres != null)
            return postgres;

        return decodeCommon(null, errors, PostgresDialect::dbObjectName);
    }

    private static SqlExceptionInfo findRecognized(DialectId dialect, List<Throwable> errors,
                                                  Function<SQLException, Kind> classify,
                                                  Function<SQLException, String> objectName) {
        SqlExceptionInfo batchMatch = null;
        for (Throwable error : errors) {
            if (error instanceof SQLException) {
                SQLException sql = (SQLException) error;
                Kind kind = classify.apply(sql);
                if (kind == null)
                    continue;
                if (!(sql instanceof BatchUpdateException))
                    return SqlExceptionInfo.of(dialect, kind, sql, objectName.apply(sql));
                if (batchMatch == null)
                    batchMatch = SqlExceptionInfo.of(dialect, kind, sql, objectName.apply(sql));
            }
        }
        return batchMatch;
    }

    private static SqlExceptionInfo decodeCommon(DialectId dialect, List<Throwable> errors,
                                                 Function<SQLException, String> objectName) {
        SqlExceptionInfo recognized = findRecognized(dialect, errors, SqlExceptionDecoder::classifyCommon, objectName);
        if (recognized != null)
            return recognized;

        return findRecognized(dialect, errors, sql -> UNKNOWN, objectName);
    }

    private static Kind classifyCommon(SQLException sql) {
        String state = sql.getSQLState();
        if ("28000".equals(state))
            return AUTHENTICATION_FAILED;
        if (state != null && state.startsWith("08"))
            return CONNECTION_FAILURE;
        return null;
    }
}
