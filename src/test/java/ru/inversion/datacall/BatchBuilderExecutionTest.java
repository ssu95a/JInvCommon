package ru.inversion.datacall;

import org.junit.Test;
import ru.inversion.dataset.ParametersByName;
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
import java.sql.Statement;
import java.sql.BatchUpdateException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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

    public static final class NoTableRow {
        private Integer id;

        @Id
        @Column(name = "ID")
        public Integer getId() {
            return id;
        }

        public void setId(Integer id) {
            this.id = id;
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
    public void customModeUsesPojoAndCallbackParameters() throws Exception {
        Path xml = Files.createTempFile("batch-custom-", ".xml");
        try {
            Files.write(xml, ("<sql_expr_list><expression name=\"update_item\" lang=\"SQL\">"
                    + "<script><![CDATA[UPDATE BATCH_ITEMS SET PAYLOAD = :payload "
                    + "WHERE ID = :id AND TENANT = :tenant]]></script>"
                    + "</expression></sql_expr_list>").getBytes(StandardCharsets.UTF_8));

            Jdbc jdbc = new Jdbc();
            IBatchLoader<WideRow> loader = new BatchBuilder<WideRow>(context(jdbc))
                    .valueClass(WideRow.class)
                    .batchMode(BatchBuilder.BatchModeEnum.CUSTOM)
                    .name("update_item")
                    .url(xml.toUri().toURL())
                    .callBackParameters((ParametersByName)name -> 9)
                    .build();

            loader.iterator(Arrays.asList(new WideRow(1, 2)).iterator());
            loader.execute();

            assertTrue(jdbc.sql.toUpperCase(Locale.ROOT).contains("BATCH_ITEMS"));
            assertEquals(Arrays.asList(1, 2, 3), jdbc.boundIndexes);
            assertEquals(Integer.valueOf(2), jdbc.boundValues.get(1));
            assertEquals(Integer.valueOf(1), jdbc.boundValues.get(2));
            assertEquals(Integer.valueOf(9), jdbc.boundValues.get(3));
            loader.close();
        }
        finally {
            Files.deleteIfExists(xml);
        }
    }

    @Test
    public void invalidModeFailsBeforeOpeningStatement() throws Exception {
        Jdbc jdbc = new Jdbc();

        assertThrows(UnsupportedOperationException.class,
                () -> builder(jdbc).batchMode(BatchBuilder.BatchModeEnum.CUSTOM).build());
        assertThrows(UnsupportedOperationException.class,
                () -> builder(jdbc).batchMode(BatchBuilder.BatchModeEnum.CUSTOM)
                        .url(new java.net.URL("file:/unused.xml")).build());
        CallException unsupported = assertThrows(CallException.class,
                () -> builder(jdbc).batchMode(BatchBuilder.BatchModeEnum.MERGE).build());
        assertTrue(unsupported.getCause() instanceof UnsupportedOperationException);
        assertEquals(0, jdbc.prepares);
    }

    @Test
    public void tableModeRejectsMissingTableBeforeOpeningStatement() throws Exception {
        Jdbc jdbc = new Jdbc();

        CallException error = assertThrows(CallException.class,
                () -> new BatchBuilder<NoTableRow>(context(jdbc))
                        .valueClass(NoTableRow.class).build());

        assertTrue(error.getCause().getMessage().contains("No table name"));
        assertEquals(0, jdbc.prepares);
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
    public void jdbcBindingFailureIsNotSkippedAsBadRow() throws Exception {
        Jdbc jdbc = new Jdbc();
        jdbc.bindFailure = new SQLException("binding failed");
        IBatchLoader<Row> loader = builder(jdbc).continueOnError(true).build();
        loader.iterator(Arrays.asList(new Row(1), new Row(2)).iterator());

        CallException error = assertThrows(CallException.class, loader::execute);

        assertSame(jdbc.bindFailure, error.getCause().getCause());
        assertEquals(0, jdbc.batchExecutions);
        loader.close();
    }

    @Test
    public void mapperErrorIsNotTreatedAsSkippableRowFailure() throws Exception {
        Jdbc jdbc = new Jdbc();
        IBatchLoader<Row> loader = builder(jdbc)
                .continueOnError(true)
                .mapper((row, ps) -> { throw new AssertionError("mapper failed"); })
                .build();
        loader.iterator(Arrays.asList(new Row(1)).iterator());

        CallException failure = assertThrows(CallException.class, loader::execute);

        assertTrue(failure.getCause() instanceof AssertionError);
        assertEquals(0, jdbc.batchExecutions);
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
    public void autoCommitCannotContinueAsIfRollbackUndidPartialBatch() throws Exception {
        Jdbc jdbc = new Jdbc();
        jdbc.autoCommit = true;
        jdbc.executeFailure = new SQLException("partial batch");
        IBatchLoader<Row> loader = builder(jdbc)
                .batchSize(1)
                .commitBatch(true)
                .continueOnError(true)
                .build();
        loader.iterator(Arrays.asList(new Row(1), new Row(2)).iterator());

        CallException error = assertThrows(CallException.class, loader::execute);

        assertSame(jdbc.executeFailure, error.getCause());
        assertEquals(1, jdbc.batchExecutions);
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

    @Test
    public void driverReportedExecuteFailedIsNotCommitted() throws Exception {
        Jdbc jdbc = new Jdbc();
        jdbc.countsOverride = new int[]{Statement.EXECUTE_FAILED};
        IBatchLoader<Row> loader = builder(jdbc)
                .batchSize(1)
                .commitBatch(true)
                .build();
        loader.iterator(Arrays.asList(new Row(1)).iterator());

        CallException error = assertThrows(CallException.class, loader::execute);

        assertTrue(error.getCause() instanceof BatchUpdateException);
        assertEquals(0, jdbc.commits);
        assertEquals(1, jdbc.rollbacks);
        loader.close();
    }

    @Test
    public void driverReportedSuccessWithoutCountsIsCommitted() throws Exception {
        Jdbc jdbc = new Jdbc();
        jdbc.countsOverride = new int[]{Statement.SUCCESS_NO_INFO};
        IBatchLoader<Row> loader = builder(jdbc).batchSize(1).commitBatch(true).build();
        loader.iterator(Arrays.asList(new Row(1)).iterator());

        loader.execute();

        assertEquals(1, jdbc.commits);
        assertEquals(0, jdbc.rollbacks);
        loader.close();
    }

    @Test
    public void incompleteUpdateCountsAreNotTakenForSuccess() throws Exception {
        Jdbc jdbc = new Jdbc();
        jdbc.countsOverride = new int[]{1};
        IBatchLoader<Row> loader = builder(jdbc).batchSize(2).build();
        loader.iterator(Arrays.asList(new Row(1), new Row(2)).iterator());

        CallException error = assertThrows(CallException.class, loader::execute);

        assertTrue(error.getCause() instanceof BatchUpdateException);
        assertEquals(1, jdbc.batchExecutions);
        loader.close();
    }

    @Test
    public void failureAttemptsBothJdbcCleanupOperations() throws Exception {
        Jdbc jdbc = new Jdbc();
        jdbc.executeFailure = new SQLException("execute failed");
        jdbc.clearBatchFailure = new SQLException("clear batch failed");
        IBatchLoader<Row> loader = builder(jdbc).batchSize(1).build();
        loader.iterator(Arrays.asList(new Row(1)).iterator());

        CallException error = assertThrows(CallException.class, loader::execute);

        assertSame(jdbc.executeFailure, error.getCause());
        assertTrue(jdbc.clearBatchCalls >= 2);
        assertEquals(1, jdbc.clearParametersAfterFailure);
        loader.close();
    }

    @Test
    public void clearBatchFailureRollsBackBeforeCommitAndPreventsRetry() throws Exception {
        Jdbc jdbc = new Jdbc();
        jdbc.clearBatchFailure = new SQLException("clear failed");
        IBatchLoader<Row> loader = builder(jdbc)
                .batchSize(1)
                .commitBatch(true)
                .continueOnError(true)
                .build();
        loader.iterator(Arrays.asList(new Row(1), new Row(2)).iterator());

        CallException error = assertThrows(CallException.class, loader::execute);

        assertSame(jdbc.clearBatchFailure, error.getCause());
        assertEquals(0, jdbc.commits);
        assertEquals(1, jdbc.rollbacks);
        assertEquals(1, jdbc.batchExecutions);

        jdbc.clearBatchFailure = null;
        loader.iterator(Arrays.asList(new Row(3)).iterator());
        CallException retry = assertThrows(CallException.class, loader::execute);
        assertTrue(retry.getCause().getMessage().contains("cannot be reused"));
        assertEquals(1, jdbc.batchExecutions);
        loader.close();
    }

    @Test
    public void successfulBatchClearsQueueBeforeCommit() throws Exception {
        Jdbc jdbc = new Jdbc();
        IBatchLoader<Row> loader = builder(jdbc)
                .batchSize(1)
                .commitBatch(true)
                .build();
        loader.iterator(Arrays.asList(new Row(1)).iterator());

        loader.execute();

        assertTrue(jdbc.actions.indexOf("executeBatch") < jdbc.actions.indexOf("clearBatch"));
        assertTrue(jdbc.actions.indexOf("clearBatch") < jdbc.actions.indexOf("commit"));
        loader.close();
    }

    @Test
    public void closedLoaderRejectsFurtherExecution() throws Exception {
        Jdbc jdbc = new Jdbc();
        IBatchLoader<Row> loader = builder(jdbc).build();
        loader.close();
        loader.iterator(Arrays.asList(new Row(1)).iterator());

        CallException error = assertThrows(CallException.class, loader::execute);

        assertTrue(error.getCause().getMessage().contains("closed"));
        assertEquals(0, jdbc.batchExecutions);
    }

    @Test
    public void nextExecutionStartsWithEmptyBatch() throws Exception {
        Jdbc jdbc = new Jdbc();
        IBatchLoader<Row> loader = builder(jdbc).batchSize(2).build();

        loader.iterator(Arrays.asList(new Row(1)).iterator());
        loader.execute();
        loader.iterator(Arrays.asList(new Row(2)).iterator());
        loader.execute();

        assertEquals(Arrays.asList(1, 2), jdbc.executed);
        assertEquals(2, jdbc.batchExecutions);
        loader.close();
    }

    @Test
    public void executeListenerCannotReenterOrCloseActiveLoader() throws Exception {
        Jdbc jdbc = new Jdbc();
        IBatchLoader<Row> loader = builder(jdbc).batchSize(1).build();
        loader.addBatchListener(event -> {
            if (event.getEventType() == BatchEvent.Type.EXECUTE
                    && event.isBefore()) {
                assertThrows(CallException.class, loader::execute);
                assertThrows(IllegalStateException.class, loader::close);
                assertThrows(IllegalStateException.class,
                        () -> loader.iterator(Arrays.asList(new Row(99)).iterator()));
            }
        });
        loader.iterator(Arrays.asList(new Row(1)).iterator());

        loader.execute();

        assertEquals(Arrays.asList(1), jdbc.executed);
        loader.close();
        assertEquals(1, jdbc.closes);
    }

    @Test
    public void closeListenerCanCallCloseWithoutRecursion() throws Exception {
        Jdbc jdbc = new Jdbc();
        IBatchLoader<Row> loader = builder(jdbc).build();
        loader.addBatchListener(event -> {
            if (event.getEventType() == BatchEvent.Type.CLOSE && event.isBefore()) {
                try {
                    loader.close();
                }
                catch (Exception ex) {
                    throw new AssertionError(ex);
                }
            }
        });

        loader.close();

        assertEquals(1, jdbc.closes);
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
        tc.setProperty("ru.inversion.db_prod_name", 1);
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
        final List<String> actions = new ArrayList<>();
        final Map<Integer, Integer> boundValues = new HashMap<>();
        SQLException executeFailure;
        SQLException addFailure;
        SQLException bindFailure;
        SQLException commitFailure;
        SQLException rollbackFailure;
        SQLException closeFailure;
        SQLException clearBatchFailure;
        int[] countsOverride;
        String sql;
        boolean autoCommit;
        int currentValue;
        int batchExecutions;
        int clearBatchCalls;
        int clearParametersAfterFailure;
        int prepares;
        int commits;
        int rollbacks;
        int closes;

        Jdbc() {
            connection = (Connection)Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[]{Connection.class},
                    (proxy, method, args) -> connectionCall(method.getName(), args));
        }

        private Object connectionCall(String name, Object[] args) throws SQLException {
            switch (name) {
                case "prepareStatement":
                    prepares++;
                    sql = (String)args[0];
                    return Proxy.newProxyInstance(
                            getClass().getClassLoader(),
                            new Class<?>[]{PreparedStatement.class},
                            (proxy, method, args) ->
                                    statementCall(method.getName(), args));
                case "getAutoCommit": return autoCommit;
                case "commit":
                    actions.add("commit");
                    commits++;
                    if (commitFailure != null) throw commitFailure;
                    return null;
                case "rollback":
                    actions.add("rollback");
                    rollbacks++;
                    if (rollbackFailure != null) throw rollbackFailure;
                    return null;
                default: throw new AssertionError("Unexpected Connection." + name);
            }
        }

        private Object statementCall(String name, Object[] args) throws SQLException {
            switch (name) {
                case "setObject":
                    if (bindFailure != null) throw bindFailure;
                    boundIndexes.add((Integer)args[0]);
                    boundValues.put((Integer)args[0], ((Number)args[1]).intValue());
                    currentValue = ((Number)args[1]).intValue();
                    return null;
                case "clearParameters":
                    if (executeFailure != null && batchExecutions > 0)
                        clearParametersAfterFailure++;
                    return null;
                case "addBatch":
                    if (addFailure != null) throw addFailure;
                    pending.add(currentValue);
                    return null;
                case "executeBatch":
                    actions.add("executeBatch");
                    batchExecutions++;
                    if (executeFailure != null) throw executeFailure;
                    executed.addAll(pending);
                    if (countsOverride != null) return countsOverride;
                    int[] counts = new int[pending.size()];
                    Arrays.fill(counts, 1);
                    return counts;
                case "clearBatch":
                    actions.add("clearBatch");
                    clearBatchCalls++;
                    if (clearBatchFailure != null) throw clearBatchFailure;
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
