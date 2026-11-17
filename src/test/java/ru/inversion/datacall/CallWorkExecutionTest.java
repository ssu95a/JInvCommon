package ru.inversion.datacall;

import org.junit.jupiter.api.Test;
import ru.inversion.parser.statement.LangEnum;
import ru.inversion.tc.TaskContext;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLXML;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static javax.persistence.ParameterMode.IN;
import static javax.persistence.ParameterMode.INOUT;
import static javax.persistence.ParameterMode.OUT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CallWorkExecutionTest {

    @Test
    void repeatedExecutionReusesStatementAndKeepsSqlBindingOrder() {
        JdbcScenario jdbc = new JdbcScenario();
        CallStatement statement = CallBuilder.NEW()
                .sql("update test_table set b=:b, a=:a")
                .build();
        CallWork work = statement.createCallWork(
                taskContext(jdbc.connection()), null, false
        );

        List<String> supplierOrder = new ArrayList<>();
        AtomicInteger sequence = new AtomicInteger();
        work.set("a", () -> {
            supplierOrder.add("a");
            return sequence.incrementAndGet();
        });
        work.set("b", () -> {
            supplierOrder.add("b");
            return sequence.incrementAndGet();
        });

        work.execute();
        work.execute();

        assertEquals(Arrays.asList("b", "a", "b", "a"), supplierOrder);
        assertEquals(1, jdbc.statements.size());
        assertEquals(2, jdbc.statements.get(0).executions);
        assertEquals(2, jdbc.statements.get(0).clearCalls);
        assertEquals(0, jdbc.statements.get(0).closeCalls);

        work.close();
        assertEquals(1, jdbc.statements.get(0).closeCalls);
    }

    @Test
    void closeAfterExecuteCreatesFreshStatementForEveryExecution() {
        JdbcScenario jdbc = new JdbcScenario();
        CallStatement statement = CallBuilder.NEW()
                .sql("update test_table set value=:value")
                .build();
        CallWork work = statement.createCallWork(
                taskContext(jdbc.connection()), null, true
        );
        work.set("value", 10);

        work.execute();
        work.execute();

        assertEquals(2, jdbc.statements.size());
        assertEquals(1, jdbc.statements.get(0).closeCalls);
        assertEquals(1, jdbc.statements.get(1).closeCalls);
    }

    @Test
    void callableOutValueIsConvertedAndNotifiesBothListeners() {
        JdbcScenario jdbc = new JdbcScenario();
        jdbc.outValues.put(2, 42L);

        CallStatement statement = CallBuilder.NEW()
                .sql("{call test_proc(:inValue, :outValue)}")
                .lang(LangEnum.JDBC)
                .putParameter(new ParameterMetadataBuilder()
                        .name("outValue")
                        .mode(OUT)
                        .sqlType(Types.INTEGER)
                        .build())
                .build();
        CallWork work = statement.createCallWork(
                taskContext(jdbc.connection()), null, false
        );
        work.set("inValue", 7);

        List<String> notifications = new ArrayList<>();
        work.outCallback("outValue", (id, value) ->
                notifications.add("specific:" + id + "=" + value));
        work.outCallback((id, value) ->
                notifications.add("common:" + id + "=" + value));

        work.execute();

        assertEquals(Integer.valueOf(42), work.get("outValue"));
        assertEquals(Arrays.asList(
                "specific:outValue=42",
                "common:outValue=42"
        ), notifications);
        assertEquals(Arrays.asList(2), jdbc.statements.get(0).outPositions);
        work.close();
    }

    @Test
    void inOutSupplierRemainsAnInputSupplierAfterListenerRegistration() {
        JdbcScenario jdbc = new JdbcScenario();
        jdbc.outValues.put(1, 13L);

        CallStatement statement = CallBuilder.NEW()
                .sql("{call update_value(:value)}")
                .lang(LangEnum.JDBC)
                .putParameter(new ParameterMetadataBuilder()
                        .name("value")
                        .mode(INOUT)
                        .sqlType(Types.INTEGER)
                        .build())
                .build();
        CallWork work = statement.createCallWork(
                taskContext(jdbc.connection()), null, false
        );
        work.set("value", () -> 12);

        List<Object> notifications = new ArrayList<>();
        work.outCallback("value", (id, value) ->
                notifications.add(value));

        work.execute();

        assertEquals(12, jdbc.statements.get(0).boundValues.get(1));
        assertEquals(Arrays.asList(13), notifications);
        assertEquals(Integer.valueOf(13), work.get("value"));
        work.close();
    }

    @Test
    void returningOutValueDoesNotNotifySpecificListenerWithIntermediateNull() {
        JdbcScenario jdbc = new JdbcScenario();
        jdbc.generatedKeyLabel = "id";
        jdbc.generatedKeyValue = 77L;

        CallStatement statement = CallBuilder.NEW()
                .sql("insert into test_table(value) values(:value) "
                        + "returning id into :OUT__ID")
                .putParameter(new ParameterMetadataBuilder()
                        .name("OUT__ID")
                        .mode(OUT)
                        .sqlType(Types.INTEGER)
                        .build())
                .build();
        CallWork work = statement.createCallWork(
                taskContext(jdbc.connection()), null, true
        );
        work.set("value", 10);

        List<Object> specificValues = new ArrayList<>();
        AtomicInteger commonNotifications = new AtomicInteger();
        work.outCallback("OUT__ID", (id, value) ->
                specificValues.add(value));
        work.outCallback((id, value) ->
                commonNotifications.incrementAndGet());

        work.execute();

        assertEquals(Arrays.asList(77), specificValues);
        assertEquals(1, commonNotifications.get());
        assertEquals(Integer.valueOf(77), work.get("OUT__ID"));
    }

    @Test
    void resultSetOutDefersStatementClose() {
        JdbcScenario jdbc = new JdbcScenario();
        ResultSet resultSet = resultSet();
        jdbc.outValues.put(1, resultSet);

        CallStatement statement = CallBuilder.NEW()
                .sql("{call test_cursor(:cursorValue)}")
                .lang(LangEnum.JDBC)
                .putParameter(new ParameterMetadataBuilder()
                        .name("cursorValue")
                        .mode(OUT)
                        .sqlType(Types.REF_CURSOR)
                        .javaType(ResultSet.class)
                        .build())
                .build();
        CallWork work = statement.createCallWork(
                taskContext(jdbc.connection()), null, true
        );

        work.execute();

        assertSame(resultSet, work.get("cursorValue"));
        assertEquals(0, jdbc.statements.get(0).closeCalls);
        assertEquals(1, jdbc.statements.get(0).closeOnCompletionCalls);
    }

    @Test
    void autoCommitRestoreFailureDoesNotMaskExecutionFailure() {
        JdbcScenario jdbc = new JdbcScenario();
        jdbc.autoCommit = false;
        jdbc.failExecute = true;
        jdbc.failAutoCommitRestore = true;

        CallStatement statement = CallBuilder.NEW()
                .sql("update test_table set value=:value")
                .autoCommit(true)
                .build();
        CallWork work = statement.createCallWork(
                taskContext(jdbc.connection()), null, false
        );
        work.set("value", 10);

        CallException error = assertThrows(
                CallException.class, work::execute
        );

        assertEquals(Arrays.asList(true, false), jdbc.autoCommitChanges);
        assertTrue(error.getCause().getMessage().contains("Database error"));
        assertEquals(1, error.getCause().getSuppressed().length);
        assertTrue(error.getCause().getSuppressed()[0]
                .getMessage().contains("autoCommit"));
        work.close();
    }

    @Test
    void cleanupFailuresAreReportedAfterSuccessfulExecution() {
        JdbcScenario jdbc = new JdbcScenario();
        jdbc.failClearParameters = true;
        jdbc.failStatementClose = true;

        CallStatement statement = CallBuilder.NEW()
                .sql("update test_table set value=:value")
                .build();
        CallWork work = statement.createCallWork(
                taskContext(jdbc.connection()), null, true
        );
        work.set("value", 10);

        CallException error = assertThrows(
                CallException.class, work::execute
        );

        assertTrue(error.getMessage().contains("cleanup"));
        assertTrue(error.getCause().getMessage().contains("clearParameters"));
        assertEquals(1, error.getCause().getSuppressed().length);
        assertTrue(error.getCause().getSuppressed()[0]
                .getMessage().contains("close"));
    }

    @Test
    void ownedSqlXmlIsFreedWhenBindingFails() {
        JdbcScenario jdbc = new JdbcScenario();
        jdbc.failSqlXmlBinding = true;

        CallStatement statement = CallBuilder.NEW()
                .sql("update test_table set payload=:payload")
                .putParameter(new ParameterMetadataBuilder()
                        .name("payload")
                        .mode(IN)
                        .sqlType(Types.SQLXML)
                        .build())
                .build();
        CallWork work = statement.createCallWork(
                taskContext(jdbc.connection()), null, true
        );
        work.set("payload", "<root/>");

        assertThrows(CallException.class, work::execute);

        assertEquals(1, jdbc.sqlXmlValues.size());
        assertTrue(jdbc.sqlXmlValues.get(0).freed);
    }

    @Test
    void unusedDeclaredParameterIsNotEvaluated() {
        JdbcScenario jdbc = new JdbcScenario();
        CallStatement statement = CallBuilder.NEW()
                .sql("update test_table set value=:usedValue")
                .putParameter(new ParameterMetadataBuilder()
                        .name("unusedValue")
                        .mode(IN)
                        .build())
                .build();
        CallWork work = statement.createCallWork(
                taskContext(jdbc.connection()), null, true
        );
        work.set("usedValue", 10);
        work.set("unusedValue", () -> {
            throw new AssertionError("Неиспользуемый параметр вычислен");
        });

        work.execute();

        assertEquals(1, jdbc.statements.get(0).executions);
    }

    private static TaskContext taskContext(Connection connection) {
        try {
            Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
            Field singleton = unsafeClass.getDeclaredField("theUnsafe");
            singleton.setAccessible(true);
            Object unsafe = singleton.get(null);
            Method allocateInstance = unsafeClass.getMethod(
                    "allocateInstance", Class.class
            );
            TaskContext result = (TaskContext)allocateInstance.invoke(
                    unsafe, TaskContext.class
            );

            Field connectionField = TaskContext.class
                    .getDeclaredField("connection");
            connectionField.setAccessible(true);
            connectionField.set(result, connection);
            return result;
        }
        catch( Exception ex ) {
            throw new AssertionError("Не удалось создать тестовый TaskContext", ex);
        }
    }

    private static ResultSet resultSet() {
        return (ResultSet)Proxy.newProxyInstance(
                ResultSet.class.getClassLoader(),
                new Class<?>[]{ResultSet.class},
                (proxy, method, args) -> defaultValue(method.getReturnType())
        );
    }

    private static Object defaultValue(Class<?> type) {
        if( !type.isPrimitive() )
            return null;
        if( type == boolean.class )
            return false;
        if( type == char.class )
            return '\0';
        if( type == byte.class )
            return (byte)0;
        if( type == short.class )
            return (short)0;
        if( type == int.class )
            return 0;
        if( type == long.class )
            return 0L;
        if( type == float.class )
            return 0F;
        if( type == double.class )
            return 0D;
        return null;
    }

    private static final class JdbcScenario {
        final List<StatementState> statements = new ArrayList<>();
        final List<Boolean> autoCommitChanges = new ArrayList<>();
        final Map<Integer,Object> outValues = new HashMap<>();
        final List<SqlXmlState> sqlXmlValues = new ArrayList<>();

        String generatedKeyLabel;
        Object generatedKeyValue;

        boolean autoCommit = true;
        boolean failExecute;
        boolean failAutoCommitRestore;
        boolean failClearParameters;
        boolean failStatementClose;
        boolean failSqlXmlBinding;

        Connection connection() {
            return (Connection)Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class},
                    this::invokeConnection
            );
        }

        private Object invokeConnection(
                Object proxy,
                Method method,
                Object[] args
        ) throws Throwable {
            switch( method.getName() ) {
                case "prepareStatement":
                    return statement(false).proxy();
                case "prepareCall":
                    return statement(true).proxy();
                case "getAutoCommit":
                    return autoCommit;
                case "setAutoCommit": {
                    boolean value = (Boolean)args[0];
                    autoCommitChanges.add(value);
                    if( !value && failAutoCommitRestore )
                        throw new SQLException("autoCommit restore failure");
                    autoCommit = value;
                    return null;
                }
                case "getMetaData":
                    return databaseMetaData();
                case "createSQLXML": {
                    SqlXmlState state = new SqlXmlState();
                    sqlXmlValues.add(state);
                    return state.proxy();
                }
                case "isClosed":
                    return false;
                case "toString":
                    return "CallWorkTestConnection";
                default:
                    return defaultValue(method.getReturnType());
            }
        }

        private StatementState statement(boolean callable) {
            StatementState result = new StatementState(this, callable);
            statements.add(result);
            return result;
        }

        private DatabaseMetaData databaseMetaData() {
            return (DatabaseMetaData)Proxy.newProxyInstance(
                    DatabaseMetaData.class.getClassLoader(),
                    new Class<?>[]{DatabaseMetaData.class},
                    (proxy, method, args) -> {
                        if( "getDatabaseProductName".equals(method.getName()) )
                            return "PostgreSQL";
                        return defaultValue(method.getReturnType());
                    }
            );
        }

        private ResultSet generatedKeys() {
            AtomicInteger row = new AtomicInteger();
            return (ResultSet)Proxy.newProxyInstance(
                    ResultSet.class.getClassLoader(),
                    new Class<?>[]{ResultSet.class},
                    (proxy, method, args) -> {
                        switch( method.getName() ) {
                            case "next":
                                return row.getAndIncrement() == 0;
                            case "getMetaData":
                                return generatedKeysMetaData();
                            case "getObject":
                                return generatedKeyValue;
                            default:
                                return defaultValue(method.getReturnType());
                        }
                    }
            );
        }

        private ResultSetMetaData generatedKeysMetaData() {
            return (ResultSetMetaData)Proxy.newProxyInstance(
                    ResultSetMetaData.class.getClassLoader(),
                    new Class<?>[]{ResultSetMetaData.class},
                    (proxy, method, args) -> {
                        switch( method.getName() ) {
                            case "getColumnCount":
                                return 1;
                            case "getColumnLabel":
                            case "getColumnName":
                                return generatedKeyLabel;
                            default:
                                return defaultValue(method.getReturnType());
                        }
                    }
            );
        }
    }

    private static final class StatementState implements InvocationHandler {
        final JdbcScenario scenario;
        final boolean callable;
        final List<Integer> outPositions = new ArrayList<>();
        final Map<Integer,Object> boundValues = new HashMap<>();

        int executions;
        int clearCalls;
        int closeCalls;
        int closeOnCompletionCalls;
        boolean closed;

        StatementState(JdbcScenario scenario, boolean callable) {
            this.scenario = scenario;
            this.callable = callable;
        }

        PreparedStatement proxy() {
            Class<?> type = callable
                    ? CallableStatement.class
                    : PreparedStatement.class;
            return (PreparedStatement)Proxy.newProxyInstance(
                    type.getClassLoader(), new Class<?>[]{type}, this
            );
        }

        @Override
        public Object invoke(
                Object proxy,
                Method method,
                Object[] args
        ) throws Throwable {
            switch( method.getName() ) {
                case "setObject":
                    boundValues.put((Integer)args[0], args[1]);
                    return null;
                case "setNull":
                case "setString":
                case "setBinaryStream":
                case "setCharacterStream":
                case "setBlob":
                case "setArray":
                case "setBytes":
                    return null;
                case "setSQLXML":
                    if( scenario.failSqlXmlBinding )
                        throw new SQLException("SQLXML binding failure");
                    return null;
                case "registerOutParameter":
                    outPositions.add((Integer)args[0]);
                    return null;
                case "executeUpdate":
                case "executeLargeUpdate":
                    executions++;
                    if( scenario.failExecute )
                        throw new SQLException("Execution failure", "99999");
                    if( method.getReturnType() == long.class )
                        return 1L;
                    return 1;
                case "getObject":
                    return scenario.outValues.get((Integer)args[0]);
                case "getGeneratedKeys":
                    return scenario.generatedKeys();
                case "clearParameters":
                    clearCalls++;
                    if( scenario.failClearParameters )
                        throw new SQLException("clearParameters failure");
                    return null;
                case "close":
                    closeCalls++;
                    if( scenario.failStatementClose )
                        throw new SQLException("statement close failure");
                    closed = true;
                    return null;
                case "isClosed":
                    return closed;
                case "closeOnCompletion":
                    closeOnCompletionCalls++;
                    return null;
                case "toString":
                    return "CallWorkTestStatement";
                default:
                    return defaultValue(method.getReturnType());
            }
        }
    }

    private static final class SqlXmlState implements InvocationHandler {
        String value;
        boolean freed;

        SQLXML proxy() {
            return (SQLXML)Proxy.newProxyInstance(
                    SQLXML.class.getClassLoader(),
                    new Class<?>[]{SQLXML.class},
                    this
            );
        }

        @Override
        public Object invoke(
                Object proxy,
                Method method,
                Object[] args
        ) {
            switch( method.getName() ) {
                case "setString":
                    value = (String)args[0];
                    return null;
                case "free":
                    freed = true;
                    return null;
                default:
                    return defaultValue(method.getReturnType());
            }
        }
    }
}
