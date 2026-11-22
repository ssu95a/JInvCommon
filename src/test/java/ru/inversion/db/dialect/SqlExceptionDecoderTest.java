package ru.inversion.db.dialect;

import oracle.net.ns.NetException;
import org.junit.Test;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import ru.inversion.db.SqlExceptionInfo;

import java.sql.BatchUpdateException;
import java.sql.SQLException;
import java.sql.SQLRecoverableException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static ru.inversion.db.SqlExceptionInfo.Kind.*;

public class SqlExceptionDecoderTest {

    @Test
    public void codesAreInterpretedByTheirOwnDialect() {
        SQLException oracle = new SQLException("password expired", "72000", 28001);
        SQLException postgres = new SQLException("account locked", "28P22");

        assertEquals(PASSWORD_EXPIRED, SqlDialectFactory.ORACLE.decodeException(oracle).getKind());
        assertEquals(UNKNOWN, SqlDialectFactory.POSTGRES.decodeException(oracle).getKind());
        assertEquals(ACCOUNT_LOCKED, SqlDialectFactory.POSTGRES.decodeException(postgres).getKind());
        assertEquals(UNKNOWN, SqlDialectFactory.ORACLE.decodeException(postgres).getKind());
    }

    @Test
    public void oracleDistinguishesNamingFailuresFromConnectionFailures() {
        assertEquals(CONNECTION_ALIAS_UNRESOLVED,
                SqlDialectFactory.ORACLE.decodeException(new SQLException("unknown alias", "66000", 12154)).getKind());
        assertEquals(CONNECTION_ALIAS_UNRESOLVED,
                SqlDialectFactory.ORACLE.decodeException(oracleNetworkError(NetException.NAMELOOKUP_FAILED)).getKind());
        assertEquals(NAMING_CONFIGURATION_ERROR,
                SqlDialectFactory.ORACLE.decodeException(new SQLException("bad connect descriptor", "66000", 12162)).getKind());
        assertEquals(NAMING_CONFIGURATION_ERROR,
                SqlDialectFactory.ORACLE.decodeException(oracleNetworkError(NetException.NAMELOOKUP_FILE_ERROR)).getKind());
        assertEquals(CONNECTION_FAILURE,
                SqlDialectFactory.ORACLE.decodeException(new SQLRecoverableException("network error", "08006", 17002)).getKind());
    }

    @Test
    public void decoderFollowsBatchNextExceptionAndStopsAtCycles() {
        BatchUpdateException batch = new BatchUpdateException("batch wrapper", "08001", 0, new int[0]);
        SQLException rejected = new SQLException("expired password", "72000", 28001);
        batch.setNextException(rejected);

        IllegalStateException wrapper = new IllegalStateException("outer wrapper", batch);
        rejected.initCause(wrapper);

        SqlExceptionInfo decoded = SqlDialectFactory.ORACLE.decodeException(wrapper);
        assertEquals(PASSWORD_EXPIRED, decoded.getKind());
        assertEquals("72000", decoded.getSqlState());
        assertEquals(28001, decoded.getVendorCode());
        assertEquals("expired password", decoded.getMessage());
    }

    @Test
    public void postgresCombinesTheStructuredObjectName() {
        PSQLException error = new PSQLException(new ServerErrorMessage(
                "SERROR\000C23505\000Mduplicate key\000ssales\000tpayment\000npayment_pk\000"));
        BatchUpdateException batch = new BatchUpdateException("batch wrapper", "23505", 0, new int[0]);
        batch.setNextException(error);

        SqlExceptionInfo decoded = SqlDialectFactory.POSTGRES.decodeException(batch);
        assertEquals(UNKNOWN, decoded.getKind());
        assertEquals("23505", decoded.getSqlState());
        assertEquals("sales.payment.payment_pk", decoded.getDbObjectName());
    }

    @Test
    public void nonSqlFailuresHaveNoSqlDiagnostics() {
        assertNull(SqlDialectFactory.ORACLE.decodeException(new IllegalStateException("no JDBC cause")));
        assertNull(SqlDialectFactory.POSTGRES.decodeException(null));
    }

    private static SQLException oracleNetworkError(int code) {
        return new SQLRecoverableException("IO Error", "08006", 17002, new NetException(code));
    }
}
