package ru.inversion.tds;

import org.junit.jupiter.api.Test;

import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.NamedNativeQuery;
import javax.persistence.Table;

import java.util.Map;

import ru.inversion.db.dialect.SqlDialectFactory;
import ru.inversion.utils.sparser.jdbc.PreparedSql;
import ru.inversion.utils.sparser.select.SqlSelect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    private static final class ExposedTreeDataSet<P>
            extends SQLTreeDataSet<P> {

        ExposedTreeDataSet(Class<? super P> rowClass) {
            super(rowClass);
        }

        String completeSql() {
            return prepareCompleteSql();
        }

        Object parameter(int index) {
            return getParameterValue(index);
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

    @Test
    void structuralTextSettersRejectWhitespaceOnlyWithoutRewritingText() {
        SQLTreeDataSet<Object> ds = new SQLTreeDataSet<>();

        ds.setQueryAlias("  q  ");
        ds.setOrderBy("  ID desc  ");

        assertEquals("  q  ", ds.getQueryAlias());
        assertEquals("  ID desc  ", ds.getOrderBy());

        ds.setQueryAlias("   ");
        ds.setOrderBy("   ");

        assertNull(ds.getQueryAlias());
        assertNull(ds.getOrderBy());
    }

    @Test
    void missingNativeQueryFailsBeforeInvalidSqlIsBuilt() {
        ExposedTreeDataSet<TableOnlyRow> ds =
                new ExposedTreeDataSet<>(TableOnlyRow.class);
        ds.setNativeQueryName("missing.query");

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                ds::completeSql
        );

        assertTrue(failure.getMessage().contains("missing.query"));
        assertTrue(failure.getMessage().contains(TableOnlyRow.class.getName()));
    }

    @Test
    void missingIndexedParameterReportsItsIndex() {
        ExposedTreeDataSet<Object> ds =
                new ExposedTreeDataSet<>(Object.class);

        TreeDataSetException failure = assertThrows(
                TreeDataSetException.class,
                () -> ds.parameter(7)
        );

        assertTrue(failure.getMessage().contains("7"));
    }

    @Test
    void postgresRegexOperatorInOrderByReachesDialectParser() {
        ExposedTreeDataSet<TableOnlyRow> ds =
                new ExposedTreeDataSet<>(TableOnlyRow.class);
        ds.setNativeQueryName("tree.query");
        ds.setOrderBy("(CODE ~ :pattern) desc");

        PreparedSql prepared = new SqlSelect(
                ds.completeSql(),
                SqlDialectFactory.POSTGRES
        ).prepare();

        assertTrue(prepared.getSql().contains("CODE ~ ?"));
        assertEquals(
                "pattern",
                prepared.getParameters().get(0).getName()
        );
    }

    @Test
    void pairedLegacyOrderByMarkersAreStillRemovedByParser() {
        ExposedTreeDataSet<TableOnlyRow> ds =
                new ExposedTreeDataSet<>(TableOnlyRow.class);
        ds.setNativeQueryName("tree.query");
        ds.setOrderBy("~replace(CODE, 'a', 'b')~");

        PreparedSql prepared = new SqlSelect(
                ds.completeSql(),
                SqlDialectFactory.POSTGRES
        ).prepare();

        assertFalse(prepared.getSql().contains("~"));
        assertTrue(
                prepared.getSql().contains(
                        "replace(CODE, 'a', 'b')"
                )
        );
    }

    @Test
    void dumpNullIsNoOp() {
        SQLTreeDataSet<Object> ds = new SQLTreeDataSet<>();

        assertDoesNotThrow(() -> ds.dump((Map<String,Object>) null));
    }

    @Table(name = "TREE_ROW")
    @NamedNativeQuery(
            name = "tree.query",
            query = "select ID, CODE from TREE_ROW"
    )
    private static final class TableOnlyRow {
        private Long id;

        @Id
        @Column(name = "ID")
        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }
    }
}
