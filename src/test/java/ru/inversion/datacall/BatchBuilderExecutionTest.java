package ru.inversion.datacall;

import org.junit.Test;
import ru.inversion.tc.TaskContext;

import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Table;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class BatchBuilderExecutionTest {

    @Table(name = "BATCH_ITEMS")
    public static final class Row {
        private Integer id;

        public Row(int id) {
            this.id = id;
        }

        @Id
        @Column(name = "ID")
        public Integer getId() {
            return id;
        }

        public void setId(Integer id) {
            this.id = id;
        }
    }

    @Table(name = "BATCH_ITEMS")
    public static final class WideRow {
        private Integer id;
        private Integer payload;

        public WideRow(int id, int payload) {
            this.id = id;
            this.payload = payload;
        }

        @Id
        @Column(name = "ID")
        public Integer getId() {
            return id;
        }

        public void setId(Integer id) {
            this.id = id;
        }

        @Column(name = "PAYLOAD")
        public Integer getPayload() {
            return payload;
        }

        public void setPayload(Integer payload) {
            this.payload = payload;
        }
    }

    @Test
    public void builderRebuildsDefaultMapperWhenModeChanges() throws Exception {
        Jdbc jdbc = new Jdbc();
        BatchBuilder<WideRow> builder = new BatchBuilder<WideRow>(context(jdbc))
                .valueClass(WideRow.class);

        IBatchLoader<WideRow> insert = builder.build();
        insert.iterator(Arrays.asList(new WideRow(1, 2)).iterator());
        insert.execute();
        assertEquals(Arrays.asList(1, 2), jdbc.boundIndexes);
        insert.close();

        jdbc.boundIndexes.clear();
        IBatchLoader<WideRow> delete = builder
                .batchMode(BatchBuilder.BatchModeEnum.DELETE)
                .build();
        delete.iterator(Arrays.asList(new WideRow(1, 2)).iterator());
        delete.execute();
        assertEquals(Arrays.asList(1), jdbc.boundIndexes);
        delete.close();
    }

    @Test
    public void mapperErrorSkipsOneItemWithoutLosingPendingRows() throws Exception {
        Jdbc jdbc = new Jdbc();
        IBatchLoader<Row> loader = builder(jdbc)
                .batchSize(2)
                .continueOnError(true)
                .mapper((row, ps) -> {
                    if (row.id == 2)
                        throw new IllegalArgumentException("bad row");
                    bind(ps, row.id);
                })
                .build();
        List<Integer> rejected = new ArrayList<>();
        loader.addBatchListener(event -> {
            if (event.getEventType() == BatchEvent.Type.PREPARE_ITEM
                    && event.getThrowable() != null)
                rejected.add(event.getDataIndex());
        });

        loader.iterator(Arrays.asList(new Row(1), new Row(2), new Row(3)).iterator());
        loader.execute();

        assertEquals(Arrays.asList(1, 3), jdbc.executed);
        assertEquals(Arrays.asList(1), rejected);
        assertEquals(1, jdbc.batchExecutions);
        loader.close();
    }

    @Test
    public void afterPrepareListenerFailureDoesNotExecuteQueuedItem() throws Exception {
        Jdbc jdbc = new Jdbc();
        IBatchLoader<Row> loader = builder(jdbc)
                .continueOnError(true)
                .build();
        loader.addBatchListener(event -> {
            if (event.getEventType() == BatchEvent.Type.PREPARE_ITEM
                    && event.isAfter())
                throw new IllegalStateException("listener failed");
        });
        loader.iterator(Arrays.asList(new Row(1)).iterator());

        CallException error = assertThrows(CallException.class, loader::execute);

        assertEquals("listener failed", error.getCause().getMessage());
        assertEquals(0, jdbc.batchExecutions);
        assertTrue(jdbc.pending.isEmpty());
        loader.close();
    }

    @Test
    public void jdbcAddBatchFailureIsFatalEvenWhenMapperErrorsMayContinue() throws Exception {
        Jdbc jdbc = new Jdbc();
        jdbc.addFailure = new SQLException("add failed");
        IBatchLoader<Row> loader = builder(jdbc)
                .continueOnError(true)
                .build();
        loader.iterator(Arrays.asList(new Row(1), new Row(2)).iterator());

        CallException error = assertThrows(CallException.class, loader::execute);

        assertSame(jdbc.addFailure, error.getCause());
        assertEquals(0, jdbc.batchExecutions);
        assertTrue(jdbc.pending.isEmpty());
        loader.close();
    }

    @Test
    public void executeAfterListenerCannotHideJdbcFailure() throws Exception {
        Jdbc jdbc = new Jdbc();
        jdbc.executeFailure = new SQLException("batch failed");
        IBatchLoader<Row> loader = builder(jdbc).batchSize(1).build();
        loader.addBatchListener(event -> {
            if (event.getEventType() == BatchEvent.Type.EXECUTE
                    && event.isAfter())
                throw new IllegalStateException("after failed");
        });
        loader.iterator(Arrays.asList(new Row(1)).iterator());

        CallException error = assertThrows(CallException.class, loader::execute);

        assertSame(jdbc.executeFailure, error.getCause());
        assertEquals("after failed", error.getSuppressed()[0].getMessage());
        loader.close();
    }

    @Test
    public void callerTransactionCannotSilentlyContinueAfterBatchFailure() throws Exception {
        Jdbc jdbc = new Jdbc();
        jdbc.executeFailure = new SQLException("partial batch");
        IBatchLoader<Row> loader = builder(jdbc)
                .batchSize(1)
                .continueOnError(true)
                .build();
        loader.iterator(Arrays.asList(new Row(1), new Row(2)).iterator());

        CallException error = assertThrows(CallException.class, loader::execute);

        assertSame(jdbc.executeFailure, error.getCause());
        assertEquals(1, jdbc.batchExecutions);
        assertEquals(0, jdbc.commits);
        assertEquals(0, jdbc.rollbacks);
        loader.close();
    }

    @Test
    public void autoCommitCanContinueAfterReportedBatchFailure() throws Exception {
        Jdbc jdbc = new Jdbc();
        jdbc.autoCommit = true;
        jdbc.executeFailure = new SQLException("first batch failed");
        IBatchLoader<Row> loader = builder(jdbc)
                .batchSize(1)
                .continueOnError(true)
                .build();
        List<Throwable> errors = new ArrayList<>();
        loader.addBatchListener(event -> {
            if (event.getEventType() == BatchEvent.Type.PROCESS_BATCH
                    && event.getThrowable() != null) {
                errors.add(event.getThrowable());
                jdbc.executeFailure = null;
            }
        });
        loader.iterator(Arrays.asList(new Row(1), new Row(2)).iterator());

        loader.execute();

        assertEquals(Arrays.asList(2), jdbc.executed);
        assertEquals(2, jdbc.batchExecutions);
        assertEquals(1, errors.size());
        loader.close();
    }

    @Test
    public void committedModeContinuesAfterSuccessfulRollback() throws Exception {
        Jdbc jdbc = new Jdbc();
        jdbc.executeFailure = new SQLException("first batch failed");
        IBatchLoader<Row> loader = builder(jdbc)
                .batchSize(1)
                .commitBatch(true)
                .continueOnError(true)
                .build();
        loader.addBatchListener(event -> {
            if (event.getEventType() == BatchEvent.Type.PROCESS_BATCH
                    && event.getThrowable() != null)
                jdbc.executeFailure = null;
        });
        loader.iterator(Arrays.asList(new Row(1), new Row(2)).iterator());

        loader.execute();

        assertEquals(Arrays.asList(2), jdbc.executed);
        assertEquals(1, jdbc.rollbacks);
        assertEquals(1, jdbc.commits);
        loader.close();
    }

    @Test
    public void failedCommitStopsContinuationEvenIfRollbackSucceeds() throws Exception {
        Jdbc jdbc = new Jdbc();
        jdbc.commitFailure = new SQLException("commit failed");
        IBatchLoader<Row> loader = builder(jdbc)
                .batchSize(1)
                .commitBatch(true)
                .continueOnError(true)
                .build();
        loader.iterator(Arrays.asList(new Row(1), new Row(2)).iterator());

        CallException error = assertThrows(CallException.class, loader::execute);

        assertSame(jdbc.commitFailure, error.getCause());
        assertEquals(1, jdbc.batchExecutions);
        assertEquals(1, jdbc.rollbacks);
        loader.close();
    }

    @Test
    public void failedRollbackStopsContinuationAndPreservesBothErrors() throws Exception {
        Jdbc jdbc = new Jdbc();
        jdbc.executeFailure = new SQLException("batch failed");
        jdbc.rollbackFailure = new SQLException("rollback failed");
        IBatchLoader<Row> loader = builder(jdbc)
                .batchSize(1)
                .commitBatch(true)
                .continueOnError(true)
                .build();
        loader.iterator(Arrays.asList(new Row(1), new Row(2)).iterator());

        CallException error = assertThrows(CallException.class, loader::execute);

        assertSame(jdbc.executeFailure, error.getCause());
        assertSame(jdbc.rollbackFailure, error.getCause().getSuppressed()[0]);
        assertEquals(1, jdbc.batchExecutions);
        loader.close();
    }

    @Test
    public void closeAttemptsJdbcCleanupEvenWhenListenersFail() throws Exception {
        Jdbc jdbc = new Jdbc();
        IBatchLoader<Row> loader = builder(jdbc).build();
        loader.addBatchListener(event -> {
            if (event.getEventType() == BatchEvent.Type.CLOSE)
                throw new IllegalStateException(
                        event.isBefore() ? "before failed" : "after failed");
        });

        IllegalStateException error = assertThrows(
                IllegalStateException.class, loader::close);

        assertEquals("before failed", error.getMessage());
        assertEquals("after failed", error.getSuppressed()[0].getMessage());
        assertEquals(1, jdbc.closes);
        loader.close();
        assertEquals(1, jdbc.closes);
    }

    @Test
    public void closeFailureIsReportedAndCanBeRetried() throws Exception {
        Jdbc jdbc = new Jdbc();
        jdbc.closeFailure = new SQLException("close failed");
        IBatchLoader<Row> loader = builder(jdbc).build();

        CallException error = assertThrows(CallException.class, loader::close);
        assertSame(jdbc.closeFailure, error.getCause());

        jdbc.closeFailure = null;
        loader.close();
        assertEquals(2, jdbc.closes);
    }

    private static BatchBuilder<Row> builder(Jdbc jdbc) throws Exception {
        return new BatchBuilder<Row>(context(jdbc)).valueClass(Row.class);
    }

    private static TaskContext context(Jdbc jdbc) throws Exception {
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field singleton = unsafeClass.getDeclaredField("theUnsafe");
        singleton.setAccessible(true);
        Object unsafe = singleton.get(null);
        Method allocate = unsafeClass.getMethod("allocateInstance", Class.class);
        TaskContext tc = (TaskContext)allocate.invoke(unsafe, TaskContext.class);
        Field connection = TaskContext.class.getDeclaredField("connection");
        connection.setAccessible(true);
        connection.set(tc, jdbc.connection);
        return tc;
    }

    private static void bind(PreparedStatement ps, int value) {
        try {
            ps.setObject(1, value);
        }
        catch (SQLException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static final class Jdbc {
        final Connection connection;
        final List<Integer> pending = new ArrayList<>();
        final List<Integer> executed = new ArrayList<>();
        final List<Integer> boundIndexes = new ArrayList<>();
        SQLException executeFailure;
        SQLException addFailure;
        SQLException commitFailure;
        SQLException rollbackFailure;
        SQLException closeFailure;
        boolean autoCommit;
        int currentValue;
        int batchExecutions;
        int commits;
        int rollbacks;
        int closes;

        Jdbc() {
            connection = (Connection)Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[]{Connection.class},
                    (proxy, method, args) -> connectionCall(method.getName()));
        }

        private Object connectionCall(String name) throws SQLException {
            switch (name) {
                case "prepareStatement":
                    return Proxy.newProxyInstance(
                            getClass().getClassLoader(),
                            new Class<?>[]{PreparedStatement.class},
                            (proxy, method, args) ->
                                    statementCall(method.getName(), args));
                case "getAutoCommit": return autoCommit;
                case "commit":
                    commits++;
                    if (commitFailure != null) throw commitFailure;
                    return null;
                case "rollback":
                    rollbacks++;
                    if (rollbackFailure != null) throw rollbackFailure;
                    return null;
                default: throw new AssertionError("Unexpected Connection." + name);
            }
        }

        private Object statementCall(String name, Object[] args) throws SQLException {
            switch (name) {
                case "setObject":
                    boundIndexes.add((Integer)args[0]);
                    currentValue = ((Number)args[1]).intValue();
                    return null;
                case "clearParameters": return null;
                case "addBatch":
                    if (addFailure != null) throw addFailure;
                    pending.add(currentValue);
                    return null;
                case "executeBatch":
                    batchExecutions++;
                    if (executeFailure != null) throw executeFailure;
                    executed.addAll(pending);
                    int[] counts = new int[pending.size()];
                    Arrays.fill(counts, 1);
                    return counts;
                case "clearBatch":
                    pending.clear();
                    return null;
                case "getConnection": return connection;
                case "close":
                    closes++;
                    if (closeFailure != null) throw closeFailure;
                    return null;
                default: throw new AssertionError("Unexpected PreparedStatement." + name);
            }
        }
    }
}
