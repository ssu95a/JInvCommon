package ru.inversion.datacall;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import ru.inversion.db.session.SessionEnvironment;
import ru.inversion.db.session.cfg_impl.Pg_DataSourceCfgBuilder;
import ru.inversion.parser.statement.LangEnum;
import ru.inversion.tc.TaskContext;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;

import static javax.persistence.ParameterMode.IN;
import static javax.persistence.ParameterMode.INOUT;
import static javax.persistence.ParameterMode.OUT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Запуск на тестовой PostgreSQL через JUnit 5 в IDE или Maven:
 * {@code mvn "-Dversion.surefire.plugin=2.22.2" "-Dtest=PostgresDataCallIntegrationSmokeTest" test}.
 * Задайте JINV_TEST_PG_URL, JINV_TEST_PG_USER и JINV_TEST_PG_PASSWORD
 * или инициализируйте SessionEnvironment через Logon.login перед тестами.
 * Нужны совместимые с TaskContext USERENV/DUAL, право TEMP и, для штатного
 * маршрута, установленный jf_exec.execute.
 */
@Isolated
class PostgresDataCallIntegrationSmokeTest {

    private static final String DIRECT_PROPERTY = "ru.inversion.datacall.pg_anonymous_direct";
    private static final String SQL = "BEGIN\n"
            + "  :doubled := :amount * 2;\n"
            + "  :balance := :%s + COALESCE(:amount, 0) + 1;\n"
            + "  INSERT INTO pg_temp.jinv_datacall_smoke (value) VALUES (:balance);\n"
            + "END;";

    @Test
    void jfExec() throws Exception {
        checkCall(false, JsonSqlCall.class);
    }

    @Test
    void direct() throws Exception {
        checkCall(true, PGAnonymousSqlCall.class);
    }

    private static void checkCall(boolean direct, Class<? extends IDataCall> implementation) throws Exception {
        String url = System.getenv("JINV_TEST_PG_URL");
        boolean configureSession = url != null && !url.isEmpty();

        // У SessionEnvironment нет публичного сброса. После теста восстанавливаем исходное окружение.
        Field session = SessionEnvironment.class.getDeclaredField("instance");
        session.setAccessible(true);
        Object previousSession = session.get(null);
        assumeTrue(configureSession || previousSession != null,
                "Задайте JINV_TEST_PG_* или вызовите Logon.login перед тестами");

        String previousMode = System.getProperty(DIRECT_PROPERTY);
        try {
            if (configureSession) {
                assertTrue(url.startsWith("jdbc:postgresql:"), "JINV_TEST_PG_URL должен быть JDBC URL PostgreSQL");
                session.set(null, null);
                SessionEnvironment.initialize(new Pg_DataSourceCfgBuilder("DataCallSmoke")
                        .dbUrl(url)
                        .login(requiredEnv("JINV_TEST_PG_USER"))
                        .password(requiredEnv("JINV_TEST_PG_PASSWORD"))
                        .usePseudoConnection(false)
                        .build());
            }

            System.setProperty(DIRECT_PROPERTY, Boolean.toString(direct));
            try (TaskContext tc = new TaskContext()) {
                assertTrue(tc.isPostgreSql(), "Для smoke-теста требуется тестовая PostgreSQL");
                Connection connection = tc.getConnection();
                assertFalse(connection.getAutoCommit());
                try (Statement statement = connection.createStatement()) {
                    statement.execute("CREATE TEMP TABLE jinv_datacall_smoke (value integer) ON COMMIT PRESERVE ROWS");
                }
                // Коммитим только создание временной таблицы, чтобы проверять полный rollback вызовов.
                connection.commit();

                // В jf_exec начальное значение передаётся отдельным IN-параметром.
                String initialBalanceParam = direct ? "balance" : "opening_balance";
                CallBuilder callBuilder = CallBuilder.NEW()
                        .sql(String.format(SQL, initialBalanceParam))
                        .lang(LangEnum.PL_pgSQL)
                        .parameter(IN, "amount", Types.INTEGER, null, Integer.class, null, false)
                        .parameter(OUT, "doubled", Types.INTEGER, null, Integer.class, null, false)
                        .parameter(direct ? INOUT : OUT, "balance", Types.INTEGER, null, Integer.class, null, false);
                if (!direct)
                    callBuilder.parameter(IN, initialBalanceParam, Types.INTEGER, null, Integer.class, null, false);

                // Разным описаниям параметров нужны разные имена в кэше SQLCallBuilder.
                try (IDataCall call = SQLCallBuilder.NEW(tc)
                        .name(direct ? "postgresDatacallSmokeDirect" : "postgresDatacallSmokeJfExec")
                        .callStatement(name -> callBuilder.build())
                        .build()) {
                    assertInstanceOf(implementation, call);

                    call.setParameter("amount", 7);
                    call.setParameter(initialBalanceParam, 10);
                    call.execute();
                    assertEquals(7, call.getParameter("amount"));
                    assertEquals(14, call.getParameter("doubled"));
                    assertEquals(18, call.getParameter("balance"));

                    // Для jf_exec переносим OUT первого вызова во вход второго явно.
                    if (!direct)
                        call.setParameter(initialBalanceParam, call.getParameter("balance"));
                    call.setParameter("amount", null);
                    call.execute();
                    assertNull(call.getParameter("amount"));
                    assertNull(call.getParameter("doubled"));
                    assertEquals(19, call.getParameter("balance"));

                    assertFalse(connection.getAutoCommit());
                    assertEquals(2, rowCount(connection));
                    connection.rollback();
                    assertEquals(0, rowCount(connection));
                }
            }
        } finally {
            if (previousMode == null)
                System.clearProperty(DIRECT_PROPERTY);
            else
                System.setProperty(DIRECT_PROPERTY, previousMode);
            if (configureSession)
                session.set(null, previousSession);
        }
    }

    private static int rowCount(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT count(*) FROM pg_temp.jinv_datacall_smoke")) {
            assertTrue(rows.next());
            return rows.getInt(1);
        }
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        assertTrue(value != null && !value.isEmpty(), "Задайте " + name);
        return value;
    }
}
