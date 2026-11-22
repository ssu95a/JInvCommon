package ru.inversion.db.session.xxi;

import org.junit.Test;
import ru.inversion.db.SqlExceptionInfo;
import ru.inversion.db.dialect.SqlDialectFactory;
import ru.inversion.db.session.DataSourceCfg;
import ru.inversion.db.session.DatabaseLoginErrors;
import ru.inversion.db.session.SessionEnvironment;
import ru.inversion.utils.dco.Dco;
import ru.inversion.utils.dco.IDco;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static ru.inversion.db.session.xxi.XXIConnectorException.Reason.*;

public class DatabaseLoginErrorsTest {

    @Test
    public void oracleAndPostgresReturnTheSameAuthenticationFailure() {
        XXIConnectorException oracle = DatabaseLoginErrors.normalize(new SQLException("ORA-01017", "72000", 1017));
        XXIConnectorException postgres = DatabaseLoginErrors.normalize(new SQLException("invalid password", "28P01"));
        assertEquals(AUTH_FAILED, oracle.getReason());
        assertEquals(oracle.getReason(), postgres.getReason());
        assertEquals(oracle.getMessage(), postgres.getMessage());
    }

    @Test
    public void classifiesDetailedSQLExceptionAndPreservesTheWrapper() {
        SQLException driver = new SQLException("driver wrapper", "08001");
        driver.setNextException(new SQLException("expired password", "28P21"));
        IllegalStateException wrapper = new IllegalStateException("caller wrapper", driver);

        XXIConnectorException failure = DatabaseLoginErrors.normalize(SqlDialectFactory.POSTGRES, wrapper, null);
        assertEquals(PASSWORD_EXPIRED, failure.getReason());
        assertEquals("28P21", failure.sqlState());
        assertEquals(Integer.valueOf(0), failure.vendorCode());
        assertEquals(SqlExceptionInfo.Kind.PASSWORD_EXPIRED, failure.getSqlExceptionInfo().getKind());
        assertEquals("expired password", failure.getSqlExceptionInfo().getMessage());
        assertSame(wrapper, failure.getCause());
    }

    @Test
    public void distinguishesUnavailableDatabaseInvalidUrlAndUnknownError() {
        assertEquals(DB_UNAVAILABLE, DatabaseLoginErrors.normalize(new SQLException("cannot connect now", "57P03")).getReason());
        assertEquals(DB_UNAVAILABLE, DatabaseLoginErrors.normalize(new SQLException("ORA-01033", "72000", 1033)).getReason());
        assertEquals(INCORRECT_CONNECTION_STRING, DatabaseLoginErrors.normalize(new SQLException("invalid Oracle URL", "99999", 17067)).getReason());
        assertEquals(PASSWORD_REQUIRED, DatabaseLoginErrors.normalize(new SQLException("ORA-01005", "72000", 1005)).getReason());
        assertEquals(INTERNAL_ERROR, DatabaseLoginErrors.normalize(new SQLException("technical break")).getReason());
    }

    @Test
    public void preservesUnresolvedOracleAliasAsALoginReason() {
        SQLException driver = new SQLException("TNS could not resolve the connect identifier", "66000", 12154);
        XXIConnectorException failure = DatabaseLoginErrors.normalize(SqlDialectFactory.ORACLE, driver, null);

        assertEquals(TNS_ALIAS_UNRESOLVED, failure.getReason());
        assertEquals(Integer.valueOf(12154), failure.vendorCode());
        assertSame(driver, failure.getCause());
    }

    @Test
    public void preservesExplicitRemoteMaintenanceSignalPastJdbcWrapper() {
        XXIConnectorException remote = XXIConnectorException.fromRemote(503, "request-id", "target.db.technical.break", "Maintenance scheduled", null);
        SQLException wrapper = new SQLException("connection failed", "08001", remote);

        XXIConnectorException failure = DatabaseLoginErrors.normalize(wrapper);
        assertEquals(TECHNICAL_BREAK, failure.getReason());
        assertEquals(XXIConnectorException.Origin.REMOTE, failure.getOrigin());
        assertEquals("request-id", failure.getDetails().get("requestId"));
        assertSame(wrapper, failure.getCause());
    }

    @Test
    public void readsOracleNamingErrorsFromAuthenticationResponse() {
        assertRemoteNamingReason("target.db.tns.alias.unresolved", TNS_ALIAS_UNRESOLVED);
        assertRemoteNamingReason("target.db.tns.configuration.error", TNS_CONFIGURATION_ERROR);
    }

