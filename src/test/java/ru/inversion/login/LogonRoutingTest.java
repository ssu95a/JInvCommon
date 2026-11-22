package ru.inversion.login;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import ru.inversion.db.session.AbstractDataSourceCfgBuilder;
import ru.inversion.db.session.DataSourceCfg;
import ru.inversion.db.session.cfg_impl.Ora_DataSourceCfgBuilder;
import ru.inversion.db.session.cfg_impl.Pg_DataSourceCfgBuilder;
import ru.inversion.db.session.xxi.XXIConnectorException;

import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static ru.inversion.db.session.cfg_impl.Ora_DataSourceCfgBuilder.ORACLE_TNS_ADMIN;
import static ru.inversion.db.session.xxi.XXIConnectorException.Reason.TNS_CONFIGURATION_ERROR;

public class LogonRoutingTest {

    private static final String ORACLE_URL = "jdbc:oracle:thin:@//postgres-dev:1521/service";
    private static final String POSTGRES_URL = "jdbc:postgresql://pg-dev:5432/db";
    private static final String DRIVER_PROPERTY = "ru.inversion.driver.class_name";

    @Rule
    public final TemporaryFolder tnsFiles = new TemporaryFolder();

    @Test
    public void separatesPasswordFromAliasOrJdbcUrl() {
        assertDatabaseSeparator("TEST_ALIAS");
        assertDatabaseSeparator(ORACLE_URL);
        assertDatabaseSeparator(POSTGRES_URL + "?ApplicationName=user@app");
    }

    @Test
    public void selectsDirectBuilderByJdbcPrefix() throws Exception {
        assertEquals(Ora_DataSourceCfgBuilder.class, builderFor(ORACLE_URL).getClass());
        assertEquals(Ora_DataSourceCfgBuilder.class, builderFor("TEST_ALIAS").getClass());

        AbstractDataSourceCfgBuilder pg = builderFor(POSTGRES_URL);
        assertEquals(Pg_DataSourceCfgBuilder.class, pg.getClass());
        assertEquals(POSTGRES_URL, pg.build().getConnectUrl());
    }

    @Test
    public void explicitOracleAddressDoesNotRequireTnsNames() throws Exception {
        String tnsAdmin = System.getProperty(ORACLE_TNS_ADMIN);
        String driver = System.getProperty(DRIVER_PROPERTY);
        System.setProperty(ORACLE_TNS_ADMIN, "");
        try {
            DataSourceCfg cfg = builderFor(ORACLE_URL).build();

            assertEquals(ORACLE_URL, cfg.getConnectUrl());
            assertEquals(ORACLE_URL,
                    cfg.getConnectionProperties("user", "password", ORACLE_URL).getProperty("url"));
            assertEquals(TNS_CONFIGURATION_ERROR,
                    assertThrows(XXIConnectorException.class, () -> builderFor("TEST_ALIAS").build()).getReason());
            assertEquals(TNS_CONFIGURATION_ERROR, assertThrows(XXIConnectorException.class,
                    () -> builderFor("jdbc:oracle:thin:@TEST_ALIAS").build()).getReason());
            XXIConnectorException overrideFailure = assertThrows(XXIConnectorException.class,
                    () -> cfg.getConnectionProperties("user", "password", "TEST_ALIAS"));
            assertEquals(TNS_CONFIGURATION_ERROR, overrideFailure.getReason());
            assertEquals(IllegalStateException.class, overrideFailure.getCause().getClass());
        } finally {
            restoreProperty(ORACLE_TNS_ADMIN, tnsAdmin);
            restoreProperty(DRIVER_PROPERTY, driver);
        }
    }

    @Test
    public void reportsInvalidTnsDirectoryAndMissingTnsNamesFile() throws Exception {
        String tnsAdmin = System.getProperty(ORACLE_TNS_ADMIN);
        try {
            System.setProperty(ORACLE_TNS_ADMIN, tnsFiles.newFile().getAbsolutePath());
            XXIConnectorException invalidDirectory = assertThrows(XXIConnectorException.class,
                    () -> builderFor("TEST_ALIAS").build());
            assertEquals(TNS_CONFIGURATION_ERROR, invalidDirectory.getReason());
            assertEquals(NotDirectoryException.class, invalidDirectory.getCause().getClass());

            System.setProperty(ORACLE_TNS_ADMIN, tnsFiles.newFolder().getAbsolutePath());
            XXIConnectorException missingFile = assertThrows(XXIConnectorException.class,
                    () -> builderFor("TEST_ALIAS").build());
            assertEquals(TNS_CONFIGURATION_ERROR, missingFile.getReason());
            assertEquals(NoSuchFileException.class, missingFile.getCause().getClass());
        } finally {
            restoreProperty(ORACLE_TNS_ADMIN, tnsAdmin);
        }
    }

    private static void assertDatabaseSeparator(String dbUrl) {
        String password = "p@ss";
        String connectString = "user/" + password + "@" + dbUrl;
        int passwordStart = connectString.indexOf('/') + 1;
        int separator = Logon.findDatabaseSeparator(connectString, passwordStart);
        assertEquals(password, connectString.substring(passwordStart, separator));
        assertEquals(dbUrl, connectString.substring(separator + 1));
    }

    private static AbstractDataSourceCfgBuilder builderFor(String url) {
        return AbstractDataSourceCfgBuilder.create(key -> {
            if ("app_id".equals(key)) return "test";
            return "url".equals(key) ? url : null;
        }).login("user").password("password").dbUrl(url).usePseudoConnection(false);
    }

    private static void restoreProperty(String key, String previousValue) {
        if (previousValue == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, previousValue);
        }
    }
}
