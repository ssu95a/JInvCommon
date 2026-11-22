package ru.inversion.db.session;

import ru.inversion.db.session.xxi.XXIConnectorException;
import ru.inversion.db.session.xxi.XXIConnectorException.Origin;
import ru.inversion.db.session.xxi.XXIConnectorException.Reason;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static ru.inversion.db.session.xxi.XXIConnectorException.Reason.*;

/** Единая классификация ошибок входа в БД. */
public final class DatabaseLoginErrors {

    private DatabaseLoginErrors() { }

    public static XXIConnectorException normalize(Throwable failure) {
        return normalize(failure, null);
    }

    public static XXIConnectorException normalize(Throwable failure, Map<String, Object> details) {
        List<SQLException> sqlFailures = new ArrayList<>();
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
        Deque<Throwable> pending = new ArrayDeque<>();
        if (failure != null)
            pending.add(failure);

        boolean connectionFailure = false;
        while (!pending.isEmpty()) {
            Throwable current = pending.remove();
            if (!visited.add(current))
                continue;

            if (current instanceof XXIConnectorException) {
                XXIConnectorException loginFailure = (XXIConnectorException) current;
                String message = loginFailure.getReason().getDefaultMessage();
                if (current == failure && message.equals(current.getMessage()))
                    return loginFailure;

                return new XXIConnectorException(loginFailure.getReason(), loginFailure.getOrigin(),
                        message, failure, loginFailure.getDetails() == null ? details : loginFailure.getDetails());
            }

            if (current.getCause() != null)
                pending.add(current.getCause());

            if (current instanceof SQLException) {
                SQLException sqlFailure = (SQLException) current;
                sqlFailures.add(sqlFailure);
                if (sqlFailure.getNextException() != null)
                    pending.add(sqlFailure.getNextException());
            }

            connectionFailure |= current instanceof ConnectException
                    || current instanceof SocketTimeoutException
                    || current instanceof UnknownHostException
                    || current instanceof SQLTimeoutException;
        }

        Reason reason = classifySql(sqlFailures);
        if (reason == null)
            reason = connectionFailure ? DB_UNAVAILABLE : INTERNAL_ERROR;

        return new XXIConnectorException(reason, Origin.LOCAL, reason.getDefaultMessage(), failure, details);
    }

    private static Reason classifySql(List<SQLException> failures) {
        for (SQLException failure : failures) {
            switch (failure.getErrorCode()) {
                case 1005:
                    return PASSWORD_REQUIRED;
                case 1017:
                    return AUTH_FAILED;
                case 28000:
                    return ACCOUNT_LOCKED;
                case 28001:
                    return PASSWORD_EXPIRED;
                case 17067:
                    return INCORRECT_CONNECTION_STRING;
                case 1033:
                case 1034:
                case 1089:
                case 1090:
                case 12154:
                case 12170:
                case 12514:
                case 12541:
                case 17002:
                    return DB_UNAVAILABLE;
                default:
                    break;
            }
        }

        for (SQLException failure : failures) {
            String state = failure.getSQLState();
            if (state == null)
                continue;

            switch (state) {
                // Дополнительные коды, которые уже использует PostgreSQL-вход в ЦАБС.
                case "28P21":
                    return PASSWORD_EXPIRED;
                case "28P22":
                    return ACCOUNT_LOCKED;
                case "28P25":
                    return PASSWORD_CHANGE_REQUIRED;
                case "28P01":
                    return classifyInvalidPassword(failure.getMessage());
                default:
                    break;
            }
        }

        for (SQLException failure : failures) {
            String state = failure.getSQLState();
            if (state == null)
                continue;

            if ("28000".equals(state))
                return AUTH_FAILED;
            if (state.startsWith("08") || "57P01".equals(state)
                    || "57P02".equals(state) || "57P03".equals(state) || "53300".equals(state))
                return DB_UNAVAILABLE;
        }
        return null;
    }

    private static Reason classifyInvalidPassword(String message) {
        if (message == null)
            return AUTH_FAILED;

        // Штатный 28P01 и старые варианты ответа серверной аутентификации ЦАБС.
        String lower = message.toLowerCase(Locale.ROOT);
        if (lower.contains("password expired") || lower.contains("password validity"))
            return PASSWORD_EXPIRED;
        if (lower.contains("password change is required"))
            return PASSWORD_CHANGE_REQUIRED;
        if (lower.contains("authentication") && (lower.contains("scram-sha-256") || lower.contains("md5")))
            return AUTH_METHOD_MISMATCH;
        if (lower.contains("does not exist") || lower.contains("unknown user")
                || (lower.contains("user") && lower.contains("not exist")))
            return USER_NOT_FOUND;

        return AUTH_FAILED;
    }
}
