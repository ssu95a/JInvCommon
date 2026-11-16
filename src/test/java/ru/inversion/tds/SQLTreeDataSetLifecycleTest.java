package ru.inversion.tds;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static ru.inversion.tds.SQLTreeDataSet.PredicateTypeEnum.AUTO_FILTER;

class SQLTreeDataSetLifecycleTest {

    private static final class TestTreeDataSet
            extends SQLTreeDataSet<Object> {

        int beforeFirstExecuteCount;
        boolean failBeforeFirstExecute;
        boolean failPrepare;

        @Override
        protected void onBeforeFirstExecute() {
            beforeFirstExecuteCount++;
            predicates.put(
                    -1,
                    new PredicateItem("enabled = 1", AUTO_FILTER)
            );

            if (failBeforeFirstExecute)
                throw new IllegalStateException("initialization failed");
        }

        @Override
        protected String prepareCompleteSql() {
            if (failPrepare)
                throw new TreeDataSetException("prepare failed");

            return "select 1";
        }

        boolean hasAutoFilter() {
            return predicates.containsKey(-1);
        }
    }

    @Test
    void failedFirstInitializationIsRetriedWithoutPendingPredicate() {
        TestTreeDataSet ds = new TestTreeDataSet();
        ds.failBeforeFirstExecute = true;

        assertThrows(TreeDataSetException.class, ds::executeQuery);

        assertEquals(1, ds.beforeFirstExecuteCount);
        assertNull(ds.getProperty("ts.execute_count"));
        assertFalse(ds.hasAutoFilter());

        ds.failBeforeFirstExecute = false;
        ds.failPrepare = true;

        assertThrows(TreeDataSetException.class, ds::executeQuery);

        assertEquals(2, ds.beforeFirstExecuteCount);
        assertNull(ds.getProperty("ts.execute_count"));
        assertFalse(ds.hasAutoFilter());
    }
}
