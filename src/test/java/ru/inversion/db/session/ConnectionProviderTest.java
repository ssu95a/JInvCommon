package ru.inversion.db.session;

import org.junit.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.logging.Logger;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

public class ConnectionProviderTest {

    private static final String URL = "jdbc:jinv-provider-test:default";
    private static final String OVERRIDE_URL = "jdbc:jinv-provider-test:override";

    @Test
    public void closesConnectionOnSetupFailureInEveryConfigModeOverload() throws Exception {
        ConnectionProvider provider = configuredProvider();
        List<Callable<Connection>> opens = Arrays.asList(
                provider::getConnection,
                () -> provider.getConnection("other", "test-only"),
                () -> provider.getConnection("other", "test-only", OVERRIDE_URL)
        );

        try( TestDriver driver = new TestDriver() ) {
            driver.closeFailure = new SQLException("close failed");
            int attempts = 0;
            for( Callable<Connection> open : opens ) {
                driver.setupFailure = new SQLException("setAutoCommit failed");

                SQLException failure = assertThrows(SQLException.class, open::call);

                assertEquals(++attempts, driver.closeCalls);
                assertSame(driver.setupFailure, failure);
                assertArrayEquals(new Throwable[]{driver.closeFailure}, failure.getSuppressed());
            }
        }
    }

    @Test
    public void returnsConfiguredConnectionForCallerToClose() throws SQLException {
        try( TestDriver driver = new TestDriver() ) {
            try( Connection connection = configuredProvider().getConnection() ) {
                assertSame(driver.connection, connection);
                assertFalse(connection.getAutoCommit());
                assertEquals(0, driver.closeCalls);
            }
            assertEquals(1, driver.closeCalls);
        }
    }

    private static ConnectionProvider configuredProvider() {
        return new ConnectionProvider(new DataSourceCfg(
                "test-user", "test-only", URL, URL, null, false, new Properties(), null, null
        ));
    }

    private static final class TestDriver implements Driver, AutoCloseable {
        private SQLException setupFailure;
        private SQLException closeFailure;
        private int closeCalls;
        private boolean autoCommit = true;
        private final Connection connection = (Connection)Proxy.newProxyInstance(
                TestDriver.class.getClassLoader(), new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    switch( method.getName() ) {
                        case "setAutoCommit":
                            if( setupFailure != null )
                                throw setupFailure;
                            autoCommit = (Boolean)args[0];
                            return null;
                        case "getAutoCommit":
                            return autoCommit;
                        case "close":
                            closeCalls++;
                            if( closeFailure != null )
                                throw closeFailure;
                            return null;
                        default:
                            throw new AssertionError(method.getName());
                    }
                }
        );

        private TestDriver() throws SQLException { DriverManager.registerDriver(this); }

        @Override public Connection connect(String url, Properties properties) {
            return acceptsURL(url) ? connection : null;
        }
        @Override public boolean acceptsURL(String url) { return URL.equals(url) || OVERRIDE_URL.equals(url); }
        @Override public DriverPropertyInfo[] getPropertyInfo(String url, Properties properties) { return new DriverPropertyInfo[0]; }
        @Override public int getMajorVersion() { return 1; }
        @Override public int getMinorVersion() { return 0; }
        @Override public boolean jdbcCompliant() { return false; }
        @Override public Logger getParentLogger() { return Logger.getGlobal(); }
        @Override public void close() throws SQLException { DriverManager.deregisterDriver(this); }
    }
}
