package ru.inversion.dataset;

import org.junit.jupiter.api.Test;
import ru.inversion.dataset.parser.SQLParser;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Проверка настоящего SQLDataSet.executeQuery() без БД.
 * Подменяется только ISQLDataReader, production код SQLDataSet не меняется ради тестов.
 */
class SQLDataSetLifecycleTest {

    private static final class TestDataSet extends SQLDataSet<String> {
        TestDataSet() {
            super(null, String.class);
        }

        void useReader(ISQLDataReader<String> reader) {
            this.dataReader = reader;
            this.needReCreateReader.setValue(false);
        }

        boolean readerNeedsRecreation() {
            return needReCreateReader.getValue();
        }

        ISQLDataReader<String> reader() {
            return dataReader;
        }
    }

    private static final class Reader implements ISQLDataReader<String> {
        final List<Object> receivedParameters = new ArrayList<>();
        int executeCount;
        int pageCount;
        int closeCount;
        boolean eof;
        boolean failExecute;
        boolean failPage;
        boolean failClose;
        boolean emptyPage;
        Runnable duringPage;

        @Override
        public void executeQuery(IParameters values) throws DataSetException {
            executeCount++;
            if (failExecute)
                throw new DataSetException("execute failed");
            receivedParameters.add(values.hasParameter("branch")
                    ? values.getParameter("branch") : null);
            eof = false;
        }

        @Override
        public boolean isEOF() {
            return eof;
        }

        @Override
        public List<String> getNextPage(int pageSize) {
            pageCount++;
            if (duringPage != null)
                duringPage.run();
            if (failPage)
                throw new IllegalStateException("reading first page failed");
            eof = true;
            return emptyPage ? Collections.emptyList() : Arrays.asList("row");
        }

        @Override
        public void close() throws DataSetException {
            closeCount++;
            eof = true;
            if (failClose)
                throw new DataSetException("close failed");
        }
    }

    private static ISQLDataSet.PredicateItem pre(TestDataSet ds, int id) {
        ISQLDataSet.PredicateItem item = new ISQLDataSet.PredicateItem(
                "value = " + id, ISQLDataSet.PredicateTypeEnum.TEMPORARY_FILTER);
        ds.predicates().put(id, item);
        return item;
    }

    @Test
    void firstPageFailureRemovesPreAndClosesReader() {
        TestDataSet ds = new TestDataSet();
        Reader reader = new Reader();
        reader.failPage = true;
        pre(ds, 101);
        ds.useReader(reader);

        assertThrows(DataSetException.class, ds::executeQuery);

        assertFalse(ds.predicates().containsKey(101));
        assertNull(ds.reader());
        assertTrue(ds.readerNeedsRecreation());
        assertEquals(1, reader.executeCount);
        assertEquals(1, reader.pageCount);
        assertEquals(1, reader.closeCount);
    }

    @Test
    void executeFailureRemovesPreAndClosesReader() {
        TestDataSet ds = new TestDataSet();
        Reader reader = new Reader();
        reader.failExecute = true;
        pre(ds, 102);
        ds.useReader(reader);

        assertThrows(DataSetException.class, ds::executeQuery);

        assertFalse(ds.predicates().containsKey(102));
        assertNull(ds.reader());
        assertTrue(ds.readerNeedsRecreation());
        assertEquals(0, reader.pageCount);
        assertEquals(1, reader.closeCount);
    }

    @Test
    void successfulFirstPageConfirmsPreAndSetsCurrentRow() throws DataSetException {
        TestDataSet ds = new TestDataSet();
        Reader reader = new Reader();
        ISQLDataSet.PredicateItem item = pre(ds, 103);
        List<Boolean> observed = new ArrayList<>();
        reader.duringPage = () -> observed.add(item.isPre());
        ds.useReader(reader);

        ds.executeQuery();

        assertEquals(Arrays.asList(true), observed); // During fetch, not yet confirmed.
        assertTrue(item.isFinal());
        assertEquals(Arrays.asList("row"), ds.getRows());
        assertEquals(0, ds.getCurrentRowNum());
        assertEquals(0, reader.closeCount);
    }

