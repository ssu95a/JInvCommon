package ru.inversion.db.session.xxi;

import ru.inversion.db.SqlExceptionInfo;
import ru.inversion.db.session.DatabaseLoginErrors;
import ru.inversion.utils.IExceptionInfo;
import ru.inversion.utils.S;
import ru.inversion.utils.dco.IDco;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class XXIConnectorException extends RuntimeException implements IExceptionInfo {

    private static final long serialVersionUID = 7628605587618228812L;

    public enum Reason {
        AUTH_FAILED,
        USER_NOT_FOUND,
        NO_XXI_PASSWORD,
        PASSWORD_EXPIRED,
        PASSWORD_CHANGE_REQUIRED,
        ACCOUNT_LOCKED,
        AUTH_METHOD_MISMATCH,
        DB_UNAVAILABLE,
        TECHNICAL_BREAK,
        ENCRYPTION_ERROR,
        DECRYPTION_ERROR,
        LICENCE_ERROR,
        INTERNAL_ERROR,
        INCORRECT_CONNECTION_STRING,
        PASSWORD_REQUIRED,
        TNS_ALIAS_UNRESOLVED,
        TNS_CONFIGURATION_ERROR,
        DATABASE_NOT_FOUND;

        public String getDefaultMessage() {
            switch (this) {
                case USER_NOT_FOUND:
                     return "User not found";
                case NO_XXI_PASSWORD:
                     return "No password set for user";
                case PASSWORD_EXPIRED:
                     return "Password expired";
                case PASSWORD_CHANGE_REQUIRED:
                     return "Password change required";
                case ACCOUNT_LOCKED:
                     return "Account locked";
                case AUTH_METHOD_MISMATCH:
                     return "Authentication method mismatch";
                case DB_UNAVAILABLE:
                     return "Database unavailable";
                case DATABASE_NOT_FOUND:
                     return "Database does not exist";
                case INCORRECT_CONNECTION_STRING:
                     return "Incorrect connection string";
                case TNS_ALIAS_UNRESOLVED:
                     return "Oracle connection alias could not be resolved";
                case TNS_CONFIGURATION_ERROR:
                     return "Oracle TNS configuration error. Check TNS_ADMIN and tnsnames.ora";
                case PASSWORD_REQUIRED:
                     return "Password is required";
                case TECHNICAL_BREAK:
                     return "Technical break";
                case ENCRYPTION_ERROR:
                     return "Encryption error";
                case DECRYPTION_ERROR:
                     return "Decryption error";
                case AUTH_FAILED:
                     return "Authentication failed";
                case LICENCE_ERROR:
                    return "Licence error";

                default: return "Internal error";
            }
        }
    }

    public enum Origin {
        LOCAL,
        REMOTE
    }

    private final Reason reason;

    /* происхождение */
    private final Origin origin;

    /** Диагностические детали  */
    private final Map<String, Object> details;

    private final SqlExceptionInfo sqlExceptionInfo;

    /** */
    public XXIConnectorException( Reason reason, String message) {
        this(reason, Origin.LOCAL, message, null, null);
    }

    /** */
    public XXIConnectorException( Reason reason, String message, Throwable cause ) {
        this( reason, Origin.LOCAL, message, cause, null );
    }

    /** */
    public XXIConnectorException( Reason reason,  Origin origin, String message, Throwable cause, Map<String, Object> details )
    {
        this(reason, origin, message, cause, details, null);
    }

    public XXIConnectorException(Reason reason, Origin origin, String message, Throwable cause,
                                 Map<String, Object> details, SqlExceptionInfo sqlExceptionInfo) {
        super(message, cause);
        this.reason = (reason == null) ? Reason.INTERNAL_ERROR : reason;
        this.origin = origin == null ? Origin.LOCAL : origin;
        this.details = details;
        this.sqlExceptionInfo = sqlExceptionInfo;
    }

    public Reason getReason() { return reason; }

    public Origin getOrigin() { return origin; }

    public boolean isRemote() {
        return origin == Origin.REMOTE;
    }

    public Map<String, Object> getDetails() { return details; }

    public SqlExceptionInfo getSqlExceptionInfo() { return sqlExceptionInfo; }

    /** */
    public String sqlState() {
        if (sqlExceptionInfo != null)
            return sqlExceptionInfo.getSqlState();
        Throwable c = getCause();
        if (c instanceof SQLException) return ((SQLException) c).getSQLState();
        return null;
    }
    /** */
    public Integer vendorCode() {
        if (sqlExceptionInfo != null)
            return sqlExceptionInfo.getVendorCode();
        Throwable c = getCause();
        if (c instanceof SQLException) return ((SQLException) c).getErrorCode();
        return null;
    }

    @Override
    public String getCategory() {
        return "Xxi-Connector";
    }

    @Override
    public String getDetailedMessage() {
        String message = reason == Reason.INCORRECT_CONNECTION_STRING ? connectionDiagnostic() : getMessage();
        Object requestId = details == null ? null : details.get("requestId");

        if( requestId == null )
            return message;

        String requestInfo = "Request ID: " + requestId;
        return S.isNullOrEmpty(message) ? requestInfo : message + "\n" + requestInfo;
    }

    private String connectionDiagnostic() {
        if (sqlExceptionInfo != null && S.isNotNullOrEmpty(sqlExceptionInfo.getMessage()))
            return sqlExceptionInfo.getMessage();

        Object remoteMessage = details == null ? null : details.get("remoteMessage");
        if (remoteMessage instanceof String)
            return (String) remoteMessage;

        Throwable cause = getCause();
        return cause == null ? null : cause.getLocalizedMessage();
    }
    // Factories

    public static XXIConnectorException of( Reason code, String safeMessage )
    {
        return new XXIConnectorException(code, safeMessage);
    }

    public static XXIConnectorException of(Reason code, String safeMessage, Throwable cause)
    {
        return new XXIConnectorException(code, safeMessage, cause);
    }

    public static XXIConnectorException of( Reason code, String safeMessage, Throwable cause, Map<String, Object> details )
    {
        return new XXIConnectorException(code, Origin.LOCAL,  safeMessage, cause, details);
    }

    public static XXIConnectorException fromSql(SQLException ex) {
        return fromSql(ex, null);
    }

    public static XXIConnectorException fromSql(SQLException ex, Map<String, Object> details) {

        return DatabaseLoginErrors.normalize(ex, details);
    }

    /** Для совместимости со старым стилем вызова */
    public static void throwOnSqlException(SQLException ex) {
        throw fromSql(ex, null);
    }

    public static void throwOnSqlException( Reason code, SQLException ex ) {
        if (ex == null)
            throw new XXIConnectorException(code, code.getDefaultMessage() );
        throw new XXIConnectorException(code, Origin.LOCAL, code.getDefaultMessage(), ex, null );
    }

    /** Создание из удаленного сообщения об ошибке */
    public static XXIConnectorException fromRemote ( int httpStatus, String requestId, String errorCode, String message, IDco responseDco )
    {
        if( S.isNullOrEmpty(errorCode) && responseDco != null) {
            errorCode = responseDco.single("/authResponse/errorCode")
                    .map(IDco::<String>value)
                    .orElse(null);
        }

        if( S.isNullOrEmpty(message) && responseDco != null) {
            message = responseDco.single("/authResponse/message")
                .map(IDco::<String>value)
                    .orElse(null);
        }

        final Reason reason = RemoteClassifier.classify(errorCode, httpStatus);
        if( S.isNullOrEmpty(message) || reason == Reason.INTERNAL_ERROR)
            message = reason.getDefaultMessage();

        final Map<String, Object> details = new LinkedHashMap<>();
        details.put("httpStatus", httpStatus);

        if (!S.isNullOrEmpty(requestId))
            details.put("requestId", requestId);

        if (!S.isNullOrEmpty(errorCode))
            details.put("remoteErrorCode", errorCode );

        String safeMessage = message;

        if (reason == Reason.INCORRECT_CONNECTION_STRING) {
            safeMessage = reason.getDefaultMessage();
            if (!safeMessage.equals(message))
                details.put("remoteMessage", message);
        }

        return new XXIConnectorException( reason, Origin.REMOTE, safeMessage, null, details );
    }

    private static final class RemoteClassifier {

        static Reason classify(String errorCode, int httpStatus) {

            if( !S.isNullOrEmpty(errorCode) )
            {
                switch( errorCode.trim().toLowerCase(Locale.ROOT) )
                {
                    // Auth
                    case "auth.denied":
                    case "auth.credentials.invalid":
                        return Reason.AUTH_FAILED;

                    case "auth.password.expired":
                        return Reason.PASSWORD_EXPIRED;

                    case "auth.password.change.required":
                        return Reason.PASSWORD_CHANGE_REQUIRED;

                    case "auth.method.mismatch":
                        return Reason.AUTH_METHOD_MISMATCH;

                    // Target / DB
                    case "target.db.unavailable":
                    case "target.runtime.unavailable":
                    case "target.pool.unavailable":
                    case "target.pool.timeout":
                    case "internal.service.unavailable":
                    case "state.not.ready":
                        return Reason.DB_UNAVAILABLE;

                    case "target.db.technical.break":
                        return Reason.TECHNICAL_BREAK;
                    case "target.db.tns.alias.unresolved":
                        return Reason.INCORRECT_CONNECTION_STRING;
                    case "target.db.tns.configuration.error":
                        return Reason.TNS_CONFIGURATION_ERROR;
                    // Crypto
                    case "crypto.encryption.error":
                        return Reason.ENCRYPTION_ERROR;

                    case "crypto.decryption.error":
                        return Reason.DECRYPTION_ERROR;

                    // Любой известный/неизвестный protocol/config/internal error
                    default:
                        return Reason.INTERNAL_ERROR;
                }
            }
            // Только если сервер вообще не прислал errorCode.
            return classifyHttp(httpStatus);
        }

        private static Reason classifyHttp(int httpStatus) {
            switch (httpStatus) {
                case 401:
                case 403:
                    return Reason.AUTH_FAILED;
                case 503:
                    return Reason.DB_UNAVAILABLE;
                default:
                    return Reason.INTERNAL_ERROR;
            }
        }
    }
}
