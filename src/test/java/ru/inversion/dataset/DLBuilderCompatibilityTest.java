package ru.inversion.dataset;

import org.junit.Test;
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
                            case "getValue": return value;
                            case "getDataSet": return tree;
                            default: throw new AssertionError("Unexpected ITreeDataSetItem." + method.getName());
                        }
                    });
        }

        void navigate(Row next) {
            ITreeDataSetItem<Row> previous = current;
            current = item(next);
            TreeDataSetNavigationEvent<Row> event =
                    new TreeDataSetNavigationEvent<>(tree, previous, current);
            for (ITreeDataSetNavigationListener<Row> listener : new ArrayList<>(navigation))
                listener.navigated(event);
        }

        void changeValue(Row next) {
            TreeDataSetItemEvent<Row> event =
                    new TreeDataSetItemEvent<>(current, false, current.getValue(), next);
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

        @Override public int setFilter(String sql, boolean fixed, boolean append) {
            assertTrue(fixed);
            assertTrue(append);
            filter = sql;
            int id = nextFilterId++;
            filters.put(id, sql);
            return id;
        }

        @Override public String removeFilter(int id) {
            return filters.remove(id);
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

        @Override public int setFilter(String sql, boolean fixed, boolean append) {
            assertTrue(fixed);
            assertTrue(append);
            filter = sql;
            int id = nextFilterId++;
            filters.put(id, sql);
            return id;
        }

        @Override public String removeFilter(int id) {
            return filters.remove(id);
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

        source.navigate(new Row(11));
        source.changeValue(new Row(12));
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
        source.navigate(new Row(14));
        assertEquals(3, destination.queries);
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
