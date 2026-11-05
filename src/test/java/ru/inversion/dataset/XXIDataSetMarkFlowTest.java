package ru.inversion.dataset;

import org.junit.Test;
import ru.inversion.dataset.impl.DataSetSelectBuilder;
import ru.inversion.db.dialect.SqlDialectFactory;
import ru.inversion.dataset.mark.IIDMarkable;
import ru.inversion.dataset.mark.IUMarkable;
import ru.inversion.utils.sparser.jdbc.PreparedSql;

import javax.persistence.Column;
import javax.persistence.Id;
import java.lang.reflect.Method;
import java.util.Iterator;

import static org.junit.Assert.*;

public class XXIDataSetMarkFlowTest {

    @Test
    public void markSqlFollowsCurrentRowClassWithoutStaleCache() {
        TestDataSet ds = new TestDataSet();
        ds.setMarkerID(10L);

        ds.setRowClass(IdRow.class);
        String idSql = ds.markSql();

        assertTrue(idSql, idSql.contains("v_jf_mrk_id"));
        assertTrue(idSql, idSql.contains("ID_A"));

        ds.setRowClass(URow.class);
        String uSql = ds.markSql();

        assertTrue(uSql, uSql.contains("v_jf_mrk_u"));
        assertTrue(uSql, uSql.contains("CODE_B"));
        assertFalse(uSql, uSql.contains("ID_A"));
    }

    @Test
    public void unsupportedMarkIteratorIsEmptyEvenWhenRowsExist() {
        TestDataSet ds = new TestDataSet();
        ds.addTestRow(new Object());

        Iterator<Object> iterator =
                ds.getMarkedRowIterator(null);

        assertFalse(iterator.hasNext());
    }

    @Test
    public void markSourceKeepsRuntimeOrderWhenRowLimitIsActive()
            throws Exception {
        TestDataSet ds = new TestDataSet();
        ds.setRowLimit(5);

        PreparedSql prepared = markSource(ds).prepare();

        assertTrue(
                prepared.getSql(),
                prepared.getSql().contains("ORDER BY rank DESC")
        );
        assertTrue(
                prepared.getSql(),
                prepared.getSql().contains("LIMIT ?")
        );
    }

    @Test
    public void markSourceDropsRuntimeOrderWhenUnbounded()
            throws Exception {
        TestDataSet ds = new TestDataSet();

        PreparedSql prepared = markSource(ds).prepare();

        assertFalse(
                prepared.getSql(),
                prepared.getSql().contains("ORDER BY rank DESC")
        );
    }

    private static DataSetSelectBuilder markSource(TestDataSet ds)
            throws Exception {
        Method method = XXIDataSet.class.getDeclaredMethod(
                "createMarkSourceBuilder");
        method.setAccessible(true);

        return (DataSetSelectBuilder)method.invoke(ds);
    }

    private static final class TestDataSet
            extends XXIDataSet<Object> {

        void addTestRow(Object row) {
            rows().add(row);
        }

        String markSql() {
            return getMarkSQLPart();
        }

        @Override
        protected DataSetSelectBuilder createDataSetSelectBuilder(
                boolean ignoreFilters
        ) {
            return new DataSetSelectBuilder(
                    "select id, rank from t",
                    SqlDialectFactory.POSTGRES
            )
                    .orderBy("rank DESC")
                    .rowLimit(getRowLimit())
                    .mark("0");
        }
    }

    public static final class IdRow
            implements IIDMarkable {
        private boolean mark;

        @Override
        public boolean isMark() {
            return mark;
        }

        @Override
        public void setMark(boolean mark) {
            this.mark = mark;
        }

        @Id
        @Column(name = "ID_A")
        @Override
        public Long getMarkLongID() {
            return 1L;
        }
    }

    public static final class URow
            implements IUMarkable {
        private boolean mark;

        @Override
        public boolean isMark() {
            return mark;
        }

        @Override
        public void setMark(boolean mark) {
            this.mark = mark;
        }

        @Id
        @Column(name = "CODE_B")
        @Override
        public String getMarkStringID() {
            return "B";
        }
    }
}
