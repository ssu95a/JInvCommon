package ru.inversion.dataset;

import org.junit.Test;
import ru.inversion.utils.AutoCloseableIterator;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.Assert.*;

public class IDataReaderIteratorTest {

    @Test
    public void exhaustionClosesReaderAndNextFollowsIteratorContract()
            throws Exception {
        Reader reader = new Reader(
                Collections.singletonList(
                        Collections.singletonList(1)
                )
        );

        AutoCloseableIterator<Integer> iterator =
                IDataReader.closeableIterator(reader, 1);

        assertTrue(iterator.hasNext());
        assertEquals(Integer.valueOf(1), iterator.next());
        assertFalse(iterator.hasNext());
        assertEquals(1, reader.closeCount);

        try {
            iterator.next();
            fail("Expected NoSuchElementException");
        }
        catch( NoSuchElementException expected ) {
            // expected
        }

        iterator.close();
        assertEquals(1, reader.closeCount);
    }

    @Test
    public void compatibilityIteratorIsActuallyCloseable()
            throws Exception {
        Reader reader = new Reader(
                Collections.singletonList(
                        Arrays.asList(1, 2)
                )
        );

        Iterator<Integer> iterator =
                IDataReader.iterator(reader, 50);

        assertTrue(iterator instanceof AutoCloseableIterator);
        assertEquals(Integer.valueOf(1), iterator.next());
        assertEquals(0, reader.closeCount);

        ((AutoCloseableIterator<?>)iterator).close();

        assertEquals(1, reader.closeCount);
        assertFalse(iterator.hasNext());
    }

    @Test
    public void pagingFailureClosesReaderAndPreservesCloseFailure() {
        Reader reader = new Reader(
                Collections.singletonList(
                        Collections.singletonList(1)
                )
        );
        reader.failPage = 1;
        reader.failClose = true;

        AutoCloseableIterator<Integer> iterator =
                IDataReader.closeableIterator(reader, 1);

        IllegalStateException failure;

        try {
            iterator.next();
            fail("Expected page failure");
            return;
        }
        catch( IllegalStateException ex ) {
            failure = ex;
        }

        assertEquals("page failed", failure.getMessage());
        assertEquals(1, reader.closeCount);
        assertEquals(1, failure.getSuppressed().length);
        assertTrue(
                failure.getSuppressed()[0].getMessage(),
                failure.getSuppressed()[0]
                        .getMessage()
                        .contains("close failed")
        );
        assertFalse(iterator.hasNext());
    }

    @Test
    public void emptyReaderIsClosedDuringIteratorCreation() {
        Reader reader = new Reader(Collections.emptyList());

        Iterator<Integer> iterator =
                IDataReader.iterator(reader, 50);

        assertFalse(iterator.hasNext());
        assertEquals(1, reader.closeCount);
    }

    private static final class Reader
            implements IDataReader<Integer> {

        private final List<List<Integer>> pages;
        private int pageIndex;
        int closeCount;
        int failPage = -1;
        boolean failClose;

        private Reader(List<List<Integer>> pages) {
            this.pages = pages;
        }

        @Override
        public boolean isEOF() {
            return pageIndex >= pages.size();
        }

        @Override
        public List<Integer> getNextPage(int nRowCount) {
            if( pageIndex == failPage )
                throw new IllegalStateException("page failed");

            if( pageIndex >= pages.size() )
                return Collections.emptyList();

            return pages.get(pageIndex++);
        }

        @Override
        public void close() throws DataSetException {
            closeCount++;

            if( failClose )
                throw new DataSetException("close failed");
        }
    }
}
