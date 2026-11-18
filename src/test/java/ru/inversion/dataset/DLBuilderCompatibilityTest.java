package ru.inversion.dataset;

import org.junit.Test;
import ru.inversion.datacall.IDataCall;
import ru.inversion.tds.ITreeDataSet;
import ru.inversion.tds.ITreeDataSetItem;
import ru.inversion.tds.ITreeDataSetItemListener;
import ru.inversion.tds.ITreeDataSetListener;
import ru.inversion.tds.ITreeDataSetNavigationListener;
import ru.inversion.tds.SQLTreeDataSet;
import ru.inversion.tds.TreeDataSetItemEvent;
import ru.inversion.tds.TreeDataSetNavigationEvent;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static org.junit.Assert.*;

public class DLBuilderCompatibilityTest {

    private static final class Row {
        final int id;

        Row(int id) {
            this.id = id;
        }
    }

    private static final class DataSource {
        final IDataSet<Row> dataSet;
        final List<IDataSetNavigationListener<Row>> navigation = new ArrayList<>();
        final List<IDataSetRowListener<Row>> rows = new ArrayList<>();
        Row current;
        RuntimeException removeNavigationFailure;

        @SuppressWarnings("unchecked")
        DataSource(Row current) {
            this.current = current;
            dataSet = (IDataSet<Row>) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[]{IDataSet.class},
                    (proxy, method, invocationArgs) -> {
                        switch (method.getName()) {
                            case "getCurrentRow": return this.current;
                            case "addNavigationListener":
                                navigation.add((IDataSetNavigationListener<Row>) invocationArgs[0]);
                                return null;
                            case "removeNavigationListener":
                                navigation.remove(invocationArgs[0]);
                                if (removeNavigationFailure != null) throw removeNavigationFailure;
                                return null;
                            case "addRowListener":
                                rows.add((IDataSetRowListener<Row>) invocationArgs[0]);
                                return null;
                            case "removeRowListener":
                                rows.remove(invocationArgs[0]);
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

        void refresh(Row next) {
            Row previous = current;
            current = next;
            DataSetRowEvent<Row> event = new DataSetRowEvent<>(dataSet,
                    DataSetRowEvent.RowOperationEnum.REFRESH, previous, next, 0, 1);
            for (IDataSetRowListener<Row> listener : new ArrayList<>(rows))
                listener.rowOperation(event);
        }
    }

    private static final class TreeSource {
        final ITreeDataSet<Row> tree;
        final List<ITreeDataSetNavigationListener<Row>> navigation = new ArrayList<>();
        final List<ITreeDataSetItemListener<Row>> items = new ArrayList<>();
        final List<ITreeDataSetListener<Row>> dataSetListeners = new ArrayList<>();
        ITreeDataSetItem<Row> current;
        Row currentValue;
        RuntimeException removeNavigationFailure;

        @SuppressWarnings("unchecked")
        TreeSource(Row initial) {
            tree = (ITreeDataSet<Row>) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[]{ITreeDataSet.class},
                    (proxy, method, invocationArgs) -> {
                        switch (method.getName()) {
                            case "getCurrentItem": return current;
                            case "addNavigationListener":
                                navigation.add((ITreeDataSetNavigationListener<Row>) invocationArgs[0]);
                                return null;
                            case "removeNavigationListener":
                                navigation.remove(invocationArgs[0]);
                                if (removeNavigationFailure != null) throw removeNavigationFailure;
                                return null;
                            case "addItemListener":
                                items.add((ITreeDataSetItemListener<Row>) invocationArgs[0]);
                                return null;
                            case "removeItemListener":
                                items.remove(invocationArgs[0]);
                                return null;
                            case "addDataSetListener":
                                dataSetListeners.add((ITreeDataSetListener<Row>) invocationArgs[0]);
                                return null;
                            case "removeDataSetListener":
                                dataSetListeners.remove(invocationArgs[0]);
                                return null;
                            default: throw new AssertionError("Unexpected ITreeDataSet." + method.getName());
                        }
                    });
            currentValue = initial;
            current = item(initial);
        }

        @SuppressWarnings("unchecked")
        private ITreeDataSetItem<Row> item(Row value) {
            if (value == null)
                return null;
            return (ITreeDataSetItem<Row>) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[]{ITreeDataSetItem.class},
                    (proxy, method, invocationArgs) -> {
                        switch (method.getName()) {
                            case "getValue": return proxy == current ? currentValue : value;
                            case "getDataSet": return tree;
                            default: throw new AssertionError("Unexpected ITreeDataSetItem." + method.getName());
                        }
                    });
        }

        void navigate(Row next) {
            ITreeDataSetItem<Row> previous = current;
            current = item(next);
            currentValue = next;
            TreeDataSetNavigationEvent<Row> event =
                    new TreeDataSetNavigationEvent<>(tree, previous, current);
            for (ITreeDataSetNavigationListener<Row> listener : new ArrayList<>(navigation))
                listener.navigated(event);
        }

        void changeValue(Row next) {
            TreeDataSetItemEvent<Row> event =
                    new TreeDataSetItemEvent<>(current, false, current.getValue(), next);
            currentValue = next;
            for (ITreeDataSetItemListener<Row> listener : new ArrayList<>(items))
                listener.itemChanged(event);
        }

        void changeOtherValue() {
            TreeDataSetItemEvent<Row> event =
                    new TreeDataSetItemEvent<>(item(new Row(99)), false, new Row(99), new Row(100));
            for (ITreeDataSetItemListener<Row> listener : new ArrayList<>(items))
                listener.itemChanged(event);
        }

        void changeLeaf() {
            TreeDataSetItemEvent<Row> event = new TreeDataSetItemEvent<>(current, false, true);
            for (ITreeDataSetItemListener<Row> listener : new ArrayList<>(items))
                listener.itemChanged(event);
        }
    }

