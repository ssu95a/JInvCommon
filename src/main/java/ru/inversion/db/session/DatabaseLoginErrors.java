package ru.inversion.db.session;

import ru.inversion.db.SqlExceptionInfo;
import ru.inversion.db.dialect.SqlDialect;
import ru.inversion.db.dialect.SqlExceptionDecoder;
import ru.inversion.db.session.xxi.XXIConnectorException;
import ru.inversion.db.session.xxi.XXIConnectorException.Origin;
import ru.inversion.db.session.xxi.XXIConnectorException.Reason;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.sql.SQLTimeoutException;
import java.util.List;
import java.util.Map;

import static ru.inversion.db.session.xxi.XXIConnectorException.Reason.*;

/** Преобразование SQL-диагностики и прикладных сигналов в ошибку входа. */
public final class DatabaseLoginErrors {

    private DatabaseLoginErrors() { }

    public static XXIConnectorException normalize(Throwable failure) {
        return normalize(failure, null);
    }

    public static XXIConnectorException normalize(Throwable failure, Map<String, Object> details) {
        return normalize(null, failure, details);
    }

    public static XXIConnectorException normalize(SqlDialect dialect, Throwable failure, Map<String, Object> details) {
        List<Throwable> errors = SqlExceptionDecoder.chain(failure);
        boolean connectionFailure = false;

        for (Throwable current : errors) {
            if (current instanceof XXIConnectorException) {
                XXIConnectorException loginFailure = (XXIConnectorException) current;
                String message = loginFailure.getReason().getDefaultMessage();
                if (current == failure && message.equals(current.getMessage()))
                    return loginFailure;

                return new XXIConnectorException(loginFailure.getReason(), loginFailure.getOrigin(),
                        message, failure, loginFailure.getDetails() == null ? details : loginFailure.getDetails(),
                        loginFailure.getSqlExceptionInfo());
            }

            connectionFailure |= current instanceof ConnectException
                    || current instanceof SocketTimeoutException
                    || current instanceof UnknownHostException
                    || current instanceof SQLTimeoutException;
        }

        SqlExceptionInfo info = dialect != null ? dialect.decodeException(failure)
                : SqlExceptionDecoder.decodeWithoutDialect(errors);
        Reason reason = reasonFor(info);
        if (reason == INTERNAL_ERROR && connectionFailure)
            reason = DB_UNAVAILABLE;

        return new XXIConnectorException(reason, Origin.LOCAL, reason.getDefaultMessage(), failure, details, info);
    }

    private static Reason reasonFor(SqlExceptionInfo info) {
        if (info == null)
            return INTERNAL_ERROR;

        switch (info.getKind()) {
            case AUTHENTICATION_FAILED:
                return AUTH_FAILED;
            case USER_NOT_FOUND:
                return USER_NOT_FOUND;
            case PASSWORD_REQUIRED:
                return PASSWORD_REQUIRED;
            case PASSWORD_EXPIRED:
                return PASSWORD_EXPIRED;
            case PASSWORD_CHANGE_REQUIRED:
                return PASSWORD_CHANGE_REQUIRED;
            case ACCOUNT_LOCKED:
                return ACCOUNT_LOCKED;
            case AUTH_METHOD_MISMATCH:
                return AUTH_METHOD_MISMATCH;
            case CONNECTION_FAILURE:
                return DB_UNAVAILABLE;
            case INVALID_CONNECTION_STRING:
                return INCORRECT_CONNECTION_STRING;
            default:
                return INTERNAL_ERROR;
        }
    }
}
