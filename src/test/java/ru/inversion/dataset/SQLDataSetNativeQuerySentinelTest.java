package ru.inversion.dataset;

import org.junit.Test;
import ru.inversion.dataset.impl.DataSetSelectBuilder;
import ru.inversion.dataset.parser.jdbc.Parameter;
import ru.inversion.dataset.parser.jdbc.PreparedSql;
import ru.inversion.tc.TaskContext;

import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.NamedNativeQueries;
import javax.persistence.NamedNativeQuery;
import javax.persistence.Table;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class SQLDataSetNativeQuerySentinelTest {

    @Test
    public void unnamedSentinelUsesValueEquality() {
        SQLDataSet<Row> ds = new SQLDataSet<>(Row.class);

        ds.setNativeQueryName(
                new String(ISQLDataSet.QUERY_UNNAMED)
        );

        assertEquals(
                "select ID from NATIVE_SOURCE",
                ds.getSQL()
        );
    }

    @Test
    public void tableSentinelUsesValueEqualityAndSkipsNativeQuery() {
        SQLDataSet<Row> ds = new SQLDataSet<>(Row.class);

        ds.setNativeQueryName(
                new String(ISQLDataSet.QUERY_TABLE)
        );

        assertNull(ds.getSQL());
    }

    @Test
    public void unnamedDirectQueryWinsOverTable() {
        SQLDataSet<UnnamedRow> ds = new SQLDataSet<>(UnnamedRow.class);
        assertEquals("select ID from UNNAMED_SOURCE", ds.getSQL());
    }

    @Test
    public void singleNamedQueryInContainerDoesNotReplaceTableDefault() {
        SQLDataSet<ContainerRow> ds = new SQLDataSet<>(ContainerRow.class);
        assertNull(ds.getSQL());
        ds.setNativeQueryName("byEntryId");
        assertEquals(
                "select * from V_SM_003_MF where idsm_entry = ?",
                ds.getSQL()
        );
    }

    @Test
    public void tableDefaultResolvesNamedPredicateWithoutEntryId() throws Exception {
        SQLDataSet<ContainerRow> ds = new SQLDataSet<>(ContainerRow.class);
        ds.setTaskContext(oracleTaskContext());
        ds.setWherePredicat(":batchId is null or IdSM_Batch = :batchId");
        ds.setOrderBy("DTCREATE DESC");

        AtomicReference<Long> batchId = new AtomicReference<>(4000000000L);
        ds.setCallbackParameters((ParametersByName)name -> batchId.get());

        assertNull(ds.getSQL());
        DataSetSelectBuilder builder = ds.createDataSetSelectBuilder(false);
        PreparedSql prepared = builder.prepare();
        String sql = prepared.getSql().toUpperCase(Locale.ROOT);

        assertTrue(sql, sql.contains("FROM V_SM_003_MF"));
        assertTrue(sql, sql.contains("IDSM_BATCH = ?"));
        assertTrue(sql, sql.contains("ORDER BY DTCREATE DESC"));
        assertFalse(sql, sql.contains("WHERE IDSM_ENTRY = ?"));
        assertEquals(2, prepared.getParameters().size());
        for (Parameter parameter : prepared.getParameters()) {
            assertEquals(Parameter.Kind.NAMED, parameter.getKind());
            assertEquals("batchId", parameter.getName());
        }

        assertEquals(Arrays.<Object>asList(4000000000L, 4000000000L),
                builder.jdbcValues(ds.mainSqlParameters));

        batchId.set(null);
        assertEquals(Arrays.<Object>asList(null, null),
                builder.jdbcValues(ds.mainSqlParameters));
    }

    private static TaskContext oracleTaskContext() throws Exception {
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field singleton = unsafeClass.getDeclaredField("theUnsafe");
        singleton.setAccessible(true);
        Object unsafe = singleton.get(null);
        Method allocateInstance = unsafeClass.getMethod(
                "allocateInstance", Class.class);
        TaskContext tc = (TaskContext)allocateInstance.invoke(
                unsafe, TaskContext.class);
        tc.setProperty("ru.inversion.db_prod_name", 1);
        return tc;
    }

    @Table(name = "UNNAMED_TABLE")
    @NamedNativeQuery(name = "", query = "select ID from UNNAMED_SOURCE")
    public static final class UnnamedRow {
    }

    @Table(name = "V_SM_003_MF")
    @NamedNativeQueries({
            @NamedNativeQuery(
                    name = "byEntryId",
                    query = "select * from V_SM_003_MF where idsm_entry = ?")
    })
    public static final class ContainerRow {

        private Long id;

        @Id
        @Column(name = "IDSM_ENTRY")
        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }
    }

    @Table(name = "TABLE_SOURCE")
    @NamedNativeQuery(
            name = "row.native",
            query = "select ID from NATIVE_SOURCE"
    )
    public static final class Row {

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