    private static final class TreeDestination extends SQLTreeDataSet<Row> {
        final Map<String, Object> parameters = new HashMap<>();
        final Map<Integer, String> filters = new HashMap<>();
        String filter;
        int nextFilterId = 42;
        int queries;
        int clears;
        RuntimeException removeFailure;

        @Override public int setFilter(String sql, boolean fixed, boolean append) {
            assertTrue(fixed);
            assertTrue(append);
            filter = sql;
            int id = nextFilterId++;
            filters.put(id, sql);
            return id;
        }

        @Override public String removeFilter(int id) {
            String filter = filters.remove(id);
            if (removeFailure != null) throw removeFailure;
            return filter;
        }

        @Override public void setParameter(String name, Object value) {
            parameters.put(name, value);
        }

        @Override public void executeQuery() {
            queries++;
        }

        @Override public void clear() {
            clears++;
        }
    }

    private static final class DataDestination extends SQLDataSet<Row> {
        final Map<String, Object> parameters = new HashMap<>();
        final Map<Integer, String> filters = new HashMap<>();
        String filter;
        int nextFilterId = 43;
        int queries;
        int clears;
        boolean failQuery;
        RuntimeException removeFailure;

        @Override public int setFilter(String sql, boolean fixed, boolean append) {
            assertTrue(fixed);
            assertTrue(append);
            filter = sql;
            int id = nextFilterId++;
            filters.put(id, sql);
            return id;
        }

        @Override public String removeFilter(int id) {
            String filter = filters.remove(id);
            if (removeFailure != null) throw removeFailure;
            return filter;
        }

        @Override public void setParameter(String name, Object value) {
            parameters.put(name, value);
        }

        @Override public void executeQuery() {
            if (failQuery) throw new IllegalStateException("query failed");
            queries++;
        }

        @Override public void clear() {
            clears++;
        }
    }

    private static final class CallDestination {
        final IDataCall call;
        final Map<String,Supplier<?>> callbacks = new HashMap<>();
        final List<Object> executions = new ArrayList<>();
        int closes;

