
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import ru.inversion.db.expr.SQLExpressionFactory;
import ru.inversion.db.session.SessionEnvironment;
import ru.inversion.db.session.cfg_impl.Pg_DataSourceCfgBuilder;
import ru.inversion.parser.statement.LangEnum;
import ru.inversion.tc.TaskContext;
import ru.inversion.utils.ParametersValues;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Isolated
class PostgresTextExpressionTest {

    @Test
    void preparesOnlyAnonymousTextCalls() throws Exception {
        String url = System.getenv("JINV_TEST_PG_URL");
        assumeTrue(url != null && !url.isEmpty(), "Задайте JINV_TEST_PG_URL, JINV_TEST_PG_USER и JINV_TEST_PG_PASSWORD");
        Field session = SessionEnvironment.class.getDeclaredField("instance");
        session.setAccessible(true);
        Object previousSession = session.get(null);

        try {
            session.set(null, null);
            SessionEnvironment.initialize(new Pg_DataSourceCfgBuilder("PostgresTextExpressionTest")
                    .dbUrl(url)
                    .login(System.getenv("JINV_TEST_PG_USER"))
                    .password(System.getenv("JINV_TEST_PG_PASSWORD"))
                    .usePseudoConnection(false)
                    .build());
            try (TaskContext tc = new TaskContext()) {
                assertTrue(tc.isPostgreSql());
                checkAnonymousText(tc.getConnection());
                checkOrdinarySql(tc.getConnection());
                CallStatement block = CallBuilder.NEW().lang(LangEnum.PL_pgSQL).sql("BEGIN NULL; END;").build();
                try (IDataCall call = SQLCallBuilder.NEW(tc).callStatement(ignored -> block).runByDbmsSql(true).build()) {
                    assertInstanceOf(SQLCallBuilder.DbmsSqlDataCall.class, call);
                }
            }
        } finally {
            session.set(null, previousSession);
        }
    }

    private static void checkAnonymousText(Connection connection) throws Exception {
        ParametersValues values = new ParametersValues() {
            @Override
            public Object getParameter(String name) {
                assertFalse("o1".equalsIgnoreCase(name), "o1 нельзя читать как входной параметр");
                return super.getParameter(name);
            }
        };
        values.setParameter("order_count", 6);
        values.setParameter("note", null);
        values.setParameter("o1", "previous");
        values.setParameter("rate", 1.5d);
        values.setParameter("enabled", true);

        String sql = "BEGIN :order_count := :order_count + 1; :note := coalesce(:note, 'new');"
                + " :rate := :rate + 1; :enabled := NOT :enabled; :O1 := coalesce(:o1, 'done'); END;";
        SQLExpressionFactory.execute("testPostgresTextExpression", sql, connection, values);
        assertEquals(Integer.valueOf(7), values.get("order_count"));
        assertEquals("new", values.get("note"));
        assertEquals("done", values.get("o1"));
        assertEquals(Double.valueOf(2.5), values.get("rate"));
        assertEquals(Boolean.FALSE, values.get("enabled"));

        // SQL того же exprId остаётся в кэше. Типы bind вычисляются заново из текущих значений.
        values.setParameter("order_count", new BigDecimal("1.5"));
        SQLExpressionFactory.execute("testPostgresTextExpression", sql, connection, values);
        assertEquals(new BigDecimal("2.5"), values.get("order_count"));
    }

    private static void checkOrdinarySql(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TEMP TABLE jinv_text_expr_test (value integer)");
            ParametersValues values = new ParametersValues();
            values.setParameter("input", 42);
            SQLExpressionFactory.execute("INSERT INTO jinv_text_expr_test VALUES (:input)", connection, values);
            try (ResultSet rows = statement.executeQuery("SELECT value FROM jinv_text_expr_test")) {
                assertTrue(rows.next());
                assertEquals(42, rows.getInt(1));
            }
        }
    }
}
