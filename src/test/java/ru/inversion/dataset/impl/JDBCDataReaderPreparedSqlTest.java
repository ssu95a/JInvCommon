package ru.inversion.dataset.impl;

import org.junit.Test;
import ru.inversion.dataset.DataSetException;
import ru.inversion.dataset.IParameters;
import ru.inversion.dataset.IRowMapper;
import ru.inversion.utils.sparser.jdbc.Parameter;
import ru.inversion.utils.sparser.jdbc.PreparedSql;
import ru.inversion.utils.sparser.select.SqlSelect;
import ru.inversion.utils.sparser.lexer.PostgresSqlScanner;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class JDBCDataReaderPreparedSqlTest {

    @Test
    public void bindsMixedParametersInJdbcOrder() throws Exception {
        RecordingJdbc jdbc = new RecordingJdbc();
        PreparedSql sql = prepared(
                "select ?, ?, ?, ?, ?, ?, ?, ?",
                Parameter.positional(),
                Parameter.named("owner"),
                Parameter.literal("ACTIVE"),
                Parameter.numbered(2),
                Parameter.named("owner"),
                Parameter.positional(),
                Parameter.literal(new BigInteger("10")),
                Parameter.literal(new BigDecimal("3.75")));

        Params params = new Params().at(0, 123L).at(1, 456L).named("owner", 789L);
        reader(jdbc, sql).executeQuery(params);

        assertEquals("select ?, ?, ?, ?, ?, ?, ?, ?", jdbc.statements.get(0).sql);
        assertEquals(Arrays.asList(
                123, 789, "ACTIVE", 456, 789, 456, 10, new BigDecimal("3.75")),
                jdbc.statements.get(0).values());
        assertEquals(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8), jdbc.statements.get(0).indices());
        assertEquals(60, jdbc.statements.get(0).fetchSize);
        assertEquals(1, jdbc.statements.get(0).executions);
        assertTrue(jdbc.statements.get(0).closed); // ResultSet.next() == false
    }

    @Test
    public void resolvesChangedRuntimeValuesOnEachExecution() throws Exception {
        RecordingJdbc jdbc = new RecordingJdbc();
        PreparedSql sql = prepared("select ?, ?", Parameter.named("id"), Parameter.literal("FIXED"));
        JDBCDataReader<Object> reader = reader(jdbc, sql);
        Params params = new Params().named("id", 10L);

        reader.executeQuery(params);
        params.named("id", 20L);
        reader.executeQuery(params);

        assertEquals(2, jdbc.statements.size());
        assertEquals(Arrays.asList(10, "FIXED"), jdbc.statements.get(0).values());
        assertEquals(Arrays.asList(20, "FIXED"), jdbc.statements.get(1).values());
        assertEquals(jdbc.statements.get(0).sql, jdbc.statements.get(1).sql);
    }

    @Test
    public void permitsNullAndPreservesRuntimeConversion() throws Exception {
        RecordingJdbc jdbc = new RecordingJdbc();
        PreparedSql sql = prepared("select ?, ?, ?", Parameter.named("nullable"),
                Parameter.positional(), Parameter.named("booleanValue"));
        reader(jdbc, sql).executeQuery(new Params().named("nullable", null)
                .at(0, "").named("booleanValue", true));
        assertEquals(Arrays.asList(null, null, 1), jdbc.statements.get(0).values());
    }

    @Test
    public void narrowsGeneratedIntegersWithoutNarrowingUserBigInteger() throws Exception {
        RecordingJdbc jdbc = new RecordingJdbc();
        BigInteger huge = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE);
        PreparedSql sql = prepared("select ?, ?, ?, ?",
                Parameter.literal(new BigInteger("1")),
                Parameter.literal(new BigInteger("2147483648")),
                Parameter.literal(huge),
                Parameter.named("userValue"));
        reader(jdbc, sql).executeQuery(new Params().named("userValue", new BigInteger("2")));
        assertEquals(Arrays.asList(1, 2147483648L, huge, new BigInteger("2")),
                jdbc.statements.get(0).values());
    }

    @Test
    public void doesNotOpenStatementWhenParameterResolutionFails() throws Exception {
        RecordingJdbc jdbc = new RecordingJdbc();
        JDBCDataReader<Object> reader = reader(jdbc, prepared("select ?", Parameter.named("explode")));
        try {
            reader.executeQuery(new IParameters() {
                @Override public Object getParameter(String name) {
                    throw new IllegalArgumentException("lookup failed");
                }
                @Override public Object getParameter(int index) { return null; }
            });
            fail("Expected DataSetException");
        } catch (DataSetException expected) {
            assertTrue(expected.getCause() instanceof IllegalArgumentException);
        }
        assertTrue(jdbc.statements.isEmpty());
        assertTrue(reader.isEOF());
    }

    @Test
    public void closesStatementAndDoesNotExecuteOnBindingFailure() throws Exception {
        RecordingJdbc jdbc = new RecordingJdbc();
        jdbc.failOnBinding = 2;
        JDBCDataReader<Object> reader = reader(jdbc, prepared("select ?, ?",
                Parameter.literal("A"), Parameter.literal("B")));
        try {
            reader.executeQuery(new Params());
            fail("Expected DataSetException");
        } catch (DataSetException expected) {
            assertNotNull(expected.getCause());
        }
        assertTrue(jdbc.statements.get(0).closed);
        assertEquals(0, jdbc.statements.get(0).executions);
    }

    @Test
    public void bindsOutputOfRealSqlSelectPrepare() throws Exception {
        RecordingJdbc jdbc = new RecordingJdbc();
        SqlSelect select = new SqlSelect(
                "select id from client where status = 'ACTIVE'",
                new PostgresSqlScanner());
        select.setPredicates(Arrays.asList("owner_id = :ownerId"));
        select.setOrderBy("id desc");
        PreparedSql prepared = select.prepare();

        reader(jdbc, prepared).executeQuery(new Params().named("ownerId", 17L));
        assertEquals(prepared.getSql(), jdbc.statements.get(0).sql);
        assertEquals(Arrays.asList(17, "ACTIVE"), jdbc.statements.get(0).values());
        assertEquals(2, prepared.getParameters().size());
    }

    private static JDBCDataReader<Object> reader(RecordingJdbc jdbc, PreparedSql sql) {
        return new JDBCDataReader<>(jdbc.connection(), sql, dummyMapper(), null, false);
    }

    private static IRowMapper<Object> dummyMapper() {
        return (rs, n) -> new Object();
    }

    private static PreparedSql prepared(String text, Parameter... values) {
        return new PreparedSql(text, Arrays.asList(values));
    }

    private static final class Params implements IParameters {
        final Map<String, Object> names = new HashMap<>();
        final Map<Integer, Object> indices = new HashMap<>();
        Params named(String name, Object value) { names.put(name, value); return this; }
        Params at(int index, Object value) { indices.put(index, value); return this; }
        @Override public Object getParameter(String name) { return names.get(name); }
        @Override public Object getParameter(int index) { return indices.get(index); }
    }

    private static final class RecordingJdbc {
        final List<StatementCalls> statements = new ArrayList<>();
        int failOnBinding = -1;

        Connection connection() {
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                        if (method.getName().equals("prepareStatement")) {
                            StatementCalls calls = new StatementCalls((String) args[0], failOnBinding);
                            statements.add(calls);
                            return calls.proxy();
                        }
                        if (method.getName().equals("toString")) return "RecordingConnection";
                        throw new UnsupportedOperationException("Unexpected Connection." + method.getName());
                    });
        }
    }

    private static final class StatementCalls implements InvocationHandler {
        final String sql;
        final Map<Integer, Object> binds = new HashMap<>();
        final List<Integer> bindingOrder = new ArrayList<>();
        final int failOnBinding;
        boolean closed;
        int executions;
        int fetchSize;

        StatementCalls(String sql, int failOnBinding) {
            this.sql = sql;
            this.failOnBinding = failOnBinding;
        }

        PreparedStatement proxy() {
            return (PreparedStatement) Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),
                    new Class<?>[]{PreparedStatement.class}, this);
        }

        List<Object> values() {
            List<Object> values = new ArrayList<>();
            for (int index : bindingOrder) values.add(binds.get(index));
            return values;
        }

        List<Integer> indices() { return new ArrayList<>(bindingOrder); }

        @Override public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            switch (method.getName()) {
                case "setObject":
                    int index = (Integer) args[0];
                    if (index == failOnBinding) throw new SQLException("Binding failure", "99999");
                    bindingOrder.add(index);
                    binds.put(index, args[1]);
                    return null;
                case "setFetchSize": fetchSize = (Integer) args[0]; return null;
                case "executeQuery":
                    executions++;
                    return Proxy.newProxyInstance(ResultSet.class.getClassLoader(),
                            new Class<?>[]{ResultSet.class}, (rs, m, a) -> {
                                if (m.getName().equals("next")) return false;
                                if (m.getName().equals("close")) return null;
                                if (m.getName().equals("isClosed")) return false;
                                throw new UnsupportedOperationException(m.getName());
                            });
                case "close": closed = true; return null;
                case "isClosed": return closed;
                default: throw new UnsupportedOperationException("Unexpected Statement." + method.getName());
            }
        }
    }
}