    @Test
    void failedNewPreDoesNotRemoveExistingFinal() {
        TestDataSet ds = new TestDataSet();
        ISQLDataSet.PredicateItem old = pre(ds, 201);
        old.makeFinal();
        pre(ds, 202);
        Reader reader = new Reader();
        reader.failPage = true;
        ds.useReader(reader);

        assertThrows(DataSetException.class, ds::executeQuery);

        assertSame(old, ds.predicates().get(201));
        assertTrue(old.isFinal());
        assertFalse(ds.predicates().containsKey(202));
    }

    @Test
    void canRetryWithoutFailedPreButWithPreviousFinal() throws DataSetException {
        TestDataSet ds = new TestDataSet();
        ISQLDataSet.PredicateItem retained = pre(ds, 501);
        retained.makeFinal();
        pre(ds, 502);

        Reader failure = new Reader();
        failure.failPage = true;
        ds.useReader(failure);
        assertThrows(DataSetException.class, ds::executeQuery);
        assertTrue(ds.predicates().containsKey(501));
        assertFalse(ds.predicates().containsKey(502));

        Reader retry = new Reader();
        ds.useReader(retry);
        ds.executeQuery();

        assertTrue(retained.isFinal());
        assertEquals(Arrays.asList("row"), ds.getRows());
        assertEquals(0, ds.getCurrentRowNum());
        assertEquals(1, retry.executeCount);
    }

    @Test
    void emptyResultIsStillSuccessfulExecution() throws DataSetException {
        TestDataSet ds = new TestDataSet();
        ISQLDataSet.PredicateItem item = pre(ds, 301);
        Reader reader = new Reader();
        reader.emptyPage = true;
        ds.useReader(reader);

        ds.executeQuery();

        assertTrue(item.isFinal());
        assertTrue(ds.getRows().isEmpty());
        assertEquals(-1, ds.getCurrentRowNum());
    }

    @Test
    void repeatedExecuteReadsLiveDataSetParameters() throws DataSetException {
        TestDataSet ds = new TestDataSet();
        Reader reader = new Reader();
        ds.setParameter("branch", 10);
        ds.useReader(reader);

        ds.executeQuery();
        ds.setParameter("branch", 20);
        ds.executeQuery();

        assertSame(reader, ds.reader());
        assertEquals(Arrays.asList(10, 20), reader.receivedParameters);
        assertEquals(2, reader.executeCount);
        assertEquals(2, reader.pageCount);
        assertEquals(0, reader.closeCount);
    }

    @Test
    void closeErrorDoesNotReplaceOriginalPageFailure() {
        TestDataSet ds = new TestDataSet();
        Reader reader = new Reader();
        reader.failPage = true;
        reader.failClose = true;
        pre(ds, 401);
        ds.useReader(reader);

        DataSetException failure = assertThrows(DataSetException.class, ds::executeQuery);
        Throwable original = failure.getCause();
        assertNotNull(original);
        assertTrue(original.getMessage().contains("swap data"));
        assertEquals(1, original.getSuppressed().length);
        assertTrue(original.getSuppressed()[0].getMessage().contains("close failed"));
        assertNull(ds.reader());
    }

    @Test
    void replacingSqlInvalidatesLegacySqlAnalysis() throws Exception {
        TestDataSet ds = new TestDataSet();
        Field cache = SQLDataSet.class.getDeclaredField("exprInfo");
        cache.setAccessible(true);
        cache.set(ds, new SQLParser("select id from items").analyze());
        assertNotNull(cache.get(ds));

        ds.setSQL("select code from items");

        assertNull(cache.get(ds));
        assertTrue(ds.readerNeedsRecreation());
    }
}
