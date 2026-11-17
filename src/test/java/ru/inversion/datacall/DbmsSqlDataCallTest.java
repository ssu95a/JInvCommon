package ru.inversion.datacall;

import org.junit.jupiter.api.Test;
import ru.inversion.utils.ParametersValues;

import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static javax.persistence.ParameterMode.INOUT;
import static javax.persistence.ParameterMode.OUT;
import static org.junit.jupiter.api.Assertions.*;

class DbmsSqlDataCallTest {

    private static CallStatement statement() {
        return CallBuilder.NEW()
                .sql("begin :A := :A; :RET := :A; end;")
                .putParameter(new ParameterMetadataBuilder()
                        .name("A").mode(INOUT).sqlType(Types.VARCHAR).build())
                .putParameter(new ParameterMetadataBuilder()
                        .name("RET").mode(OUT).sqlType(Types.VARCHAR)
                        .isReturnValue(true).build())
                .build();
    }

    private static final class FakeWork extends DbmsSqlWork {
        final ParametersValues values;
        int executions;
        int closes;
        RuntimeException executionFailure;
        RuntimeException closeFailure;

        FakeWork(CallStatement statement, ParametersValues values) {
            super(null, statement, values);
            this.values = values;
        }

        @Override
        public DbmsSqlWork execute(Void ignored) {
            executions++;
            if( executionFailure != null )
                throw executionFailure;
            values.setParameter("A", "out-" + values.get("A"));
            values.setParameter("RET", "return-" + executions);
            return this;
        }

        @Override
        public void close() {
            closes++;
            if( closeFailure != null )
                throw closeFailure;
            super.close();
        }
    }

    @Test
    void callbacksListenersPropertiesEventsAndReturnValue() {
        CallStatement statement = statement();
        ParametersValues values = new ParametersValues();
        FakeWork work = new FakeWork(statement, values);
        IDataCall call = new SQLCallBuilder.DbmsSqlDataCall(work, statement, values, "oracle");
        List<String> events = new ArrayList<>();
        List<String> outputs = new ArrayList<>();
        AtomicInteger callbacks = new AtomicInteger();

        call.addDataCallListener(event -> events.add(
                event.getEventType() + ":" + event.isBefore()));
        call.setValueCallback("a", () -> "in-" + callbacks.incrementAndGet());
        call.setValueListener((id, value) -> outputs.add(id + "=" + value));
        call.setValueListener("a", (id, value) -> outputs.add("individual=" + value));
        call.setProperty("tag", 17);

        assertEquals("oracle", call.getName());
        assertEquals(Integer.valueOf(17), call.getProperty("tag"));
        assertTrue(call.hasParameter("a"));
        assertNotNull(call.getParameterMetadata("a"));
        call.execute();
        call.execute();

        assertEquals(2, callbacks.get());
        assertEquals(2, work.executions);
        assertEquals("out-in-2", call.getParameter("A"));
        assertEquals("return-2", call.getReturnValue());
        assertEquals(Arrays.asList("EXECUTE:true", "EXECUTE:false", "EXECUTE:true", "EXECUTE:false"), events);
        assertEquals(Arrays.asList(
                "A=out-in-1", "individual=out-in-1", "RET=return-1",
                "A=out-in-2", "individual=out-in-2", "RET=return-2"
        ), outputs);

        call.close();
        call.close();
        assertEquals(1, work.closes);
        assertEquals("CLOSE:true", events.get(4));
        assertEquals("CLOSE:false", events.get(5));
        assertThrows(CallException.class, call::execute);
    }

    @Test
    void executionErrorRetainsAfterEventError() {
        CallStatement statement = statement();
        ParametersValues values = new ParametersValues();
        FakeWork work = new FakeWork(statement, values);
        work.executionFailure = new CallException("launcher failure");
        IDataCall call = new SQLCallBuilder.DbmsSqlDataCall(work, statement, values, null);
        call.addDataCallListener(event -> {
            if( event.getEventType() == DataCallEvent.Type.EXECUTE && event.isAfter() )
                throw new IllegalStateException("after failure");
        });

        CallException error = assertThrows(CallException.class, call::execute);
        assertSame(work.executionFailure, error);
        assertEquals("after failure", error.getSuppressed()[0].getMessage());
        call.close();
    }

    @Test
    void indexedAccessAndCallbackFailureDoNotNotifyOutputs() {
        CallStatement indexed = CallBuilder.NEW().sql("begin ? := ?; end;").build();
        ParametersValues indexedValues = new ParametersValues();
        FakeWork indexedWork = new FakeWork(indexed, indexedValues);
        IDataCall call = new SQLCallBuilder.DbmsSqlDataCall(indexedWork, indexed, indexedValues, null);
        call.setParameter(0, "zero");
        assertEquals("zero", call.getParameter(0));
        assertTrue(call.hasParameter(0));
        assertNotNull(call.getParameterMetadata(1));
        call.close();

        CallStatement named = statement();
        ParametersValues values = new ParametersValues();
        FakeWork work = new FakeWork(named, values);
        IDataCall failing = new SQLCallBuilder.DbmsSqlDataCall(work, named, values, null);
        List<String> events = new ArrayList<>();
        failing.addDataCallListener(event -> events.add(event.getEventType() + ":" + event.isBefore()));
        failing.setValueCallback("A", () -> { throw new IllegalStateException("callback failure"); });
        failing.setValueListener((id, value) -> fail("OUT listener before execution"));

        CallException error = assertThrows(CallException.class, failing::execute);
        assertEquals("callback failure", error.getCause().getMessage());
        assertEquals(0, work.executions);
        assertEquals(Arrays.asList("EXECUTE:true", "EXECUTE:false"), events);
        failing.close();
    }

    @Test
    void closeReleasesWorkEvenWhenBeforeAndAfterListenersFail() {
        CallStatement statement = statement();
        ParametersValues values = new ParametersValues();
        FakeWork work = new FakeWork(statement, values);
        work.closeFailure = new IllegalStateException("cleanup failure");
        IDataCall call = new SQLCallBuilder.DbmsSqlDataCall(work, statement, values, null);
        call.addDataCallListener(event -> {
            if( event.getEventType() == DataCallEvent.Type.CLOSE )
                throw new IllegalStateException(event.isBefore() ? "before failure" : "after failure");
        });

        IllegalStateException error = assertThrows(IllegalStateException.class, call::close);
        assertEquals("before failure", error.getMessage());
        assertEquals("cleanup failure", error.getSuppressed()[0].getMessage());
        assertEquals("after failure", error.getSuppressed()[1].getMessage());
        assertEquals(1, work.closes);
        call.close();
        assertThrows(CallException.class, call::execute);
    }
}