        CallDestination() {
            call = (IDataCall)Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[]{IDataCall.class}, (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "setValueCallback":
                                callbacks.put((String)args[0], (Supplier<?>)args[1]);
                                return null;
                            case "execute":
                                executions.add(callbacks.get("PARENT_ID").get());
                                return proxy;
                            case "close": closes++; return null;
                            default: throw new AssertionError(method.getName());
                        }
                    });
        }
    }

    @Test
    public void dataSetToTreePreservesNavigationRefreshAndEmptyRow() throws Exception {
        DataSource source = new DataSource(new Row(1));
        TreeDestination destination = new TreeDestination();
        AtomicBoolean requery = new AtomicBoolean(true);

        IDataLink<IDataSet<Row>, SQLTreeDataSet<Row>> link =
                new DLBuilder<IDataSet<Row>, SQLTreeDataSet<Row>, Row, Integer>()
                        .source(source.dataSet).destination(destination)
                        .joinColumn(row -> row.id, "PARENT_ID")
                        .requeryIf(requery::get).link();

        assertNotNull(link);
        assertSame(source.dataSet, link.getSource());
        assertSame(destination, link.getDestination());
        assertTrue(destination.filter.contains("PARENT_ID= :PARENT_ID"));
        assertEquals(Integer.valueOf(1), destination.parameters.get("PARENT_ID"));
        assertEquals(1, destination.queries);

        source.navigate(new Row(2));
        source.refresh(new Row(3));
        assertEquals(Integer.valueOf(3), destination.parameters.get("PARENT_ID"));
        assertEquals(3, destination.queries);

        requery.set(false);
        source.navigate(new Row(4));
        assertEquals(Integer.valueOf(4), destination.parameters.get("PARENT_ID"));
        assertEquals(3, destination.queries);
        source.navigate(null);
        assertTrue(destination.parameters.containsKey("PARENT_ID"));
        assertNull(destination.parameters.get("PARENT_ID"));
        assertEquals(1, destination.clears);

        ((AutoCloseable) link).close();
        assertTrue(source.navigation.isEmpty());
        assertTrue(source.rows.isEmpty());
        source.navigate(new Row(5));
        assertEquals(3, destination.queries);
    }

    @Test
    public void dataSetToTreeUnsubscribeFailureStillRemovesFilter() throws Exception {
        DataSource source = new DataSource(null);
        TreeDestination destination = new TreeDestination();
        IDataLink<IDataSet<Row>, SQLTreeDataSet<Row>> link =
                new DLBuilder<IDataSet<Row>, SQLTreeDataSet<Row>, Row, Integer>()
                        .source(source.dataSet).destination(destination)
                        .joinColumn(row -> row.id, "PARENT_ID").link();
        IllegalStateException failure = new IllegalStateException("unsubscribe failed");
        source.removeNavigationFailure = failure;

        try {
            ((AutoCloseable) link).close();
            fail("Close should fail");
        } catch (IllegalStateException actual) {
            assertSame(failure, actual);
        }
        assertNull(link.getSource());
        assertNull(link.getDestination());
        assertTrue(source.navigation.isEmpty());
        assertTrue(source.rows.isEmpty());
        assertTrue(destination.filters.isEmpty());
    }

    @Test
    public void treeToDataSetPreservesNavigationItemChangeAndEmptyItem() throws Exception {
        TreeSource source = new TreeSource(new Row(10));
        DataDestination destination = new DataDestination();
        AtomicBoolean requery = new AtomicBoolean(true);

        IDataLink<ITreeDataSet<Row>, ISQLDataSet<Row>> link =
                new DLBuilder<ITreeDataSet<Row>, ISQLDataSet<Row>, Row, Integer>()
                        .source(source.tree).destination(destination)
                        .joinColumn(row -> row.id, "PARENT_ID")
                        .requeryIf(requery::get).link();

        assertNotNull(link);
        assertSame(source.tree, link.getSource());
        assertSame(destination, link.getDestination());
        assertTrue(destination.filter.contains("PARENT_ID= :PARENT_ID"));
        assertEquals(Integer.valueOf(10), destination.parameters.get("PARENT_ID"));
        assertEquals(1, destination.queries);
        assertEquals(1, source.dataSetListeners.size());

        source.navigate(new Row(11));
        source.changeValue(new Row(12));
        source.changeLeaf();
        source.changeOtherValue();
        assertEquals(Integer.valueOf(12), destination.parameters.get("PARENT_ID"));
        assertEquals(3, destination.queries);

        requery.set(false);
        source.navigate(new Row(13));
        assertEquals(Integer.valueOf(13), destination.parameters.get("PARENT_ID"));
        assertEquals(3, destination.queries);
        source.navigate(null);
        assertNull(destination.parameters.get("PARENT_ID"));
        assertEquals(1, destination.clears);

        ((AutoCloseable) link).close();
        assertTrue(source.navigation.isEmpty());
        assertTrue(source.items.isEmpty());
        assertTrue(source.dataSetListeners.isEmpty());
        source.navigate(new Row(14));
        assertEquals(3, destination.queries);
    }

    @Test
    public void treeToTreePreservesFilterNavigationAndClose() throws Exception {
        TreeSource source = new TreeSource(new Row(1));
        TreeDestination destination = new TreeDestination();
        IDataLink<ITreeDataSet<Row>,SQLTreeDataSet<Row>> link =
                new DLBuilder<ITreeDataSet<Row>,SQLTreeDataSet<Row>,Row,Integer>()
                        .source(source.tree).destination(destination)
                        .joinColumn(row -> row.id, "PARENT_ID").link();

        assertEquals(Integer.valueOf(1), destination.parameters.get("PARENT_ID"));
        assertEquals(1, destination.queries);
        source.navigate(new Row(2));
        source.changeLeaf();
        source.changeOtherValue();
        assertEquals(2, destination.queries);
        assertEquals(Integer.valueOf(2), destination.parameters.get("PARENT_ID"));

        ((AutoCloseable)link).close();
        assertTrue(destination.filters.isEmpty());
        assertTrue(source.navigation.isEmpty());
        assertTrue(source.items.isEmpty());
        assertTrue(source.dataSetListeners.isEmpty());
    }

    @Test
    public void treeLinkCleansUpAfterMultipleCloseFailures() throws Exception {
        TreeSource source = new TreeSource(null);
        DataDestination destination = new DataDestination();
        IDataLink<ITreeDataSet<Row>, ISQLDataSet<Row>> link =
                new DLBuilder<ITreeDataSet<Row>, ISQLDataSet<Row>, Row, Integer>()
                        .source(source.tree).destination(destination)
                        .joinColumn(row -> row.id, "PARENT_ID").link();
        IllegalStateException unsubscribeFailure = new IllegalStateException("unsubscribe failed");
        IllegalStateException filterFailure = new IllegalStateException("filter failed");
        source.removeNavigationFailure = unsubscribeFailure;
        destination.removeFailure = filterFailure;

        try {
            ((AutoCloseable) link).close();
            fail("Close should fail");
        } catch (IllegalStateException actual) {
            assertSame(unsubscribeFailure, actual);
            assertEquals(1, actual.getSuppressed().length);
            assertSame(filterFailure, actual.getSuppressed()[0]);
        }

        assertNull(link.getSource());
        assertNull(link.getDestination());
        assertTrue(source.navigation.isEmpty());
        assertTrue(source.items.isEmpty());
        assertTrue(source.dataSetListeners.isEmpty());
        assertTrue(destination.filters.isEmpty());
        ((AutoCloseable) link).close();
    }

    @Test
    public void closingTreeSourceReleasesOnlyItsDataSetLinkFilter() throws Exception {
        TreeDestination source = new TreeDestination();
        DataDestination destination = new DataDestination();
        int ownFilter = destination.setFilter("ACTIVE=1", true, true);
        IDataLink<ITreeDataSet<Row>, ISQLDataSet<Row>> link =
                new DLBuilder<ITreeDataSet<Row>, ISQLDataSet<Row>, Row, Integer>()
                        .source(source).destination(destination)
                        .joinColumn(row -> row.id, "PARENT_ID").link();
        assertEquals(2, destination.filters.size());

        source.close();

        assertNull(link.getSource());
        assertNull(link.getDestination());
        assertEquals(1, destination.filters.size());
        assertEquals("ACTIVE=1", destination.filters.get(ownFilter));
        assertEquals(0, destination.queries);
        assertEquals(0, destination.clears);
        ((AutoCloseable)link).close();
    }

    @Test
    public void closingTreeSourceReleasesOnlyItsTreeLinkFilter() throws Exception {
        TreeDestination source = new TreeDestination();
        TreeDestination destination = new TreeDestination();
        int ownFilter = destination.setFilter("ACTIVE=1", true, true);
        IDataLink<ITreeDataSet<Row>, SQLTreeDataSet<Row>> link =
                new DLBuilder<ITreeDataSet<Row>, SQLTreeDataSet<Row>, Row, Integer>()
                        .source(source).destination(destination)
                        .joinColumn(row -> row.id, "PARENT_ID").link();
        assertEquals(2, destination.filters.size());

        source.close();

        assertNull(link.getSource());
        assertNull(link.getDestination());
        assertEquals(1, destination.filters.size());
        assertEquals("ACTIVE=1", destination.filters.get(ownFilter));
        assertEquals(0, destination.queries);
        assertEquals(0, destination.clears);
        ((AutoCloseable)link).close();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void treeToDataSetWorksWithAnyISQLDataSetImplementation() throws Exception {
        TreeSource source = new TreeSource(new Row(10));
        Map<String,Object> parameters = new HashMap<>();
        List<Integer> removedFilters = new ArrayList<>();
        int[] queries = {0};
        ISQLDataSet<Row> destination = (ISQLDataSet<Row>)Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{ISQLDataSet.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "setParameter": parameters.put((String)args[0], args[1]); return null;
                        case "setFilter": return 73;
                        case "removeFilter": removedFilters.add((Integer)args[0]); return null;
                        case "executeQuery": queries[0]++; return null;
                        case "clear": return null;
                        default: throw new AssertionError(method.getName());
                    }
                });

        IDataLink<ITreeDataSet<Row>, ISQLDataSet<Row>> link =
                new DLBuilder<ITreeDataSet<Row>, ISQLDataSet<Row>, Row, Integer>()
                        .source(source.tree).destination(destination)
                        .joinColumn(row -> row.id, "PARENT_ID").link();

        assertEquals(Integer.valueOf(10), parameters.get("PARENT_ID"));
        assertEquals(1, queries[0]);
        source.navigate(new Row(11));
        assertEquals(2, queries[0]);
        ((AutoCloseable)link).close();
        assertEquals(Integer.valueOf(73), removedFilters.get(0));
        assertTrue(source.dataSetListeners.isEmpty());
    }

    @Test
    public void treeToDataCallUsesLazyValuesAndReleasesListeners() throws Exception {
        TreeSource source = new TreeSource(new Row(10));
        CallDestination destination = new CallDestination();
        AtomicBoolean requery = new AtomicBoolean(true);
        IDataLink<ITreeDataSet<Row>,IDataCall> link =
                new DLBuilder<ITreeDataSet<Row>,IDataCall,Row,Integer>()
                        .source(source.tree).destination(destination.call)
                        .joinColumn(row -> row.id, "PARENT_ID")
                        .requeryIf(requery::get).link();

        assertTrue(destination.executions.isEmpty());
        assertEquals(Integer.valueOf(10), destination.callbacks.get("PARENT_ID").get());
        source.navigate(new Row(11));
        source.changeValue(new Row(12));
        source.changeLeaf();
        source.changeOtherValue();
        assertEquals(Integer.valueOf(11), destination.executions.get(0));
        assertEquals(Integer.valueOf(12), destination.executions.get(1));
        assertEquals(2, destination.executions.size());
        requery.set(false);
        source.navigate(new Row(13));
        assertEquals(2, destination.executions.size());
        assertEquals(Integer.valueOf(13), destination.callbacks.get("PARENT_ID").get());
        source.navigate(null);
        assertNull(destination.callbacks.get("PARENT_ID").get());

        ((AutoCloseable)link).close();
        assertTrue(source.navigation.isEmpty());
        assertTrue(source.items.isEmpty());
        assertTrue(source.dataSetListeners.isEmpty());
        assertNull(destination.callbacks.get("PARENT_ID").get());
        assertEquals(0, destination.closes);
    }

    @Test
    public void failedTreeListenerRemovalClearsLazyCallInput() throws Exception {
        TreeSource source = new TreeSource(new Row(1));
        CallDestination destination = new CallDestination();
        IDataLink<ITreeDataSet<Row>, IDataCall> link =
                new DLBuilder<ITreeDataSet<Row>, IDataCall, Row, Integer>()
                        .source(source.tree).destination(destination.call)
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
        assertTrue(source.items.isEmpty());
        assertTrue(source.dataSetListeners.isEmpty());
        assertNull(destination.callbacks.get("PARENT_ID").get());
        assertNull(link.getSource());
        ((AutoCloseable) link).close();
    }

    @Test
    public void failedInitialTreeLinkQueryReleasesFilterAndListeners() {
        TreeSource source = new TreeSource(new Row(1));
        DataDestination destination = new DataDestination();
        destination.failQuery = true;

        try {
            new DLBuilder<ITreeDataSet<Row>, ISQLDataSet<Row>, Row, Integer>()
                    .source(source.tree).destination(destination)
                    .joinColumn(row -> row.id, "PARENT_ID").link();
            fail("Initial query should fail");
        } catch (RuntimeException expected) {
            assertTrue(destination.filters.isEmpty());
            assertTrue(source.navigation.isEmpty());
            assertTrue(source.items.isEmpty());
            assertTrue(source.dataSetListeners.isEmpty());
        }
    }

    @Test
    public void dataSetToTreeCloseRemovesOnlyItsFilterAcrossRelinking() throws Exception {
        TreeDestination destination = new TreeDestination();
        int ownFilter = destination.setFilter("ACTIVE=1", true, true);

        IDataLink<IDataSet<Row>, SQLTreeDataSet<Row>> first =
                new DLBuilder<IDataSet<Row>, SQLTreeDataSet<Row>, Row, Integer>()
                        .source(new DataSource(null).dataSet).destination(destination)
                        .joinColumn(row -> row.id, "PARENT_ID").link();
        assertEquals(2, destination.filters.size());

        ((AutoCloseable) first).close();
        assertEquals(1, destination.filters.size());
        assertEquals("ACTIVE=1", destination.filters.get(ownFilter));

        IDataLink<IDataSet<Row>, SQLTreeDataSet<Row>> second =
                new DLBuilder<IDataSet<Row>, SQLTreeDataSet<Row>, Row, Integer>()
                        .source(new DataSource(null).dataSet).destination(destination)
                        .joinColumn(row -> row.id, "PARENT_ID").link();
        assertEquals(2, destination.filters.size());

        ((AutoCloseable) second).close();
        ((AutoCloseable) second).close();
        assertEquals(1, destination.filters.size());
        assertEquals("ACTIVE=1", destination.filters.get(ownFilter));
    }

    @Test
    public void dataSetToTreeUsesDefaultRequeryAndSupportsUnfilteredParameter() throws Exception {
        DataSource source = new DataSource(new Row(1));
        TreeDestination destination = new TreeDestination();
        IDataLink<IDataSet<Row>, SQLTreeDataSet<Row>> link =
                new DLBuilder<IDataSet<Row>, SQLTreeDataSet<Row>, Row, Integer>()
                        .source(source.dataSet).destination(destination)
                        .joinColumn(row -> row.id, "EXTRA", false).link();

        assertNull(destination.filter);
        assertEquals(Integer.valueOf(1), destination.parameters.get("EXTRA"));
        assertEquals(1, destination.queries);
        source.navigate(new Row(2));
        assertEquals(2, destination.queries);
        ((AutoCloseable)link).close();
        assertTrue(destination.filters.isEmpty());
    }

    @Test
    public void treeToDataSetCloseRemovesOnlyItsFilterAcrossRelinking() throws Exception {
        DataDestination destination = new DataDestination();
        int ownFilter = destination.setFilter("ACTIVE=1", true, true);

        IDataLink<ITreeDataSet<Row>, ISQLDataSet<Row>> first =
                new DLBuilder<ITreeDataSet<Row>, ISQLDataSet<Row>, Row, Integer>()
                        .source(new TreeSource(null).tree).destination(destination)
                        .joinColumn(row -> row.id, "PARENT_ID").link();
        assertEquals(2, destination.filters.size());

        ((AutoCloseable) first).close();
        assertEquals(1, destination.filters.size());
        assertEquals("ACTIVE=1", destination.filters.get(ownFilter));

        IDataLink<ITreeDataSet<Row>, ISQLDataSet<Row>> second =
                new DLBuilder<ITreeDataSet<Row>, ISQLDataSet<Row>, Row, Integer>()
                        .source(new TreeSource(null).tree).destination(destination)
                        .joinColumn(row -> row.id, "PARENT_ID").link();
        assertEquals(2, destination.filters.size());

        ((AutoCloseable) second).close();
        ((AutoCloseable) second).close();
        assertEquals(1, destination.filters.size());
        assertEquals("ACTIVE=1", destination.filters.get(ownFilter));
    }
}
