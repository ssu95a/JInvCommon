package ru.inversion.datacall;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import java.sql.Types;

import static javax.persistence.ParameterMode.OUT;

import static org.junit.Assert.*;

public class SQLDataCallTest {

    private static final class Work extends CallWork {
        int executions;
        int closes;
        Object input;
        RuntimeException executeFailure;
        RuntimeException closeFailure;

        Work(CallStatement statement) {
            super(null, statement, null, false);
        }

        @Override
        public CallWork execute() {
            executions++;
            input = get("a");
            if (executeFailure != null)
                throw executeFailure;
            return this;
        }

        @Override
        public void close() {
            closes++;
            if (closeFailure != null)
                throw closeFailure;
        }
    }

    private static CallStatement statement() {
        return CallBuilder.NEW().sql("update test_table set a=:a").build();
    }

    @Test
    public void legacyEventConstructorLeavesExecutionResultUnknown() {
        DataCallEvent event = new DataCallEvent(this, DataCallEvent.Type.EXECUTE, false);
        assertNull(event.getExecutionSuccess());
    }

    @Test
    public void repeatedExecutionUsesCallbacksAndEmitsPairedEvents() {
        Work work = new Work(statement());
        IDataCall call = new SQLCallBuilder.SQLDataCall(work, "test");
        List<String> events = new ArrayList<>();
        List<Boolean> results = new ArrayList<>();
        call.addDataCallListener(e -> {
            events.add(e.getEventType() + ":" + e.isBefore());
            results.add(e.getExecutionSuccess());
        });
        final int[] input = {0};
        call.setValueCallback("a", () -> ++input[0]);
        call.setProperty("tag", "value");

        assertEquals("test", call.getName());
        assertEquals("value", call.getProperty("tag"));
        assertTrue(call.hasParameter("a"));
        assertNotNull(call.getParameterMetadata("a"));
        assertSame(call, call.execute());
        assertEquals(1, work.input);
        call.execute();
        assertEquals(2, work.input);
        assertEquals(2, work.executions);
        assertEquals(Arrays.asList("EXECUTE:true", "EXECUTE:false",
                "EXECUTE:true", "EXECUTE:false"), events);
        assertEquals(Arrays.asList(null, true, null, true), results);

        call.close();
        call.close();
        assertEquals(1, work.closes);
        assertEquals("CLOSE:true", events.get(4));
        assertEquals("CLOSE:false", events.get(5));
        assertNull(results.get(4));
        assertNull(results.get(5));
    }

    @Test
    public void executionFailureIsPrimaryWhenAfterListenerFails() {
        Work work = new Work(statement());
        work.executeFailure = new CallException("JDBC failed");
        IDataCall call = new SQLCallBuilder.SQLDataCall(work, null);
        List<Boolean> results = new ArrayList<>();
        call.addDataCallListener(e -> {
            if (e.getEventType() == DataCallEvent.Type.EXECUTE) {
                results.add(e.getExecutionSuccess());
                if (e.isAfter())
                    throw new IllegalStateException("after failed");
            }
        });
        call.setParameter("a", 1);

        CallException failure = assertThrows(CallException.class, call::execute);

        assertSame(work.executeFailure, failure);
        assertEquals("after failed", failure.getSuppressed()[0].getMessage());
        assertEquals(Arrays.asList(null, false), results);
        call.close();
    }

    @Test
    public void beforeExecuteFailureDoesNotExecuteWork() {
        Work work = new Work(statement());
        IDataCall call = new SQLCallBuilder.SQLDataCall(work, null);
        List<String> events = new ArrayList<>();
        List<Boolean> results = new ArrayList<>();
        call.addDataCallListener(e -> {
            events.add(e.getEventType() + ":" + e.isBefore());
            results.add(e.getExecutionSuccess());
            if (e.getEventType() == DataCallEvent.Type.EXECUTE && e.isBefore())
                throw new IllegalStateException("before failed");
        });

        IllegalStateException failure = assertThrows(IllegalStateException.class, call::execute);

        assertEquals("before failed", failure.getMessage());
        assertEquals(0, work.executions);
        assertEquals(Arrays.asList("EXECUTE:true"), events);
        assertEquals(Arrays.asList((Boolean)null), results);
        call.close();
    }

    @Test
    public void afterExecuteFailureIsReportedWhenWorkSucceeds() {
        Work work = new Work(statement());
        IDataCall call = new SQLCallBuilder.SQLDataCall(work, null);
        call.setParameter("a", 1);
        List<Boolean> results = new ArrayList<>();
        call.addDataCallListener(e -> {
            if (e.getEventType() == DataCallEvent.Type.EXECUTE && e.isAfter()) {
                results.add(e.getExecutionSuccess());
                throw new IllegalStateException("after failed");
            }
        });

        IllegalStateException failure = assertThrows(IllegalStateException.class, call::execute);

        assertEquals("after failed", failure.getMessage());
        assertEquals(1, work.executions);
        assertEquals(Arrays.asList(true), results);
        call.close();
    }