    private static void assertRemoteNamingReason(String code, XXIConnectorException.Reason reason) {
        IDco response = Dco.parseXml("<authResponse><errorCode>" + code + "</errorCode></authResponse>");

        XXIConnectorException failure = XXIConnectorException.fromRemote(503, "request-id", null, null, response);
        assertEquals(reason, failure.getReason());
        assertEquals(XXIConnectorException.Origin.REMOTE, failure.getOrigin());
        assertEquals("request-id", failure.getDetails().get("requestId"));
        assertEquals(code, failure.getDetails().get("remoteErrorCode"));
    }

    @Test
    public void bothPseudoLoginEntryPointsStopAtTheAnnouncedMaintenanceMarker() throws Exception {
        String url = "jdbc:login-errors-test";
        Driver driver = new TestDriver(url, () -> maintenanceConnection(true));
        DriverManager.registerDriver(driver);
        try {
            XXIConnectorException oracle = assertThrows(XXIConnectorException.class,
                    () -> XxiConnector.login(url, "user", "password", new Properties(), null));
            XXIConnectorException postgres = assertThrows(XXIConnectorException.class,
                    () -> PGPseudoConnector.login(url, "user", "password", new Properties(), null));
            assertEquals(TECHNICAL_BREAK, oracle.getReason());
            assertEquals(oracle.getReason(), postgres.getReason());
            assertEquals(oracle.getMessage(), postgres.getMessage());
        }
        finally {
            DriverManager.deregisterDriver(driver);
        }
    }

    @Test
    public void classifiesFailureDuringSessionInitialization() throws Exception {
        String url = "jdbc:login-errors-verification-test";
        SQLException rejected = new SQLException("invalid password", "28P01");
        Driver driver = new TestDriver(url, () -> { throw rejected; });
        DriverManager.registerDriver(driver);
        try {
            Properties properties = new Properties();
            properties.setProperty("check_connection", "true");
            DataSourceCfg config = new DataSourceCfg("user", "password", url, url, null,
                    false, properties, null, null);

            XXIConnectorException failure = assertThrows(XXIConnectorException.class,
                    () -> SessionEnvironment.initialize(config));
            assertEquals(AUTH_FAILED, failure.getReason());
            assertSame(rejected, failure.getCause());
        }
        finally {
            DriverManager.deregisterDriver(driver);
        }
    }

    @Test
    public void absentMarkerAllowsLoginToContinue() throws SQLException {
        ConnectorBase.check4XxiUpgrade(maintenanceConnection(false), new Properties());
    }

    private static Connection maintenanceConnection(boolean markerPresent) {
        ResultSet resultSet = (ResultSet) Proxy.newProxyInstance(ResultSet.class.getClassLoader(),
                new Class<?>[] { ResultSet.class }, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "next": return markerPresent;
                        case "close": return null;
                        default: throw new AssertionError(method);
                    }
                });
        PreparedStatement statement = (PreparedStatement) Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),
                new Class<?>[] { PreparedStatement.class }, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "executeQuery": return resultSet;
                        case "close": return null;
                        default: throw new AssertionError(method);
                    }
                });
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                new Class<?>[] { Connection.class }, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "prepareStatement":
                            String sql = (String) args[0];
                            assertTrue(sql.contains("from all_objects"));
                            assertTrue(sql.contains("owner = 'PUBLIC'"));
                            assertTrue(sql.contains("object_name = 'XXI_UPGRADE'"));
                            assertTrue(sql.contains("OBJECT_TYPE = 'SYNONYM'"));
                            return statement;
                        case "setAutoCommit":
                        case "close":
                            return null;
                        default: throw new AssertionError(method);
                    }
                });
    }

    private interface TestConnection {
        Connection open() throws SQLException;
    }

    private static final class TestDriver implements Driver {
        private final String url;
        private final TestConnection connection;

        private TestDriver(String url, TestConnection connection) {
            this.url = url;
            this.connection = connection;
        }

        @Override
        public Connection connect(String url, Properties properties) throws SQLException {
            return acceptsURL(url) ? connection.open() : null;
        }

        @Override
        public boolean acceptsURL(String url) { return this.url.equals(url); }

        @Override
        public DriverPropertyInfo[] getPropertyInfo(String url, Properties properties) { return new DriverPropertyInfo[0]; }

        @Override
        public int getMajorVersion() { return 1; }

        @Override
        public int getMinorVersion() { return 0; }

        @Override
        public boolean jdbcCompliant() { return false; }

        @Override
        public Logger getParentLogger() { return Logger.getGlobal(); }
    }
}
