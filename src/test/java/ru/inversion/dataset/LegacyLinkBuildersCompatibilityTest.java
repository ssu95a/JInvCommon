package ru.inversion.dataset;

import org.junit.Test;
import ru.inversion.datacall.DataCallEvent;
import ru.inversion.datacall.DataCallLinkBuilder;
import ru.inversion.datacall.IDataCall;
import ru.inversion.datacall.IDataCallListener;
import ru.inversion.tds.SQLTreeDataSet;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.Assert.*;

public class LegacyLinkBuildersCompatibilityTest {

    private static final class Row {
        final int id;
        final String kind;

        Row(int id, String kind) {
            this.id = id;
            this.kind = kind;
        }
    }

    private static final class Source {
        final IDataSet<Row> dataSet;
        final List<IDataSetNavigationListener<Row>> navigation = new ArrayList<>();
        final List<IDataSetRowListener<Row>> rows = new ArrayList<>();
        final List<IDataSetListener> dataSetListeners = new ArrayList<>();
        Row current;
        RuntimeException removeNavigationFailure;

        @SuppressWarnings("unchecked")
        Source(Row current) {
            this.current = current;
            dataSet = (IDataSet<Row>) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[]{IDataSet.class},
                    (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "getCurrentRow": return this.current;
                            case "addNavigationListener":
                                navigation.add((IDataSetNavigationListener<Row>) args[0]);
                                return null;
                            case "removeNavigationListener":
                                navigation.remove(args[0]);
                                if (removeNavigationFailure != null) throw removeNavigationFailure;
                                return null;
                            case "addRowListener":
                                rows.add((IDataSetRowListener<Row>) args[0]);
                                return null;
                            case "removeRowListener":
                                rows.remove(args[0]);
                                return null;
                            case "addDataSetListener":
                                dataSetListeners.add((IDataSetListener) args[0]);
                                return null;
                            case "removeDataSetListener":
                                dataSetListeners.remove(args[0]);
                                return null;
                            default: throw new AssertionError("Unexpected IDataSet." + method.getName());
                        }
                    });
        }

        void navigate(Row next) {
            Row previous = current;
            current = next;
            DataSetNavigationEvent<Row> event =
                    new DataSetNavigationEvent<>(dataSet, previous, next, 0, 0);
            for (IDataSetNavigationListener<Row> listener : new ArrayList<>(navigation))
                listener.navigated(event);
        }

        void rowOperation(DataSetRowEvent.RowOperationEnum operation, Row next) {
            Row previous = current;
            current = next;
            DataSetRowEvent<Row> event =
                    new DataSetRowEvent<>(dataSet, operation, previous, next, 0, 1);
            for (IDataSetRowListener<Row> listener : new ArrayList<>(rows))
                listener.rowOperation(event);
        }

        void closeBefore() {
            DataSetEvent event = new DataSetEvent(dataSet, DataSetEvent.DataSetEventType.CLOSE, true);
            for (IDataSetListener listener : new ArrayList<>(dataSetListeners))
                listener.dataSetChanged(event);
        }
    }

    private static final class SqlSource extends SQLDataSet<Row> {
        void useReader(Runnable onClose) {
            dataReader = (ISQLDataReader<Row>) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[]{ISQLDataReader.class},
                    (proxy, method, args) -> {
                        if ("close".equals(method.getName())) {
                            onClose.run();
                            return null;
                        }
                        throw new AssertionError("Unexpected ISQLDataReader." + method.getName());
                    });
        }
    }

    private static final class DataDestination {
        final ISQLDataSet<Row> dataSet;
        final Map<String, Object> parameters = new HashMap<>();
        final List<Integer> removedFilters = new ArrayList<>();
        String filter;
        int queries;
        int clears;
        boolean failQuery;
        RuntimeException removeFailure;

        @SuppressWarnings("unchecked")
        DataDestination() {
            dataSet = (ISQLDataSet<Row>) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[]{ISQLDataSet.class},
                    (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "setFilter":
                                filter = (String) args[0];
                                assertEquals(Boolean.TRUE, args[1]);
                                assertEquals(Boolean.TRUE, args[2]);
                                return 31;
                            case "removeFilter":
                                removedFilters.add((Integer) args[0]);
                                if (removeFailure != null) throw removeFailure;
                                return filter;
                            case "setParameter":
                                parameters.put((String) args[0], args[1]);
                                return null;
                            case "executeQuery":
                                if (failQuery) throw new IllegalStateException("query failed");
                                queries++;
                                return null;
                            case "clear":
                                clears++;
                                return null;
                            default: throw new AssertionError("Unexpected ISQLDataSet." + method.getName());
                        }
                    });
        }
    }

    private static final class TreeDestination extends SQLTreeDataSet<Row> {
        final Map<String,Object> parameters = new HashMap<>();
        final List<Integer> removedFilters = new ArrayList<>();
        String filter;
        int queries;

        @Override public void setParameter(String name, Object value) {
            parameters.put(name, value);
        }
        @Override public int setFilter(String sql, boolean fixed, boolean append) {
            filter = sql;
            return 41;
        }
        @Override public String removeFilter(int id) {
            removedFilters.add(id);
            return filter;
        }
        @Override public void executeQuery() { queries++; }
        @Override public void clear() { parameters.clear(); }
    }

    private static final class CallDestination {
        final IDataCall dataCall;
        final Map<String, Supplier<?>> callbacks = new HashMap<>();
        final List<Map<String, Object>> executions = new ArrayList<>();
        int closes;
        boolean failExecute;
        boolean failClose;
        Runnable onExecute;
        RuntimeException callbackFailure;

        CallDestination() {
            dataCall = (IDataCall) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[]{IDataCall.class},
                    (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "setValueCallback":
                                if (callbackFailure != null) throw callbackFailure;
                                callbacks.put((String) args[0], (Supplier<?>) args[1]);
                                return null;
                            case "execute":
                                if (failExecute) throw new IllegalStateException("dependent failed");
                                if (onExecute != null) onExecute.run();
                                Map<String, Object> values = new HashMap<>();
                                callbacks.forEach((name, callback) -> values.put(name, callback.get()));
                                executions.add(values);
                                return proxy;
                            case "close":
                                closes++;
                                if (failClose) throw new IllegalStateException("dependent close failed");
                                return null;
                            default: throw new AssertionError("Unexpected IDataCall." + method.getName());
                        }
                    });
        }
    }

    private static final class CallSource {
        final IDataCall dataCall;
        final Map<String,Object> outputs = new HashMap<>();
        final List<IDataCallListener> listeners = new ArrayList<>();
        boolean failExecute;
        RuntimeException removeListenerFailure;

        CallSource() {
            dataCall = (IDataCall)Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[]{IDataCall.class}, (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "addDataCallListener": listeners.add((IDataCallListener)args[0]); return null;
                            case "removeDataCallListener":
                                listeners.remove(args[0]);
                                if (removeListenerFailure != null) throw removeListenerFailure;
                                return null;
                            case "getParameter": return outputs.get(args[0]);
                            case "execute":
                                fire(new DataCallEvent(proxy, DataCallEvent.Type.EXECUTE, true));
                                fire(new DataCallEvent(proxy, DataCallEvent.Type.EXECUTE, false, !failExecute));
                                if (failExecute) throw new IllegalStateException("call failed");
                                return proxy;
                            case "close":
                                fire(new DataCallEvent(proxy, DataCallEvent.Type.CLOSE, true));
                                fire(new DataCallEvent(proxy, DataCallEvent.Type.CLOSE, false));
                                return null;
                            default: throw new AssertionError("Unexpected IDataCall." + method.getName());
                        }
                    });
        }

        void fire(DataCallEvent event) {
            for (IDataCallListener listener : new ArrayList<>(listeners))
                listener.dataCallChanged(event);
        }
    }

    @Test
    public void dataCallToDataSetUpdatesOnlyAfterConfirmedSuccess() throws Exception {
        CallSource source = new CallSource();
        DataDestination destination = new DataDestination();
        IDataLink<IDataCall,ISQLDataSet<Row>> link =
                new DLBuilder<IDataCall,ISQLDataSet<Row>,IDataCall,Object>()
                        .source(source.dataCall).destination(destination.dataSet)
                        .joinColumn(call -> call.getParameter("ID"), "PARENT_ID")
                        .joinColumn(call -> call.getParameter("KIND"), "KIND", false).link();

        assertEquals(1, source.listeners.size());
        assertEquals(0, destination.queries);
        assertTrue(destination.filter.contains("PARENT_ID= :PARENT_ID"));
        assertFalse(destination.filter.contains("KIND"));

        source.outputs.put("ID", 1);
        source.outputs.put("KIND", "A");
        source.dataCall.execute();
        assertEquals(1, destination.queries);
        assertEquals(Integer.valueOf(1), destination.parameters.get("PARENT_ID"));
        assertEquals("A", destination.parameters.get("KIND"));

        source.outputs.put("ID", 2);
        source.failExecute = true;
        try {
            source.dataCall.execute();
            fail("Call should fail");
        } catch (IllegalStateException expected) {
            assertEquals("call failed", expected.getMessage());
        }
        source.failExecute = false;
        source.fire(new DataCallEvent(source.dataCall, DataCallEvent.Type.EXECUTE, false));
        assertEquals(1, destination.queries);
        assertEquals(Integer.valueOf(1), destination.parameters.get("PARENT_ID"));

        source.dataCall.execute();
        assertEquals(2, destination.queries);
        assertEquals(Integer.valueOf(2), destination.parameters.get("PARENT_ID"));
        link.setAutoRequery(() -> false);
        source.outputs.put("ID", 3);
        source.dataCall.execute();
        assertEquals(2, destination.queries);
        assertEquals(Integer.valueOf(3), destination.parameters.get("PARENT_ID"));

        ((AutoCloseable)link).close();
        ((AutoCloseable)link).close();
        assertTrue(source.listeners.isEmpty());
        assertEquals(Integer.valueOf(31), destination.removedFilters.get(0));
        source.outputs.put("ID", 4);
        source.dataCall.execute();
        assertEquals(Integer.valueOf(3), destination.parameters.get("PARENT_ID"));
    }

    @Test
    public void closingSourceCallReleasesItsDataSetLink() {
        CallSource source = new CallSource();
        DataDestination destination = new DataDestination();
        new DLBuilder<IDataCall,ISQLDataSet<Row>,IDataCall,Object>()
                .source(source.dataCall).destination(destination.dataSet)
                .joinColumn(call -> call.getParameter("ID"), "PARENT_ID").link();

        source.dataCall.close();

        assertTrue(source.listeners.isEmpty());
        assertEquals(1, destination.removedFilters.size());
    }

    @Test
    public void failedCallToDataSetFilterRemovalDetachesLink() throws Exception {
        CallSource source = new CallSource();
        DataDestination destination = new DataDestination();
        IDataLink<IDataCall,ISQLDataSet<Row>> link =
                new DLBuilder<IDataCall,ISQLDataSet<Row>,IDataCall,Object>()
                        .source(source.dataCall).destination(destination.dataSet)
                        .joinColumn(call -> call.getParameter("ID"), "PARENT_ID").link();
        IllegalStateException failure = new IllegalStateException("filter failed");
        destination.removeFailure = failure;

        try {
            ((AutoCloseable) link).close();
            fail("Close should fail");
        } catch (IllegalStateException actual) {
            assertSame(failure, actual);
        }
        assertNull(link.getSource());
        assertNull(link.getDestination());
        assertTrue(source.listeners.isEmpty());
        ((AutoCloseable) link).close();
        assertEquals(1, destination.removedFilters.size());
    }

    @Test
    public void failedDetailQueryCanBeRetriedOnNextCall() throws Exception {
        CallSource source = new CallSource();
        DataDestination destination = new DataDestination();
        IDataLink<IDataCall,ISQLDataSet<Row>> link =
                new DLBuilder<IDataCall,ISQLDataSet<Row>,IDataCall,Object>()
                        .source(source.dataCall).destination(destination.dataSet)
                        .joinColumn(call -> call.getParameter("ID"), "PARENT_ID").link();
        source.outputs.put("ID", 1);
        destination.failQuery = true;

        try {
            source.dataCall.execute();
            fail("Detail query should fail");
        } catch (RuntimeException expected) {
            assertEquals(1, source.listeners.size());
            assertTrue(destination.removedFilters.isEmpty());
        }

        destination.failQuery = false;
        source.dataCall.execute();
        assertEquals(1, destination.queries);
        ((AutoCloseable)link).close();
    }

    @Test
    public void dataCallToTreeUpdatesOnlyAfterSuccessfulExecution() throws Exception {
        CallSource source = new CallSource();
        TreeDestination destination = new TreeDestination();
        IDataLink<IDataCall,SQLTreeDataSet<Row>> link =
                new DLBuilder<IDataCall,SQLTreeDataSet<Row>,IDataCall,Object>()
                        .source(source.dataCall).destination(destination)
                        .joinColumn(call -> call.getParameter("ID"), "PARENT_ID")
                        .joinColumn(call -> call.getParameter("EXTRA"), "EXTRA", false).link();

        assertEquals(0, destination.queries);
        assertTrue(destination.filter.contains("PARENT_ID= :PARENT_ID"));
        assertFalse(destination.filter.contains("EXTRA"));
        source.outputs.put("ID", 1);
        source.outputs.put("EXTRA", "A");
        source.dataCall.execute();
        assertEquals(1, destination.queries);
        assertEquals(Integer.valueOf(1), destination.parameters.get("PARENT_ID"));
        assertEquals("A", destination.parameters.get("EXTRA"));

        source.outputs.put("ID", 2);
        source.failExecute = true;
        try {
            source.dataCall.execute();
            fail("Call should fail");
        } catch (IllegalStateException expected) {
            assertEquals(1, destination.queries);
            assertEquals(Integer.valueOf(1), destination.parameters.get("PARENT_ID"));
        }
        source.failExecute = false;
        source.fire(new DataCallEvent(source.dataCall, DataCallEvent.Type.EXECUTE, false));
        assertEquals(1, destination.queries);
        source.dataCall.execute();
        assertEquals(2, destination.queries);
        assertEquals(Integer.valueOf(2), destination.parameters.get("PARENT_ID"));

        link.setAutoRequery(() -> false);
        source.outputs.put("ID", 3);
        source.dataCall.execute();
        assertEquals(2, destination.queries);
        assertEquals(Integer.valueOf(3), destination.parameters.get("PARENT_ID"));

        source.dataCall.close();
        ((AutoCloseable)link).close();
        assertTrue(source.listeners.isEmpty());
        assertEquals(Integer.valueOf(41), destination.removedFilters.get(0));
        source.outputs.put("ID", 4);
        source.dataCall.execute();
        assertEquals(2, destination.queries);
    }

    @Test
    public void dataCallToCallChainsOnlyConfirmedSuccessAndReleasesListeners() throws Exception {
        CallSource source = new CallSource();
        CallDestination destination = new CallDestination();
        IDataLink<IDataCall,IDataCall> link =
                new DLBuilder<IDataCall,IDataCall,IDataCall,Object>()
                        .source(source.dataCall).destination(destination.dataCall)
                        .joinColumn(call -> call.getParameter("ID"), "PARENT_ID")
                        .joinColumn(call -> call.getParameter("KIND"), "KIND").link();

        assertTrue(destination.executions.isEmpty());
        source.outputs.put("ID", 1);
        source.outputs.put("KIND", "A");
        source.dataCall.execute();
        assertEquals(Integer.valueOf(1), destination.executions.get(0).get("PARENT_ID"));
        assertEquals("A", destination.executions.get(0).get("KIND"));

        source.outputs.put("ID", 2);
        source.failExecute = true;
        try {
            source.dataCall.execute();
            fail("Call should fail");
        } catch (IllegalStateException expected) {
            assertEquals(1, destination.executions.size());
        }
        source.failExecute = false;
        source.fire(new DataCallEvent(source.dataCall, DataCallEvent.Type.EXECUTE, false));
        assertEquals(1, destination.executions.size());
        link.setAutoRequery(() -> false);
        source.dataCall.execute();
        assertEquals(1, destination.executions.size());
        assertEquals(Integer.valueOf(2), destination.callbacks.get("PARENT_ID").get());
        link.setAutoRequery(() -> true);
        source.dataCall.execute();
        assertEquals(Integer.valueOf(2), destination.executions.get(1).get("PARENT_ID"));

        ((AutoCloseable)link).close();
        ((AutoCloseable)link).close();
        assertTrue(source.listeners.isEmpty());
        assertNull(destination.callbacks.get("PARENT_ID").get());
        source.dataCall.execute();
        assertEquals(2, destination.executions.size());
        assertEquals(0, destination.closes);
    }

    @Test
    public void sourceCallCloseAlsoClosesDependentCall() {
        CallSource source = new CallSource();
        CallDestination destination = new CallDestination();
        new DLBuilder<IDataCall,IDataCall,IDataCall,Object>()
                .source(source.dataCall).destination(destination.dataCall)
                .joinColumn(call -> call.getParameter("ID"), "PARENT_ID").link();

        source.dataCall.close();

        assertEquals(1, destination.closes);
        assertTrue(source.listeners.isEmpty());
        assertNull(destination.callbacks.get("PARENT_ID").get());
    }

    @Test
    public void failedDependentCallCanBeRetriedWithoutRelinking() throws Exception {
        CallSource source = new CallSource();
        CallDestination destination = new CallDestination();
        IDataLink<IDataCall,IDataCall> link =
                new DLBuilder<IDataCall,IDataCall,IDataCall,Object>()
                        .source(source.dataCall).destination(destination.dataCall)
                        .joinColumn(call -> call.getParameter("ID"), "PARENT_ID").link();
        destination.failExecute = true;
        source.outputs.put("ID", 1);

        try {
            source.dataCall.execute();
            fail("Dependent call should fail");
        } catch (RuntimeException expected) {
            assertEquals(1, source.listeners.size());
            assertTrue(destination.executions.isEmpty());
        }

        destination.failExecute = false;
        source.dataCall.execute();
        assertEquals(Integer.valueOf(1), destination.executions.get(0).get("PARENT_ID"));
        ((AutoCloseable)link).close();
    }

    @Test
    public void failedDependentCloseStillReleasesSourceListener() {
        CallSource source = new CallSource();
        CallDestination destination = new CallDestination();
        destination.failClose = true;
        new DLBuilder<IDataCall,IDataCall,IDataCall,Object>()
                .source(source.dataCall).destination(destination.dataCall)
                .joinColumn(call -> call.getParameter("ID"), "PARENT_ID").link();

        try {
            source.dataCall.close();
            fail("Dependent close should fail");
        } catch (IllegalStateException expected) {
            assertTrue(source.listeners.isEmpty());
            assertEquals(1, destination.closes);
            assertNull(destination.callbacks.get("PARENT_ID").get());
        }
    }

    @Test
    public void failedCallListenerRemovalStillClosesDependentCall() {
        CallSource source = new CallSource();
        CallDestination destination = new CallDestination();
        IDataLink<IDataCall,IDataCall> link =
                new DLBuilder<IDataCall,IDataCall,IDataCall,Object>()
                        .source(source.dataCall).destination(destination.dataCall)
                        .joinColumn(call -> call.getParameter("ID"), "PARENT_ID").link();
        IllegalStateException failure = new IllegalStateException("unsubscribe failed");
        source.removeListenerFailure = failure;

        try {
            source.dataCall.close();
            fail("Close should fail");
        } catch (IllegalStateException actual) {
            assertSame(failure, actual);
        }
        assertEquals(1, destination.closes);
        assertTrue(source.listeners.isEmpty());
        assertNull(destination.callbacks.get("PARENT_ID").get());
        assertNull(link.getSource());
    }

    @Test
    public void reentrantCallChainFailsWithoutRecursingForever() throws Exception {
        CallSource source = new CallSource();
        CallDestination destination = new CallDestination();
        IDataLink<IDataCall,IDataCall> link =
                new DLBuilder<IDataCall,IDataCall,IDataCall,Object>()
                        .source(source.dataCall).destination(destination.dataCall)
                        .joinColumn(call -> call.getParameter("ID"), "PARENT_ID").link();
        destination.onExecute = () -> source.fire(
                new DataCallEvent(source.dataCall, DataCallEvent.Type.EXECUTE, false, true));

        try {
            source.dataCall.execute();
            fail("Reentrant chain should fail");
        } catch (RuntimeException expected) {
            assertTrue(destination.executions.isEmpty());
            assertEquals(1, source.listeners.size());
        }

        destination.onExecute = null;
        source.dataCall.execute();
        assertEquals(1, destination.executions.size());
        ((AutoCloseable)link).close();
    }

    @Test
    public void dataCallCannotLinkToItself() {
        CallSource source = new CallSource();
        try {
            new DLBuilder<IDataCall,IDataCall,IDataCall,Object>()
                    .source(source.dataCall).destination(source.dataCall)
                    .joinColumn(call -> call.getParameter("ID"), "PARENT_ID").link();
            fail("Self link should be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(source.listeners.isEmpty());
        }
    }

    @Test
    public void dataLinkBuilderPreservesFilterParametersInitializerAndClose() throws Exception {
        Source source = new Source(new Row(1, "A"));
        DataDestination destination = new DataDestination();

        IDataSetLink<Row> link = new DataLinkBuilder<Row>()
                .source(source.dataSet).destination(destination.dataSet)
                .joinColumn(row -> row.id, "PARENT_ID")
                .joinColumn(row -> row.kind, "KIND", false)
                .autoRequery(false).build();

        assertSame(source.dataSet, link.getSource());
        assertSame(destination.dataSet, link.getDestination());
        assertTrue(destination.filter.contains("PARENT_ID=:PARENT_ID"));
        assertFalse(destination.filter.contains("KIND"));
        assertEquals(Integer.valueOf(1), destination.parameters.get("PARENT_ID"));
        assertEquals("A", destination.parameters.get("KIND"));
        assertEquals(0, destination.queries);
        assertFalse(link.isAutoRequery());

        Map<String, Object> initialValues = new HashMap<>();
        link.initDestinationRow((value, column) -> initialValues.put(column, value));
        assertEquals(Integer.valueOf(1), initialValues.get("PARENT_ID"));
        assertEquals("A", initialValues.get("KIND"));

        link.setAutoRequery(true);
        source.navigate(new Row(2, "B"));
        source.rowOperation(DataSetRowEvent.RowOperationEnum.REFRESH, new Row(3, "C"));
        assertEquals(2, destination.queries);
        assertEquals(Integer.valueOf(3), destination.parameters.get("PARENT_ID"));
        assertEquals("C", destination.parameters.get("KIND"));

        link.setAutoRequery(false);
        source.navigate(new Row(4, "D"));
        assertEquals(2, destination.queries);
        assertEquals(Integer.valueOf(4), destination.parameters.get("PARENT_ID"));
        source.navigate(null);
        assertEquals(1, destination.clears);
        assertNull(destination.parameters.get("PARENT_ID"));
        assertNull(destination.parameters.get("KIND"));

        link.setAutoRequery((Supplier<Boolean>) null);
        assertTrue(link.isAutoRequery());

        link.close();
        assertEquals(Integer.valueOf(31), destination.removedFilters.get(0));
        assertEquals(1, destination.removedFilters.size());
        assertTrue(source.navigation.isEmpty());
        assertTrue(source.rows.isEmpty());
        source.navigate(new Row(5, "E"));
        assertEquals(2, destination.queries);
    }

    @Test
    public void sqlDataSetCloseReleasesItsDataSetLinkBeforeReaderClose() throws Exception {
        SqlSource source = new SqlSource();
        DataDestination destination = new DataDestination();
        int[] readerCloses = {0};
        AtomicReference<IDataSetLink<Row>> link = new AtomicReference<>();
        source.useReader(() -> {
            assertNull(link.get().getSource());
            assertNull(link.get().getDestination());
            readerCloses[0]++;
        });
        link.set(new DataLinkBuilder<Row>()
                .source(source).destination(destination.dataSet)
                .joinColumn(row -> row.id, "PARENT_ID").build());

        source.close();

        assertEquals(1, readerCloses[0]);
        assertEquals(1, destination.removedFilters.size());
        assertEquals(Integer.valueOf(31), destination.removedFilters.get(0));
        assertEquals(0, destination.clears);
        link.get().close();
        assertEquals(1, destination.removedFilters.size());
    }

    @Test
    public void sqlDataSetCloseWithoutReaderKeepsLinkUntilExplicitClose() throws Exception {
        SqlSource source = new SqlSource();
        DataDestination destination = new DataDestination();
        IDataSetLink<Row> link = new DataLinkBuilder<Row>()
                .source(source).destination(destination.dataSet)
                .joinColumn(row -> row.id, "PARENT_ID").build();

        source.close();

        assertSame(source, link.getSource());
        assertTrue(destination.removedFilters.isEmpty());
        link.close();
        assertEquals(1, destination.removedFilters.size());
    }

    @Test
    public void failedFilterRemovalStillDetachesDataSetLink() throws Exception {
        Source source = new Source(new Row(1, "A"));
        DataDestination destination = new DataDestination();
        IDataSetLink<Row> link = new DataLinkBuilder<Row>()
                .source(source.dataSet).destination(destination.dataSet)
                .joinColumn(row -> row.id, "PARENT_ID").build();
        IllegalStateException failure = new IllegalStateException("filter removal failed");
        destination.removeFailure = failure;

        try {
            link.close();
            fail("Filter removal should fail");
        } catch (IllegalStateException actual) {
            assertSame(failure, actual);
        }

        assertNull(link.getSource());
        assertNull(link.getDestination());
        assertTrue(source.navigation.isEmpty());
        assertTrue(source.rows.isEmpty());
        assertEquals(1, destination.removedFilters.size());
        link.close();
        assertEquals(1, destination.removedFilters.size());
    }

    @Test
    public void failedDataSetLinkUnsubscribeStillRemovesFilter() throws Exception {
        Source source = new Source(null);
        DataDestination destination = new DataDestination();
        IDataSetLink<Row> link = new DataLinkBuilder<Row>()
                .source(source.dataSet).destination(destination.dataSet)
                .joinColumn(row -> row.id, "PARENT_ID").build();
        IllegalStateException failure = new IllegalStateException("unsubscribe failed");
        source.removeNavigationFailure = failure;

        try {
            link.close();
            fail("Close should fail");
        } catch (IllegalStateException actual) {
            assertSame(failure, actual);
        }
        assertNull(link.getSource());
        assertNull(link.getDestination());
        assertTrue(source.navigation.isEmpty());
        assertTrue(source.rows.isEmpty());
        assertEquals(1, destination.removedFilters.size());
    }

    @Test
    public void failedFilterRemovalDuringSqlDataSetCloseStillClosesReader() throws Exception {
        SqlSource source = new SqlSource();
        DataDestination destination = new DataDestination();
        int[] readerCloses = {0};
        source.useReader(() -> readerCloses[0]++);
        IDataSetLink<Row> link = new DataLinkBuilder<Row>()
                .source(source).destination(destination.dataSet)
                .joinColumn(row -> row.id, "PARENT_ID").build();
        IllegalStateException failure = new IllegalStateException("filter removal failed");
        destination.removeFailure = failure;

        try {
            source.close();
            fail("Filter removal should fail");
        } catch (IllegalStateException actual) {
            assertSame(failure, actual);
        }

        assertEquals(1, readerCloses[0]);
        assertNull(link.getSource());
        assertNull(link.getDestination());
        assertEquals(1, destination.removedFilters.size());
        link.close();
        assertEquals(1, destination.removedFilters.size());
    }

    @Test
    public void nonSqlDataSetKeepsExplicitLinkLifecycle() throws Exception {
        Source source = new Source(new Row(1, "A"));
        DataDestination destination = new DataDestination();
        IDataSetLink<Row> link = new DataLinkBuilder<Row>()
                .source(source.dataSet).destination(destination.dataSet)
                .joinColumn(row -> row.id, "PARENT_ID").build();

        assertTrue(source.dataSetListeners.isEmpty());
        source.closeBefore();
        assertSame(source.dataSet, link.getSource());
        assertTrue(destination.removedFilters.isEmpty());

        link.close();
        assertEquals(1, destination.removedFilters.size());
        assertTrue(source.navigation.isEmpty());
        assertTrue(source.rows.isEmpty());
    }

    @Test
    public void dlBuilderKeepsTheLegacyDataSetLinkReturnContract() throws Exception {
        Source source = new Source(new Row(1, "A"));
        DataDestination destination = new DataDestination();

        IDataLink<IDataSet<Row>, ISQLDataSet<Row>> link =
                new DLBuilder<IDataSet<Row>, ISQLDataSet<Row>, Row, Integer>()
                        .source(source.dataSet).destination(destination.dataSet)
                        .joinColumn(row -> row.id, "PARENT_ID").link();

        assertTrue(((Object) link) instanceof IDataSetLink);
        assertEquals(1, destination.queries);
        ((AutoCloseable) link).close();
        assertEquals(Integer.valueOf(31), destination.removedFilters.get(0));
    }

    @Test
    public void dlBuilderSupportsUnfilteredParametersAndRequeryControl() throws Exception {
        Source source = new Source(new Row(1, "A"));
        DataDestination destination = new DataDestination();

        IDataLink<IDataSet<Row>, ISQLDataSet<Row>> link =
                new DLBuilder<IDataSet<Row>, ISQLDataSet<Row>, Row, Object>()
                        .source(source.dataSet).destination(destination.dataSet)
                        .joinColumn(row -> row.id, "PARENT_ID")
                        .joinColumn(row -> row.kind, "KIND", false)
                        .requeryIf(() -> false).link();

        assertTrue(((Object) link) instanceof IDataSetLink);
        assertTrue(destination.filter.contains("PARENT_ID=:PARENT_ID"));
        assertFalse(destination.filter.contains("KIND"));
        assertEquals(Integer.valueOf(1), destination.parameters.get("PARENT_ID"));
        assertEquals("A", destination.parameters.get("KIND"));
        assertEquals(0, destination.queries);
        ((AutoCloseable) link).close();
    }

    @Test
    public void unfilteredDataSetLinkDoesNotRemoveAnUnownedFilter() throws Exception {
        Source source = new Source(new Row(1, "A"));
        DataDestination destination = new DataDestination();
        IDataSetLink<Row> link = new DataLinkBuilder<Row>()
                .source(source.dataSet).destination(destination.dataSet)
                .joinColumn(row -> row.id, "EXTRA", false).build();

        assertNull(destination.filter);
        assertEquals(Integer.valueOf(1), destination.parameters.get("EXTRA"));
        link.close();
        assertTrue(destination.removedFilters.isEmpty());
    }

    @Test
    public void closingOneLinkDoesNotChangeAnotherBuiltFromTheSameBuilder() throws Exception {
        Source source = new Source(new Row(1, "A"));
        DataDestination destination = new DataDestination();
        DataLinkBuilder<Row> builder = new DataLinkBuilder<Row>()
                .source(source.dataSet).destination(destination.dataSet)
                .joinColumn(row -> row.id, "PARENT_ID");

        IDataSetLink<Row> first = builder.build();
        IDataSetLink<Row> second = builder.build();
        first.close();
        source.navigate(new Row(2, "B"));

        assertEquals(Integer.valueOf(2), destination.parameters.get("PARENT_ID"));
        assertEquals(3, destination.queries);
        second.close();
        assertTrue(source.navigation.isEmpty());
        assertTrue(source.rows.isEmpty());
    }

    @Test
    public void failedInitialDataSetQueryReleasesLinkResources() {
        Source source = new Source(new Row(1, "A"));
        DataDestination destination = new DataDestination();
        destination.failQuery = true;

        try {
            new DLBuilder<IDataSet<Row>, ISQLDataSet<Row>, Row, Integer>()
                    .source(source.dataSet).destination(destination.dataSet)
                    .joinColumn(row -> row.id, "PARENT_ID").link();
            fail("Initial query should fail");
        } catch (RuntimeException expected) {
            assertEquals(Integer.valueOf(31), destination.removedFilters.get(0));
            assertTrue(source.navigation.isEmpty());
            assertTrue(source.rows.isEmpty());
        }
    }

    @Test
    public void dataCallLinkPreservesLazyInputsAndNavigationExecution() {
        Source source = new Source(new Row(1, "A"));
        CallDestination destination = new CallDestination();

        IDataLink link = DataCallLinkBuilder.NEW()
                .source(source.dataSet).destination(destination.dataCall)
                .join((Row row) -> row.id, "PARENT_ID")
                .join((Row row) -> row.kind, "KIND")
                .build();

        assertSame(source.dataSet, link.getSource());
        assertSame(destination.dataCall, link.getDestination());
        assertTrue(destination.executions.isEmpty());
        assertEquals(Integer.valueOf(1), destination.callbacks.get("PARENT_ID").get());

        source.navigate(new Row(2, "B"));
        source.rowOperation(DataSetRowEvent.RowOperationEnum.REFRESH, new Row(3, "C"));
        assertEquals(2, destination.executions.size());
        assertEquals(Integer.valueOf(2), destination.executions.get(0).get("PARENT_ID"));
        assertEquals("B", destination.executions.get(0).get("KIND"));
        assertEquals(Integer.valueOf(3), destination.executions.get(1).get("PARENT_ID"));

        source.rowOperation(DataSetRowEvent.RowOperationEnum.UPDATE, new Row(4, "D"));
        assertEquals(2, destination.executions.size());
        link.setAutoRequery(() -> false);
        source.navigate(new Row(5, "E"));
        assertEquals(2, destination.executions.size());
        assertEquals(Integer.valueOf(5), destination.callbacks.get("PARENT_ID").get());
        source.navigate(null);
        assertNull(destination.callbacks.get("PARENT_ID").get());

        source.closeBefore();
        assertEquals(1, destination.closes);
        assertTrue(source.navigation.isEmpty());
        assertTrue(source.rows.isEmpty());
        assertTrue(source.dataSetListeners.isEmpty());
    }

    @Test
    public void dataCallLinkCanBeClosedWithoutClosingDestination() throws Exception {
        Source source = new Source(new Row(1, "A"));
        CallDestination destination = new CallDestination();
        IDataLink link = DataCallLinkBuilder.NEW()
                .source(source.dataSet).destination(destination.dataCall)
                .join((Row row) -> row.id, "PARENT_ID").build();

        ((AutoCloseable)link).close();
        ((AutoCloseable)link).close();
        source.navigate(new Row(2, "B"));
        source.closeBefore();

        assertTrue(destination.executions.isEmpty());
        assertEquals(0, destination.closes);
        assertNull(destination.callbacks.get("PARENT_ID").get());
        assertTrue(source.navigation.isEmpty());
        assertTrue(source.rows.isEmpty());
        assertTrue(source.dataSetListeners.isEmpty());
    }

    @Test
    public void failedDataSetListenerRemovalDetachesCallLinkAndKeepsOriginalFailure() throws Exception {
        Source source = new Source(new Row(1, "A"));
        CallDestination destination = new CallDestination();
        IDataLink<IDataSet<Row>, IDataCall> link =
                new DLBuilder<IDataSet<Row>, IDataCall, Row, Integer>()
                        .source(source.dataSet).destination(destination.dataCall)
                        .joinColumn(row -> row.id, "PARENT_ID").link();
        IllegalStateException failure = new IllegalStateException("unsubscribe failed");
        source.removeNavigationFailure = failure;

        try {
            ((AutoCloseable) link).close();
            fail("Close should fail");
        } catch (IllegalStateException actual) {
            assertSame(failure, actual);
        }
        assertTrue(source.navigation.isEmpty());
        assertTrue(source.rows.isEmpty());
        assertTrue(source.dataSetListeners.isEmpty());
        assertNull(destination.callbacks.get("PARENT_ID").get());
        assertNull(link.getSource());
        ((AutoCloseable) link).close();
    }

    @Test
    public void failedDependentClosePreservesFailureAndDetachesDataSetCallLink() {
        Source source = new Source(new Row(1, "A"));
        CallDestination destination = new CallDestination();
        IDataLink<IDataSet<Row>, IDataCall> link =
                new DLBuilder<IDataSet<Row>, IDataCall, Row, Integer>()
                        .source(source.dataSet).destination(destination.dataCall)
                        .joinColumn(row -> row.id, "PARENT_ID").link();
        IllegalStateException cleanupFailure = new IllegalStateException("unsubscribe failed");
        source.removeNavigationFailure = cleanupFailure;
        destination.failClose = true;

        try {
            source.closeBefore();
            fail("Dependent close should fail");
        } catch (IllegalStateException actual) {
            assertEquals("dependent close failed", actual.getMessage());
            assertEquals(1, actual.getSuppressed().length);
            assertSame(cleanupFailure, actual.getSuppressed()[0]);
        }
        assertEquals(1, destination.closes);
        assertNull(link.getSource());
        assertTrue(source.navigation.isEmpty());
        assertTrue(source.rows.isEmpty());
        assertTrue(source.dataSetListeners.isEmpty());
        assertNull(destination.callbacks.get("PARENT_ID").get());
    }

    @Test
    public void failedCallLinkConstructionKeepsOriginalFailureWithCleanupSuppressed() {
        Source source = new Source(new Row(1, "A"));
        CallDestination destination = new CallDestination();
        IllegalStateException failure = new IllegalStateException("callback failed");
        IllegalStateException cleanupFailure = new IllegalStateException("unsubscribe failed");
        destination.callbackFailure = failure;
        source.removeNavigationFailure = cleanupFailure;

        try {
            new DLBuilder<IDataSet<Row>, IDataCall, Row, Integer>()
                    .source(source.dataSet).destination(destination.dataCall)
                    .joinColumn(row -> row.id, "PARENT_ID").link();
            fail("Construction should fail");
        } catch (RuntimeException actual) {
            assertSame(failure, actual.getCause());
            assertEquals(1, failure.getSuppressed().length);
            assertSame(cleanupFailure, failure.getSuppressed()[0]);
        }
    }

    @Test
    public void dlBuilderDataCallForwardsLazyInputsAndRequeryCondition() {
        Source source = new Source(new Row(1, "A"));
        CallDestination destination = new CallDestination();
        AtomicBoolean requery = new AtomicBoolean(false);

        IDataLink<IDataSet<Row>, IDataCall> link =
                new DLBuilder<IDataSet<Row>, IDataCall, Row, Object>()
                        .source(source.dataSet).destination(destination.dataCall)
                        .joinColumn(row -> row.id, "PARENT_ID")
                        .joinColumn(row -> row.kind, "KIND")
                        .requeryIf(requery::get).link();

        assertNotNull(link);
        assertSame(source.dataSet, link.getSource());
        assertSame(destination.dataCall, link.getDestination());
        assertTrue(destination.executions.isEmpty());
        source.navigate(new Row(2, "B"));
        assertTrue(destination.executions.isEmpty());
        assertEquals(Integer.valueOf(2), destination.callbacks.get("PARENT_ID").get());

        requery.set(true);
        source.rowOperation(DataSetRowEvent.RowOperationEnum.REFRESH, new Row(3, "C"));
        assertEquals(1, destination.executions.size());
        assertEquals(Integer.valueOf(3), destination.executions.get(0).get("PARENT_ID"));
        assertEquals("C", destination.executions.get(0).get("KIND"));

        source.closeBefore();
        assertEquals(1, destination.closes);
    }

    @Test
    public void dlBuilderDataCallUsesDefaultRequeryForStaticLink() {
        Source source = new Source(new Row(1, "A"));
        CallDestination destination = new CallDestination();

        IDataLink<IDataSet<Row>, IDataCall> link =
                DLBuilder.<IDataSet<Row>, IDataCall, Row, Integer>link(
                        source.dataSet, destination.dataCall,
                        row -> row.id, "PARENT_ID", null);

        assertNotNull(link);
        assertTrue(destination.executions.isEmpty());
        source.navigate(new Row(2, "B"));
        assertEquals(1, destination.executions.size());
        assertEquals(Integer.valueOf(2), destination.executions.get(0).get("PARENT_ID"));
    }
}
