package ru.inversion.dataset;

import org.junit.jupiter.api.Test;
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

    private static void setTotalRowCount(SQLDataSet<?> ds, int value) {
        try {
            java.lang.reflect.Field field =
                    SQLDataSet.class.getDeclaredField("totalRowCount");
            field.setAccessible(true);
            field.setInt(ds, value);
        }
        catch( ReflectiveOperationException ex ) {
            throw new AssertionError(ex);
        }
    }

    private static final class TestDataSet extends SQLDataSet<String> {
        Runnable beforeFirstExecute;
        int beforeFirstExecuteCount;

        TestDataSet() {
            super(null, String.class);
        }

        @Override
        protected void onBeforeFirstExecute() {
            beforeFirstExecuteCount++;
            if (beforeFirstExecute != null)
                beforeFirstExecute.run();
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

    private static final class PagingDataSet extends SQLDataSet<String> {
        int swapCount;

        PagingDataSet() {
            super(null, String.class);
            rows().add("first");
            setCurrentRowNum(0);
        }

        @Override
        public boolean swappingData() {
            if( swapCount++ == 0 ) {
                rows().add("target");
                return true;
            }
            return false;
        }
    }

    private static class Reader implements ISQLDataReader<String> {
        final List<Object> receivedParameters = new ArrayList<>();
        int executeCount;
        int pageCount;
        int closeCount;
        int resolvedExecuteCount;
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
        public void executeQuery(List<Object> values) {
            resolvedExecuteCount++;
            throw new AssertionError(
                    "Externally installed reader must use compatibility IParameters path");
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
    void failedFirstQueryRetriesFirstInitialization() throws DataSetException {
        TestDataSet ds = new TestDataSet();
        ds.beforeFirstExecute = () -> pre(ds, 601);

        Reader failure = new Reader();
        failure.failExecute = true;
        ds.useReader(failure);

        assertThrows(DataSetException.class, ds::executeQuery);

        assertEquals(1, ds.beforeFirstExecuteCount);
        assertNull(ds.getProperty("ds.execute_count"));
        assertFalse(ds.predicates().containsKey(601));

        Reader retry = new Reader();
        ds.useReader(retry);
        ds.executeQuery();

        assertEquals(2, ds.beforeFirstExecuteCount);
        assertEquals(Integer.valueOf(1), ds.getProperty("ds.execute_count"));
        assertTrue(ds.predicates().get(601).isFinal());
        assertEquals(Arrays.asList("row"), ds.getRows());
        assertEquals(1, retry.executeCount);
    }

    @Test
    void firstInitializationFailureRollsBackPendingPredicateAndReader() {
        TestDataSet ds = new TestDataSet();
        Reader reader = new Reader();
        ds.useReader(reader);
        ds.beforeFirstExecute = () -> {
            pre(ds, 602);
            throw new IllegalStateException("initialization failed");
        };

        assertThrows(DataSetException.class, ds::executeQuery);

        assertEquals(1, ds.beforeFirstExecuteCount);
        assertNull(ds.getProperty("ds.execute_count"));
        assertFalse(ds.predicates().containsKey(602));
        assertNull(ds.reader());
        assertEquals(1, reader.closeCount);
        assertTrue(ds.readerNeedsRecreation());
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
        assertEquals(1, reader.executeCount);
        assertEquals(0, reader.resolvedExecuteCount);
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
        assertEquals(0, reader.resolvedExecuteCount);
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
    void replacingSqlInvalidatesReader() {
        TestDataSet ds = new TestDataSet();
        Reader reader = new Reader();
        ds.useReader(reader);
        assertFalse(ds.readerNeedsRecreation());

        ds.setSQL("select code from items");

        assertTrue(ds.readerNeedsRecreation());
    }


    @Test
    void rowLimitInvalidatesReaderOnlyWhenChanged() {
        TestDataSet ds = new TestDataSet();
        Reader reader = new Reader();

        ds.useReader(reader);
        ds.setRowLimit(10);

        assertEquals(Integer.valueOf(10), ds.getRowLimit());
        assertTrue(ds.readerNeedsRecreation());

        ds.useReader(reader);
        ds.setRowLimit(10);
        assertFalse(ds.readerNeedsRecreation());

        ds.setRowLimit(null);
        assertNull(ds.getRowLimit());
        assertTrue(ds.readerNeedsRecreation());
    }

    @Test
    void rowLimitFluentApiReturnsSameDataSet() {
        TestDataSet ds = new TestDataSet();

        assertSame(ds, ds.rowLimit(25));
        assertEquals(Integer.valueOf(25), ds.getRowLimit());
    }

    @Test
    void negativeRowLimitIsRejectedWithoutChangingState() {
        TestDataSet ds = new TestDataSet();
        Reader reader = new Reader();
        ds.useReader(reader);

        assertThrows(
                IllegalArgumentException.class,
                () -> ds.setRowLimit(-1)
        );

        assertNull(ds.getRowLimit());
        assertFalse(ds.readerNeedsRecreation());
    }
    @Test
    void findRowCanPopulateUntilMatch() throws DataSetException {
        PagingDataSet ds = new PagingDataSet();

        assertTrue(ds.findRow(
                (row, value) -> row.equals(value),
                "target",
                false,
                true
        ));

        assertEquals(1, ds.getCurrentRowNum());
        assertEquals("target", ds.getCurrentRow());
        assertEquals(1, ds.swapCount);
    }


    @Test
    void closeFailureDetachesReaderFromDataSet() {
        TestDataSet ds = new TestDataSet();
        Reader reader = new Reader();
        reader.failClose = true;
        ds.useReader(reader);

        DataSetException failure =
                assertThrows(DataSetException.class, ds::close);

        assertTrue(failure.getMessage().contains("close failed"));
        assertNull(ds.reader());
        assertEquals(1, reader.closeCount);

        assertDoesNotThrow(ds::close);
        assertEquals(1, reader.closeCount);
    }

    @Test
    void successfulCloseDetachesReaderAndIsIdempotent()
            throws DataSetException {
        TestDataSet ds = new TestDataSet();
        Reader reader = new Reader();
        ds.useReader(reader);

        ds.close();

        assertNull(ds.reader());
        assertEquals(1, reader.closeCount);

        ds.close();
        assertEquals(1, reader.closeCount);
    }

    @Test
    void closeListenerFailureStillDetachesReaderAndSuppressesCloseFailure() {
        TestDataSet ds = new TestDataSet();
        Reader reader = new Reader();
        reader.failClose = true;
        ds.useReader(reader);
        ds.addDataSetListener(event -> {
            if (event.getEventType() == DataSetEvent.DataSetEventType.CLOSE
                    && event.isBefore())
                throw new IllegalStateException("listener failed");
        });

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                ds::close
        );

        assertEquals("listener failed", failure.getMessage());
        assertNull(ds.reader());
        assertEquals(1, reader.closeCount);
        assertEquals(1, failure.getSuppressed().length);
        assertTrue(
                failure.getSuppressed()[0]
                        .getMessage()
                        .contains("close failed"),
                failure.getSuppressed()[0].getMessage()
        );
    }


    @Test
    void readerReplacementCloseFailureIsNotRetried() {
        TestDataSet ds = new TestDataSet();
        Reader reader = new Reader();
        reader.failClose = true;
        ds.useReader(reader);

        ds.setSQL("select id from changed_source");

        assertThrows(DataSetException.class, ds::executeQuery);

        assertNull(ds.reader());
        assertEquals(1, reader.closeCount);
    }

    @Test
    void singleRowCloseFailureStillDetachesReader() {
        TestDataSet ds = new TestDataSet();
        Reader reader = new Reader();
        reader.failClose = true;
        ds.useReader(reader);
        ds.setDataQueryModeEnum(
                ISQLDataSet.DataQueryModeEnum.SINGLE_ROW
        );

        assertThrows(DataSetException.class, ds::swappingData);

        assertNull(ds.reader());
        assertEquals(1, reader.closeCount);
        assertEquals(
                Arrays.asList("row"),
                ds.getRows()
        );
    }

    @Test
    void maximumFetchFailureRemainsPrimaryWhenCloseAlsoFails() {
        String property =
                "ru.inversion.db.maximum_records_fetched";
        String previous = System.getProperty(property);

        try {
            System.setProperty(property, "1");

            TestDataSet ds = new TestDataSet();
            Reader reader = new Reader() {
                @Override
                public List<String> getNextPage(int pageSize) {
                    pageCount++;
                    eof = true;
                    return Arrays.asList("one", "two");
                }
            };
            reader.failClose = true;
            ds.useReader(reader);

            DataSetException failure =
                    assertThrows(
                            DataSetException.class,
                            ds::swappingData
                    );

            Throwable cause = failure.getCause();

            assertNotNull(cause);
            assertTrue(
                    cause.getMessage().contains(
                            "Exceeded the allowed number of records"),
                    cause.getMessage()
            );
            assertEquals(1, cause.getSuppressed().length);
            assertTrue(
                    cause.getSuppressed()[0]
                            .getMessage()
                            .contains("close failed"),
                    cause.getSuppressed()[0].getMessage()
            );
            assertNull(ds.reader());
            assertEquals(1, reader.closeCount);
            assertTrue(ds.getRows().isEmpty());
        }
        finally {
            if( previous == null )
                System.clearProperty(property);
            else
                System.setProperty(property, previous);
        }
    }


    @Test
    void firstPageFailureInvalidatesPreviousTotalRowCount() {
        TestDataSet ds = new TestDataSet();
        Reader reader = new Reader();
        reader.failPage = true;
        ds.useReader(reader);
        setTotalRowCount(ds, 123);

        assertThrows(DataSetException.class, ds::executeQuery);

        assertEquals(-1, ds.getTotalRowCount());
    }

    @Test
    void executeFailureBeforeRowReplacementKeepsPreviousTotalRowCount() {
        TestDataSet ds = new TestDataSet();
        Reader reader = new Reader();
        reader.failExecute = true;
        ds.useReader(reader);
        setTotalRowCount(ds, 123);

        assertThrows(DataSetException.class, ds::executeQuery);

        assertEquals(123, ds.getTotalRowCount());
    }

}
