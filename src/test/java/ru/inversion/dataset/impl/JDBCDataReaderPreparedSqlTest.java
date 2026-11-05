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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.persistence.Column;
import javax.persistence.Id;

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
    public void executesAlreadyResolvedJdbcValuesWithoutParameterInterpretation() throws Exception {
        RecordingJdbc jdbc = new RecordingJdbc();
        PreparedSql sql = prepared(
                "select ?, ?",
                Parameter.named("ignored"),
                Parameter.literal(new BigInteger("10"))
        );

        reader(jdbc, sql).executeQuery(Arrays.<Object>asList(17, 10));

        assertEquals(
                Arrays.asList(17, 10),
                jdbc.statements.get(0).values()
        );
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
    public void executesLiteralOnlySqlWithoutRuntimeParameters() throws Exception {
        RecordingJdbc jdbc = new RecordingJdbc();

        PreparedSql sql = new SqlSelect(
                "select id from t where state = 'ACTIVE' and type = 10",
                new PostgresSqlScanner()
        ).prepare();

        reader(jdbc, sql).executeQuery((IParameters) null);

        assertEquals(
                Arrays.asList("ACTIVE", 10),
                jdbc.statements.get(0).values()
        );
    }

    @Test
    public void nullRuntimeParametersFailWhenBindValueIsRequired() throws Exception {
        RecordingJdbc jdbc = new RecordingJdbc();

        JDBCDataReader<Object> reader = reader(
                jdbc,
                prepared("select ?", Parameter.named("id"))
        );

        try {
            reader.executeQuery((IParameters) null);
            fail("Expected DataSetException");
        }
        catch (DataSetException expected) {
            assertTrue(expected.getCause() instanceof IllegalArgumentException);
            assertTrue(expected.getCause().getMessage().contains(":id"));
        }

        assertTrue(jdbc.statements.isEmpty());
        assertTrue(reader.isEOF());
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
    public void bindingFailurePreservesStatementCloseFailureAsSuppressed() throws Exception {
        RecordingJdbc jdbc = new RecordingJdbc();
        jdbc.failOnBinding = 2;
        jdbc.failStatementClose = true;

        JDBCDataReader<Object> reader = reader(
                jdbc,
                prepared("select ?, ?",
                        Parameter.literal("A"),
                        Parameter.literal("B"))
        );

        try {
            reader.executeQuery(new Params());
            fail("Expected DataSetException");
        }
        catch (DataSetException expected) {
            assertNotNull(expected.getCause());
            assertEquals(1, expected.getCause().getSuppressed().length);
            assertEquals(
                    "Statement close failure",
                    expected.getCause().getSuppressed()[0].getMessage()
            );
        }
    }

    @Test
    public void explicitCloseReportsResultSetAndStatementFailures() throws Exception {
        RecordingJdbc jdbc = new RecordingJdbc();
        jdbc.resultSetHasRow = true;
        jdbc.failResultSetClose = true;
        jdbc.failStatementClose = true;

        JDBCDataReader<Object> reader = reader(
                jdbc,
                prepared("select 1")
        );

        reader.executeQuery(Collections.<Object>emptyList());

        try {
            reader.close();
            fail("Expected DataSetException");
        }
        catch (DataSetException expected) {
            assertTrue(expected.getCause() instanceof SQLException);
            assertEquals(
                    "ResultSet close failure",
                    expected.getCause().getMessage()
            );
            assertEquals(1, expected.getCause().getSuppressed().length);
            assertEquals(
                    "Statement close failure",
                    expected.getCause().getSuppressed()[0].getMessage()
            );
        }

        assertTrue(reader.isEOF());
        assertTrue(jdbc.statements.get(0).closed);
    }

    @Test
    public void expiredCursorIsRolledBackReexecutedAndResumesAfterReturnedRow() throws Exception {
        RecoveryJdbc jdbc = new RecoveryJdbc();

        JDBCDataReader<RecoveryRow> reader =
                recoveryReader(jdbc);

        reader.executeQuery(Collections.<Object>emptyList());

        List<RecoveryRow> firstPage = reader.getNextPage(2);
        List<RecoveryRow> secondPage = reader.getNextPage(2);

        assertEquals(
                Arrays.asList(1L, 2L),
                recoveryIds(firstPage)
        );
        assertEquals(
                Collections.singletonList(3L),
                recoveryIds(secondPage)
        );

        assertEquals(2, jdbc.executions);
        assertBefore(jdbc.events, "rs1.close", "rollback");
        assertBefore(jdbc.events, "rollback", "execute.2");
        assertTrue(reader.isEOF());
    }

    @Test
    public void rollbackFailureDuringCursorRecoveryClosesStatement() throws Exception {
        RecoveryJdbc jdbc = new RecoveryJdbc();
        jdbc.failRollback = true;

        JDBCDataReader<RecoveryRow> reader =
                recoveryReader(jdbc);

        reader.executeQuery(Collections.<Object>emptyList());

        try {
            reader.getNextPage(1);
            fail("Expected cursor recovery failure");
        }
        catch( RuntimeException expected ) {
            assertEquals(
                    "Rollback failure",
                    rootCause(expected).getMessage()
            );
        }

        assertBefore(jdbc.events, "rs1.close", "rollback");
        assertTrue(jdbc.statementClosed);
        assertTrue(reader.isEOF());
    }

    @Test
    public void reexecuteFailureDuringCursorRecoveryClosesStatement() throws Exception {
        RecoveryJdbc jdbc = new RecoveryJdbc();
        jdbc.failReexecute = true;

        JDBCDataReader<RecoveryRow> reader =
                recoveryReader(jdbc);

        reader.executeQuery(Collections.<Object>emptyList());

        try {
            reader.getNextPage(1);
            fail("Expected cursor recovery failure");
        }
        catch( RuntimeException expected ) {
            assertEquals(
                    "Re-execute failure",
                    rootCause(expected).getMessage()
            );
        }

        assertBefore(jdbc.events, "rollback", "execute.2");
        assertTrue(jdbc.statementClosed);
        assertTrue(reader.isEOF());
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

    private static JDBCDataReader<RecoveryRow> recoveryReader(
            RecoveryJdbc jdbc
    ) {
        return new JDBCDataReader<>(
                jdbc.connection(),
                prepared("select id"),
                (rs, n) -> new RecoveryRow(rs.getLong(1)),
                RecoveryRow.class,
                false
        );
    }

    private static List<Long> recoveryIds(List<RecoveryRow> rows) {
        List<Long> ids = new ArrayList<>();

        for( RecoveryRow row : rows )
            ids.add(row.getId());

        return ids;
    }

    private static void assertBefore(
            List<String> events,
            String first,
            String second
    ) {
        int firstIndex = events.indexOf(first);
        int secondIndex = events.indexOf(second);

        assertTrue("Missing event " + first + ": " + events, firstIndex >= 0);
        assertTrue("Missing event " + second + ": " + events, secondIndex >= 0);
        assertTrue(
                first + " must happen before " + second + ": " + events,
                firstIndex < secondIndex
        );
    }

    private static Throwable rootCause(Throwable failure) {
        Throwable result = failure;

        while( result.getCause() != null )
            result = result.getCause();

        return result;
    }

    private static final class RecoveryRow {
        private long id;

        RecoveryRow() {
        }

        RecoveryRow(long id) {
            this.id = id;
        }

        @Id
        @Column(name = "ID")
        public long getId() {
            return id;
        }

        public void setId(long id) {
            this.id = id;
        }
    }

    private static final class RecoveryJdbc {
        final List<String> events = new ArrayList<>();

        int executions;
        boolean failRollback;
        boolean failReexecute;
        boolean statementClosed;

        Connection connection() {
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class},
                    (proxy, method, args) -> {
                        switch( method.getName() ) {
                            case "prepareStatement":
                                events.add("prepare");
                                return statement();
                            case "rollback":
                                events.add("rollback");

                                if( failRollback )
                                    throw new SQLException(
                                            "Rollback failure",
                                            "99996");

                                return null;
                            case "toString":
                                return "RecoveryConnection";
                            default:
                                throw new UnsupportedOperationException(
                                        "Unexpected Connection."
                                                + method.getName());
                        }
                    }
            );
        }

        private PreparedStatement statement() {
            return (PreparedStatement) Proxy.newProxyInstance(
                    PreparedStatement.class.getClassLoader(),
                    new Class<?>[]{PreparedStatement.class},
                    (proxy, method, args) -> {
                        switch( method.getName() ) {
                            case "setFetchSize":
                            case "setObject":
                                return null;

                            case "executeQuery":
                                executions++;
                                events.add("execute." + executions);

                                if( executions == 2 && failReexecute )
                                    throw new SQLException(
                                            "Re-execute failure",
                                            "99995");

                                return executions == 1
                                        ? resultSet(
                                                "rs1",
                                                new long[]{1L},
                                                true)
                                        : resultSet(
                                                "rs2",
                                                new long[]{1L, 2L, 3L},
                                                false);

                            case "close":
                                statementClosed = true;
                                events.add("statement.close");
                                return null;

                            case "isClosed":
                                return statementClosed;

                            default:
                                throw new UnsupportedOperationException(
                                        "Unexpected Statement."
                                                + method.getName());
                        }
                    }
            );
        }

        private ResultSet resultSet(
                String name,
                long[] rows,
                boolean expireOnSecondNext
        ) {
            return (ResultSet) Proxy.newProxyInstance(
                    ResultSet.class.getClassLoader(),
                    new Class<?>[]{ResultSet.class},
                    new InvocationHandler() {
                        int index = -1;
                        int nextCalls;
                        boolean closed;

                        @Override
                        public Object invoke(
                                Object proxy,
                                Method method,
                                Object[] args
                        ) throws Throwable {
                            switch( method.getName() ) {
                                case "next":
                                    nextCalls++;

                                    if( expireOnSecondNext
                                            && nextCalls == 2 ) {
                                        events.add(name + ".next.34000");
                                        throw new SQLException(
                                                "Cursor expired",
                                                "34000");
                                    }

                                    index++;
                                    events.add(name + ".next");

                                    return index < rows.length;

                                case "getLong":
                                    if( index < 0 || index >= rows.length )
                                        throw new SQLException(
                                                "ResultSet is not on a row");

                                    return rows[index];

                                case "close":
                                    closed = true;
                                    events.add(name + ".close");
                                    return null;

                                case "isClosed":
                                    return closed;

                                default:
                                    throw new UnsupportedOperationException(
                                            "Unexpected ResultSet."
                                                    + method.getName());
                            }
                        }
                    }
            );
        }
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
        boolean resultSetHasRow;
        boolean failResultSetClose;
        boolean failStatementClose;

        Connection connection() {
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                        if (method.getName().equals("prepareStatement")) {
                            StatementCalls calls = new StatementCalls(
                                    (String) args[0],
                                    failOnBinding,
                                    resultSetHasRow,
                                    failResultSetClose,
                                    failStatementClose
                            );
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
        final boolean resultSetHasRow;
        final boolean failResultSetClose;
        final boolean failStatementClose;
        boolean closed;
        int executions;
        int fetchSize;

        StatementCalls(
                String sql,
                int failOnBinding,
                boolean resultSetHasRow,
                boolean failResultSetClose,
                boolean failStatementClose
        ) {
            this.sql = sql;
            this.failOnBinding = failOnBinding;
            this.resultSetHasRow = resultSetHasRow;
            this.failResultSetClose = failResultSetClose;
            this.failStatementClose = failStatementClose;
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
                            new Class<?>[]{ResultSet.class}, new InvocationHandler() {
                                private boolean first = true;
                                private boolean resultSetClosed;

                                @Override
                                public Object invoke(
                                        Object rs,
                                        Method m,
                                        Object[] a
                                ) throws Throwable {
                                    switch (m.getName()) {
                                        case "next":
                                            if (first) {
                                                first = false;
                                                return resultSetHasRow;
                                            }
                                            return false;
                                        case "close":
                                            resultSetClosed = true;
                                            if (failResultSetClose)
                                                throw new SQLException(
                                                        "ResultSet close failure",
                                                        "99998");
                                            return null;
                                        case "isClosed":
                                            return resultSetClosed;
                                        default:
                                            throw new UnsupportedOperationException(
                                                    m.getName());
                                    }
                                }
                            });
                case "close":
                    closed = true;
                    if (failStatementClose)
                        throw new SQLException(
                                "Statement close failure",
                                "99997");
                    return null;
                case "isClosed": return closed;
                default: throw new UnsupportedOperationException("Unexpected Statement." + method.getName());
            }
        }
    }
}