    @Test
    public void closeAttemptsWorkAndAfterEventWhenBeforeListenerFails() {
        Work work = new Work(statement());
        IDataCall call = new SQLCallBuilder.SQLDataCall(work, null);
        List<String> events = new ArrayList<>();
        call.addDataCallListener(e -> {
            if (e.getEventType() != DataCallEvent.Type.CLOSE)
                return;
            events.add(e.isBefore() ? "before" : "after");
            throw new IllegalStateException(e.isBefore() ? "before failed" : "after failed");
        });

        IllegalStateException failure = assertThrows(IllegalStateException.class, call::close);

        assertEquals("before failed", failure.getMessage());
        assertEquals("after failed", failure.getSuppressed()[0].getMessage());
        assertEquals(Arrays.asList("before", "after"), events);
        assertEquals(1, work.closes);
        call.close();
        assertEquals(1, work.closes);
    }

    @Test
    public void closeFailureRemainsPrimaryAndCanBeRetried() {
        Work work = new Work(statement());
        work.closeFailure = new IllegalStateException("work close failed");
        IDataCall call = new SQLCallBuilder.SQLDataCall(work, null);
        call.addDataCallListener(e -> {
            if (e.getEventType() == DataCallEvent.Type.CLOSE && e.isAfter())
                throw new IllegalArgumentException("after failed");
        });

        IllegalStateException failure = assertThrows(IllegalStateException.class, call::close);

        assertSame(work.closeFailure, failure);
        assertEquals("after failed", failure.getSuppressed()[0].getMessage());
        work.closeFailure = null;
        assertThrows(IllegalArgumentException.class, call::close);
        assertEquals(2, work.closes);
        call.close();
        assertEquals(2, work.closes);
    }

    @Test
    public void listenerCannotReenterExecutionOrCloseActiveWork() {
        Work work = new Work(statement());
        IDataCall call = new SQLCallBuilder.SQLDataCall(work, null);
        call.setParameter("a", 1);
        call.addDataCallListener(e -> {
            if (e.getEventType() == DataCallEvent.Type.EXECUTE && e.isBefore()) {
                assertThrows(CallException.class, call::execute);
                assertThrows(IllegalStateException.class, call::close);
            }
        });

        call.execute();

        assertEquals(1, work.executions);
        assertEquals(0, work.closes);
        call.close();
    }

    @Test
    public void beforeCloseListenerCannotCloseWorkTwice() {
        Work work = new Work(statement());
        IDataCall call = new SQLCallBuilder.SQLDataCall(work, null);
        call.addDataCallListener(e -> {
            if (e.getEventType() == DataCallEvent.Type.CLOSE && e.isBefore())
                call.close();
        });

        call.close();

        assertEquals(1, work.closes);
    }

    @Test
    public void closedCallRejectsWorkOperationsButKeepsProperties() {
        Work work = new Work(statement());
        IDataCall call = new SQLCallBuilder.SQLDataCall(work, "test");
        call.setProperty("tag", 3);
        call.close();

        assertThrows(CallException.class, call::execute);
        assertThrows(CallException.class, () -> call.setValueCallback("a", (Supplier<Integer>)() -> 1));
        assertThrows(CallException.class, () -> call.setValueListener((id, value) -> {}));
        assertThrows(CallException.class, () -> call.getParameterMetadata("a"));
        assertThrows(CallException.class, call::getParameterIter);
        assertThrows(CallException.class, () -> call.getParameter("a"));
        assertThrows(CallException.class, () -> call.setParameter("a", 1));
        assertThrows(CallException.class, () -> call.hasParameter("a"));
        assertEquals("test", call.getName());
        assertEquals(3, call.getProperty("tag"));
    }

    @Test
    public void indexedParametersAndOutListenerUseCallWorkMetadata() {
        CallStatement statement = CallBuilder.NEW()
                .sql("{call test_proc(?, :result)}")
                .putParameter(new ParameterMetadataBuilder()
                        .name("result").mode(OUT).sqlType(Types.INTEGER).build())
                .build();
        IDataCall call = new SQLCallBuilder.SQLDataCall(new Work(statement), null);

        call.setParameter(0, 12);
        assertEquals(12, call.getParameter(0));
        assertTrue(call.hasParameter(0));
        assertNotNull(call.getParameterMetadata(0));
        call.setValueCallback(0, () -> 15);
        assertEquals(15, call.getParameter(0));
        assertTrue(call.hasParameter("result"));
        call.setValueListener("result", (id, value) -> {});
        assertThrows(CallException.class,
                () -> call.setValueListener(0, (id, value) -> {}));
        call.close();
    }
}
