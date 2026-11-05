package ru.inversion.dataset.impl;

import org.junit.Test;
import ru.inversion.dataset.DataSetException;
import ru.inversion.dataset.mark.IIDMarkable;
import ru.inversion.dataset.mark.IUMarkable;
import ru.inversion.dataset.mark.MarkDescriptor;
import ru.inversion.db.JInvDbException;
import ru.inversion.tc.TaskContext;
import ru.inversion.utils.sparser.jdbc.Parameter;
import ru.inversion.utils.sparser.jdbc.PreparedSql;

import javax.persistence.Column;
import javax.persistence.Id;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class XXIDsMarkerDaoTest {

    @Test
    public void replacingAllMarksRollsBackDeleteWhenInsertFails()
            throws Exception {
        TxJdbc jdbc = new TxJdbc();
        jdbc.failPreparedExecute = true;

        try {
            XXIDsMarkerDao.markAllRow(
                    taskContext(jdbc.connection()),
                    10L,
                    MarkDescriptor.g_emptyInstance.toCustom(),
                    emptyPrepared("insert into mark values (1)"),
                    Collections.<Object>emptyList(),
                    false
            );
            fail("Expected DataSetException");
        }
        catch( DataSetException expected ) {
            assertNotNull(expected.getCause());
        }

        assertBefore(jdbc.events, "call.execute", "prepared.execute");
        assertBefore(jdbc.events, "prepared.close", "rollback");
        assertFalse(jdbc.events.contains("commit"));
    }

    @Test
    public void replacingAllMarksCommitsOnceAfterBothStatementsClose()
            throws Exception {
        TxJdbc jdbc = new TxJdbc();

        XXIDsMarkerDao.markAllRow(
                taskContext(jdbc.connection()),
                10L,
                MarkDescriptor.g_emptyInstance.toCustom(),
                emptyPrepared("insert into mark values (1)"),
                Collections.<Object>emptyList(),
                false
        );

        assertEquals(1, count(jdbc.events, "commit"));
        assertBefore(jdbc.events, "call.close", "prepared.execute");
        assertBefore(jdbc.events, "prepared.close", "commit");
        assertFalse(jdbc.events.contains("rollback"));
    }

    @Test
    public void invalidPreparedValueCountDoesNotTouchTransaction()
            throws Exception {
        TxJdbc jdbc = new TxJdbc();

        PreparedSql sql = new PreparedSql(
                "insert into mark values (?)",
                Collections.singletonList(Parameter.named("id"))
        );

        try {
            XXIDsMarkerDao.markAllRow(
                    taskContext(jdbc.connection()),
                    10L,
                    MarkDescriptor.g_emptyInstance.toCustom(),
                    sql,
                    Collections.<Object>emptyList(),
                    false
            );
            fail("Expected DataSetException");
        }
        catch( DataSetException expected ) {
            assertTrue(expected.getCause()
                    instanceof IllegalArgumentException);
        }

        assertTrue(jdbc.events.isEmpty());
    }

    @Test
    public void statementCloseFailureRollsBackBeforeAnyCommit()
            throws Exception {
        TxJdbc jdbc = new TxJdbc();
        jdbc.failPreparedClose = true;

        try {
            XXIDsMarkerDao.markAllRow(
                    taskContext(jdbc.connection()),
                    10L,
                    MarkDescriptor.g_emptyInstance.toCustom(),
                    emptyPrepared("insert into mark values (1)"),
                    Collections.<Object>emptyList(),
                    true
            );
            fail("Expected DataSetException");
        }
        catch( DataSetException expected ) {
            assertNotNull(expected.getCause());
        }

        assertTrue(jdbc.events.contains("prepared.execute"));
        assertTrue(jdbc.events.contains("prepared.close"));
        assertTrue(jdbc.events.contains("rollback"));
        assertFalse(jdbc.events.contains("commit"));
    }

    @Test
    public void rollbackFailureIsPreservedAsSuppressed()
            throws Exception {
        TxJdbc jdbc = new TxJdbc();
        jdbc.failPreparedExecute = true;
        jdbc.failRollback = true;

        DataSetException failure;

        try {
            XXIDsMarkerDao.markAllRow(
                    taskContext(jdbc.connection()),
                    10L,
                    MarkDescriptor.g_emptyInstance.toCustom(),
                    emptyPrepared("insert into mark values (1)"),
                    Collections.<Object>emptyList(),
                    true
            );
            fail("Expected DataSetException");
            return;
        }
        catch( DataSetException expected ) {
            failure = expected;
        }

        Throwable cause = failure.getCause();
        assertNotNull(cause);
        assertEquals(1, cause.getSuppressed().length);
        assertTrue(cause.getSuppressed()[0].getMessage()
                .contains("rollback"));
    }

    @Test
    public void markRowRollsBackWhenStoredProcedureFails()
            throws Exception {
        TxJdbc jdbc = new TxJdbc();
        jdbc.failCallableExecute = true;

        MarkIdRow row = new MarkIdRow(7L);
        MarkDescriptor descriptor =
                new MarkDescriptor(MarkIdRow.class);

        try {
            XXIDsMarkerDao.markRow(
                    taskContext(jdbc.connection()),
                    10L,
                    descriptor,
                    row,
                    true
            );
            fail("Expected JInvDbException");
        }
        catch( JInvDbException expected ) {
            assertNotNull(expected.getCause());
        }

        assertTrue(jdbc.events.contains("call.close"));
        assertTrue(jdbc.events.contains("rollback"));
        assertFalse(jdbc.events.contains("commit"));
    }

    @Test
    public void markUUsesOwnTableForDuplicateCheck()
            throws Exception {
        TxJdbc jdbc = new TxJdbc();

        XXIDsMarkerDao.markAll(
                taskContext(jdbc.connection()),
                10L,
                new MarkDescriptor(UMarkRow.class),
                Collections.singletonList("U-1")
        );

        assertNotNull(jdbc.preparedSql);
        assertTrue(
                jdbc.preparedSql,
                jdbc.preparedSql.contains("from v_jf_mrk_u a")
        );
        assertFalse(
                jdbc.preparedSql,
                jdbc.preparedSql.contains("from v_jf_mrk_id a")
        );
        assertBefore(jdbc.events, "prepared.close", "commit");
    }

    @Test
    public void markAllUsesBatchesOfAtMostFiftyRows()
            throws Exception {
        TxJdbc jdbc = new TxJdbc();
        List<String> ids = new ArrayList<>();

        for( int i = 0; i < 51; i++ )
            ids.add("U-" + i);

        XXIDsMarkerDao.markAll(
                taskContext(jdbc.connection()),
                10L,
                new MarkDescriptor(UMarkRow.class),
                ids
        );

        assertEquals(2, jdbc.executeBatchCount);
    }

    private static PreparedSql emptyPrepared(String sql) {
        return new PreparedSql(
                sql,
                Collections.<Parameter>emptyList()
        );
    }

    private static TaskContext taskContext(Connection connection)
            throws Exception {
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field singleton =
                unsafeClass.getDeclaredField("theUnsafe");
        singleton.setAccessible(true);
        Object unsafe = singleton.get(null);

        Method allocateInstance =
                unsafeClass.getMethod(
                        "allocateInstance",
                        Class.class);

        TaskContext tc = (TaskContext) allocateInstance.invoke(
                unsafe,
                TaskContext.class
        );

        Field field =
                TaskContext.class.getDeclaredField("connection");
        field.setAccessible(true);
        field.set(tc, connection);

        return tc;
    }

    private static int count(
            List<String> events,
            String value
    ) {
        int result = 0;

        for( String event : events )
            if( value.equals(event) )
                result++;

        return result;
    }

    private static void assertBefore(
            List<String> events,
            String first,
            String second
    ) {
        int firstIndex = events.indexOf(first);
        int secondIndex = events.indexOf(second);

        assertTrue("Missing event " + first + ": " + events,
                firstIndex >= 0);
        assertTrue("Missing event " + second + ": " + events,
                secondIndex >= 0);
        assertTrue(events.toString(), firstIndex < secondIndex);
    }

    public static final class MarkIdRow implements IIDMarkable {
        private final Long id;
        private boolean mark;

        MarkIdRow(Long id) {
            this.id = id;
        }

        @Override
        public boolean isMark() {
            return mark;
        }

        @Override
        public void setMark(boolean mark) {
            this.mark = mark;
        }

        @Id
        @Column(name = "ID")
        @Override
        public Long getMarkLongID() {
            return id;
        }
    }

    public static final class UMarkRow implements IUMarkable {
        private boolean mark;

        @Override
        public boolean isMark() {
            return mark;
        }

        @Override
        public void setMark(boolean mark) {
            this.mark = mark;
        }

        @Id
        @Column(name = "ID")
        @Override
        public String getMarkStringID() {
            return "U";
        }
    }

    private static final class TxJdbc {
        final List<String> events = new ArrayList<>();

        boolean failPreparedExecute;
        boolean failPreparedClose;
        boolean failCallableExecute;
        boolean failRollback;

        String preparedSql;
        int executeBatchCount;

        Connection connection() {
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class},
                    (proxy, method, args) -> {
                        switch( method.getName() ) {
                            case "getAutoCommit":
                                return false;
                            case "commit":
                                events.add("commit");
                                return null;
                            case "rollback":
                                events.add("rollback");
                                if( failRollback )
                                    throw new SQLException(
                                            "rollback failure",
                                            "99994");
                                return null;
                            case "prepareCall":
                                events.add("prepareCall");
                                return callable();
                            case "prepareStatement":
                                preparedSql = (String)args[0];
                                events.add("prepareStatement");
                                return prepared();
                            case "toString":
                                return "MarkerConnection";
                            default:
                                throw new UnsupportedOperationException(
                                        "Unexpected Connection."
                                                + method.getName());
                        }
                    }
            );
        }

        private CallableStatement callable() {
            return (CallableStatement) Proxy.newProxyInstance(
                    CallableStatement.class.getClassLoader(),
                    new Class<?>[]{CallableStatement.class},
                    (proxy, method, args) -> {
                        switch( method.getName() ) {
                            case "setLong":
                            case "setInt":
                            case "setObject":
                                return null;
                            case "execute":
                                events.add("call.execute");
                                if( failCallableExecute )
                                    throw new SQLException(
                                            "call execute failure",
                                            "99993");
                                return true;
                            case "close":
                                events.add("call.close");
                                return null;
                            default:
                                throw new UnsupportedOperationException(
                                        "Unexpected CallableStatement."
                                                + method.getName());
                        }
                    }
            );
        }

        private PreparedStatement prepared() {
            return (PreparedStatement) Proxy.newProxyInstance(
                    PreparedStatement.class.getClassLoader(),
                    new Class<?>[]{PreparedStatement.class},
                    (proxy, method, args) -> {
                        switch( method.getName() ) {
                            case "setLong":
                            case "setObject":
                                return null;
                            case "executeUpdate":
                                events.add("prepared.execute");
                                if( failPreparedExecute )
                                    throw new SQLException(
                                            "prepared execute failure",
                                            "99992");
                                return 1;
                            case "addBatch":
                                return null;
                            case "executeBatch":
                                events.add("prepared.batch");
                                executeBatchCount++;
                                return new int[0];
                            case "close":
                                events.add("prepared.close");
                                if( failPreparedClose )
                                    throw new SQLException(
                                            "prepared close failure",
                                            "99991");
                                return null;
                            default:
                                throw new UnsupportedOperationException(
                                        "Unexpected PreparedStatement."
                                                + method.getName());
                        }
                    }
            );
        }
    }
}
