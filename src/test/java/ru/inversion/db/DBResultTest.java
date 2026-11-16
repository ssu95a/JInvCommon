package ru.inversion.db;

import org.junit.jupiter.api.Test;
import ru.inversion.tc.TaskContext;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.NamedNativeQueries;
import javax.persistence.NamedNativeQuery;
import javax.persistence.Table;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DBResultTest {

    @NamedNativeQueries({
            @NamedNativeQuery(
                    name = "valueById",
                    query = "SELECT VALUE FROM ORA_VALUES WHERE ID=:id"),
            @NamedNativeQuery(
                    name = "valueById.pg",
                    query = "SELECT VALUE FROM PG_VALUES WHERE ID=:id")
    })
    private static final class QueryHolder {
    }

    @Entity
    @Table(name = "TEST_ROW")
    public static final class TestRow {
        private Long id;
        private String name;

        public TestRow() {
        }

        @Id
        @Column(name = "ID")
        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        @Column(name = "NAME")
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    @Test
    void singleDirectSqlResolvesNamedAndLiteralParameters() throws Exception {
        JdbcStub jdbc = new JdbcStub(
                Collections.singletonList(new Object[] {42L}));

        Optional<Long> value =
                new DBSingleResult<QueryHolder,Long>(
                        taskContext(jdbc.connection()), Long.class)
                        .sql("SELECT VALUE FROM T "
                                + "WHERE ID=:id AND STATE='A'")
                        .set("id", 7L)
                        .execute();

        assertEquals(Optional.of(42L), value);
        assertEquals(
                "SELECT VALUE FROM T WHERE ID=? AND STATE=?",
                jdbc.sql);
        assertEquals(Arrays.asList(7L, "A"), jdbc.boundValues);
        assertEquals(1, jdbc.maxRows);
        assertTrue(jdbc.resultSetClosed);
        assertTrue(jdbc.statementClosed);
        assertFalse(jdbc.connectionClosed);
    }

    @Test
    void tableModeBuildsEntitySelectAndAppliesPredicate() throws Exception {
        JdbcStub jdbc = new JdbcStub(
                Collections.singletonList(
                        new Object[] {10L, "ten"}));

        Optional<TestRow> value =
                new DBSingleResult<Object,TestRow>(
                        taskContext(jdbc.connection()), TestRow.class)
                        .table()
                        .where("ID=:id")
                        .orderBy("ID DESC")
                        .set("id", 10L)
                        .execute();

        assertTrue(value.isPresent());
        assertEquals(Long.valueOf(10L), value.get().getId());
        assertEquals("ten", value.get().getName());
        assertTrue(jdbc.sql.contains(" FROM TEST_ROW"), jdbc.sql);
        assertTrue(jdbc.sql.contains("WHERE ((ID=?"), jdbc.sql);
        assertTrue(jdbc.sql.contains("ORDER BY ID DESC"), jdbc.sql);
        assertEquals(Collections.singletonList(10L), jdbc.boundValues);
    }

    @Test
    void namedQueryUsesDialectVariant() throws Exception {
        JdbcStub jdbc = new JdbcStub(
                Collections.singletonList(new Object[] {11L}));

        Optional<Long> value =
                new DBSingleResult<QueryHolder,Long>(
                        taskContext(jdbc.connection()),
                        QueryHolder.class,
                        Long.class)
                        .queryName("valueById")
                        .set("id", 3L)
                        .execute();

        assertEquals(Optional.of(11L), value);
        assertTrue(jdbc.sql.contains("FROM PG_VALUES"), jdbc.sql);
    }

    @Test
    void singleNoDataCanReturnEmptyOrThrow() throws Exception {
        JdbcStub empty = new JdbcStub(Collections.emptyList());

        Optional<Long> value =
                new DBSingleResult<Object,Long>(
                        taskContext(empty.connection()), Long.class)
                        .sql("SELECT VALUE FROM T")
                        .execute();

        assertEquals(Optional.empty(), value);
        assertTrue(empty.resultSetClosed);
        assertTrue(empty.statementClosed);

        JdbcStub required = new JdbcStub(Collections.emptyList());
        DBSingleResult<Object,Long> query =
                new DBSingleResult<Object,Long>(
                        taskContext(required.connection()), Long.class)
                        .sql("SELECT VALUE FROM T")
                        .throwOnNoDataFound(true);

        assertThrows(DbNoDataFoundException.class, query::execute);
        assertTrue(required.resultSetClosed);
        assertTrue(required.statementClosed);
    }

    @Test
    void missingParameterDoesNotOpenStatement() throws Exception {
        JdbcStub jdbc = new JdbcStub(
                Collections.singletonList(new Object[] {1L}));

        DBSingleResult<Object,Long> query =
                new DBSingleResult<Object,Long>(
                        taskContext(jdbc.connection()), Long.class)
                        .sql("SELECT VALUE FROM T WHERE ID=:missing");

        assertThrows(IllegalStateException.class, query::execute);
        assertEquals(null, jdbc.sql);
        assertFalse(jdbc.statementClosed);
    }

    @Test
    void explicitNullIsBoundAsAParameterValue() throws Exception {
        JdbcStub jdbc = new JdbcStub(
                Collections.singletonList(new Object[] {1L}));

        Optional<Long> value =
                new DBSingleResult<Object,Long>(
                        taskContext(jdbc.connection()), Long.class)
                        .sql("SELECT VALUE FROM T WHERE OWNER=:owner")
                        .set("owner", null)
                        .execute();

        assertEquals(Optional.of(1L), value);
        assertEquals(1, jdbc.boundValues.size());
        assertEquals(null, jdbc.boundValues.get(0));
    }

    @Test
    void iteratorStreamsRowsAndClosesAtEof() throws Exception {
        JdbcStub jdbc = new JdbcStub(Arrays.asList(
                new Object[] {1L},
                new Object[] {2L}
        ));

        DBIterResult<Object,Long> query =
                new DBIterResult<Object,Long>(
                        taskContext(jdbc.connection()), Long.class)
                        .sql("SELECT VALUE FROM T WHERE OWNER=:owner")
                        .set("owner", "SCOTT")
                        .fetchSize(32);

        Iterator<Long> rows = query.execute();

        assertTrue(rows.hasNext());
        assertEquals(Long.valueOf(1L), rows.next());
        assertEquals(Long.valueOf(2L), rows.next());
        assertFalse(rows.hasNext());
        assertEquals(32, jdbc.fetchSize);
        assertTrue(jdbc.resultSetClosed);
        assertTrue(jdbc.statementClosed);
    }

    @Test
    void iteratorCloseReleasesPartiallyConsumedCursor() throws Exception {
        JdbcStub jdbc = new JdbcStub(Arrays.asList(
                new Object[] {1L},
                new Object[] {2L}
        ));

        DBIterResult<Object,Long> query =
                new DBIterResult<Object,Long>(
                        taskContext(jdbc.connection()), Long.class)
                        .sql("SELECT VALUE FROM T");

        Iterator<Long> rows = query.execute();
        assertEquals(Long.valueOf(1L), rows.next());

        query.close();

        assertFalse(rows.hasNext());
        assertTrue(jdbc.resultSetClosed);
        assertTrue(jdbc.statementClosed);
        assertFalse(jdbc.connectionClosed);
    }

    @Test
    void repeatedIteratorExecutionClosesPreviousCursor() throws Exception {
        JdbcStub jdbc = new JdbcStub(Arrays.asList(
                new Object[] {1L},
                new Object[] {2L}
        ));

        DBIterResult<Object,Long> query =
                new DBIterResult<Object,Long>(
                        taskContext(jdbc.connection()), Long.class)
                        .sql("SELECT VALUE FROM T");

        Iterator<Long> first = query.execute();
        assertEquals(Long.valueOf(1L), first.next());

        Iterator<Long> second = query.execute();

        assertFalse(first.hasNext());
        assertEquals(1, jdbc.resultSetCloseCount);
        assertEquals(1, jdbc.statementCloseCount);
        assertEquals(Long.valueOf(1L), second.next());

        query.close();
        assertEquals(2, jdbc.resultSetCloseCount);
        assertEquals(2, jdbc.statementCloseCount);
    }

    @Test
    void iteratorMappingFailureClosesResources() throws Exception {
        JdbcStub jdbc = new JdbcStub(
                Collections.singletonList(new Object[] {1L}));

        DBIterResult<Object,Long> query =
                new DBIterResult<Object,Long>(
                        taskContext(jdbc.connection()), Long.class)
                        .sql("SELECT VALUE FROM T")
                        .mapper((rs, row) -> {
                            throw new SQLException("mapping failed");
                        });

        Iterator<Long> rows = query.execute();
        assertThrows(JInvDbException.class, rows::next);
        assertTrue(jdbc.resultSetClosed);
        assertTrue(jdbc.statementClosed);
    }

    private static TaskContext taskContext(Connection connection)
            throws Exception {
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field singleton = unsafeClass.getDeclaredField("theUnsafe");
        singleton.setAccessible(true);
        Object unsafe = singleton.get(null);

        Method allocateInstance = unsafeClass.getMethod(
                "allocateInstance", Class.class);
        TaskContext tc = (TaskContext)allocateInstance.invoke(
                unsafe, TaskContext.class);

        Field field = TaskContext.class.getDeclaredField("connection");
        field.setAccessible(true);
        field.set(tc, connection);
        return tc;
    }

    private static final class JdbcStub {
        private final List<Object[]> rows;
        private final List<Object> boundValues = new ArrayList<>();

        private String sql;
        private int maxRows;
        private int fetchSize;
        private boolean resultSetClosed;
        private boolean statementClosed;
        private boolean connectionClosed;
        private int resultSetCloseCount;
        private int statementCloseCount;

        private Connection connection;

        private JdbcStub(List<Object[]> rows) {
            this.rows = rows;
        }

        private Connection connection() {
            if( connection != null )
                return connection;

            DatabaseMetaData metadata = proxy(
                    DatabaseMetaData.class,
                    (object, method, arguments) -> {
                        if( "getDatabaseProductName".equals(method.getName()) )
                            return "PostgreSQL";
                        return defaultValue(method.getReturnType());
                    }
            );

            connection = proxy(Connection.class,
                    (object, method, arguments) -> {
                switch( method.getName() ) {
                    case "prepareStatement":
                        sql = (String)arguments[0];
                        return statement();
                    case "getMetaData":
                        return metadata;
                    case "close":
                        connectionClosed = true;
                        return null;
                    case "isClosed":
                        return connectionClosed;
                    default:
                        return defaultValue(method.getReturnType());
                }
            });
            return connection;
        }

        private PreparedStatement statement() {
            final PreparedStatement[] result = new PreparedStatement[1];
            result[0] = proxy(
                    PreparedStatement.class,
                    (object, method, arguments) -> {
                        switch( method.getName() ) {
                            case "setObject":
                                int index = (Integer)arguments[0];
                                while( boundValues.size() < index )
                                    boundValues.add(null);
                                boundValues.set(index - 1, arguments[1]);
                                return null;
                            case "setMaxRows":
                                maxRows = (Integer)arguments[0];
                                return null;
                            case "setFetchSize":
                                fetchSize = (Integer)arguments[0];
                                return null;
                            case "executeQuery":
                                return resultSet(result[0]);
                            case "getConnection":
                                return connection;
                            case "close":
                                statementClosed = true;
                                statementCloseCount++;
                                return null;
                            case "isClosed":
                                return statementClosed;
                            default:
                                return defaultValue(method.getReturnType());
                        }
                    }
            );
            return result[0];
        }

        private ResultSet resultSet(PreparedStatement statement) {
            ResultSetMetaData metadata = proxy(
                    ResultSetMetaData.class,
                    (object, method, arguments) -> {
                        int column = arguments == null
                                ? 0 : (Integer)arguments[0];
                        switch( method.getName() ) {
                            case "getColumnCount":
                                return rows.isEmpty()
                                        ? 1 : rows.get(0).length;
                            case "getColumnLabel":
                            case "getColumnName":
                                return column == 1 ? "ID" : "NAME";
                            case "getColumnType":
                                if( rows.isEmpty() )
                                    return Types.JAVA_OBJECT;
                                Object value = rows.get(0)[column - 1];
                                return value instanceof Number
                                        ? Types.BIGINT : Types.VARCHAR;
                            case "getColumnTypeName":
                                return "TEST";
                            default:
                                return defaultValue(method.getReturnType());
                        }
                    }
            );

            final int[] row = {-1};

            return proxy(ResultSet.class, (object, method, arguments) -> {
                switch( method.getName() ) {
                    case "next":
                        return ++row[0] < rows.size();
                    case "getObject":
                        return rows.get(row[0])[(Integer)arguments[0] - 1];
                    case "getMetaData":
                        return metadata;
                    case "getStatement":
                        return statement;
                    case "close":
                        resultSetClosed = true;
                        resultSetCloseCount++;
                        return null;
                    case "isClosed":
                        return resultSetClosed;
                    default:
                        return defaultValue(method.getReturnType());
                }
            });
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(
            Class<T> type,
            java.lang.reflect.InvocationHandler handler
    ) {
        return (T)Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[] {type},
                handler
        );
    }

    private static Object defaultValue(Class<?> type) {
        if( !type.isPrimitive() )
            return null;
        if( type == boolean.class )
            return false;
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
        if( type == char.class )
            return '\0';
        return null;
    }
}
