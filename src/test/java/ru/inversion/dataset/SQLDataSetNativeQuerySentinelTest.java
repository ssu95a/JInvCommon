package ru.inversion.dataset;

import org.junit.Test;
import ru.inversion.dataset.impl.DataSetSelectBuilder;
import ru.inversion.dataset.mark.MarkColumns;
import ru.inversion.dataset.mark.RowIDMarkable;
import ru.inversion.dataset.parser.jdbc.Parameter;
import ru.inversion.dataset.parser.jdbc.PreparedSql;
import ru.inversion.db.dialect.SqlDialectFactory;
import ru.inversion.tc.TaskContext;

import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.NamedNativeQueries;
import javax.persistence.NamedNativeQuery;
import javax.persistence.Table;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
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

    @Test
    public void tableAliasFollowsTableName() throws Exception {
        SQLDataSet<ContainerRow> ds = new SQLDataSet<>(ContainerRow.class);
        ds.setTaskContext(oracleTaskContext());
        ds.setQueryAlias("entry");
        ds.setWherePredicat("entry.IDSM_ENTRY = :id");

        String sql = ds.createDataSetSelectBuilder(false).buildSql();
        assertTrue(sql, sql.startsWith("SELECT entry.IDSM_ENTRY FROM V_SM_003_MF entry"));
        assertTrue(sql, sql.contains("WHERE ((entry.IDSM_ENTRY = :id"));
    }

    @Test
    public void simpleNativeQueryKeepsItsFromClause() throws Exception {
        SQLDataSet<JoinedRow> ds = new SQLDataSet<>(JoinedRow.class);
        ds.setTaskContext(oracleTaskContext());
        ds.setNativeQueryName("simple");
        ds.setQueryAlias("entry");
        ds.setWherePredicat("entry.id = :id");

        String sql = ds.createDataSetSelectBuilder(false).buildSql();
        assertTrue(sql, sql.startsWith("select entry.id from schema_name.entry_table entry"));
        assertTrue(sql, sql.contains("WHERE ((entry.id = :id"));
    }

    @Test
    public void simpleNativeQueryAddsExplicitAliasToTable() throws Exception {
        SQLDataSet<Row> ds = new SQLDataSet<>(Row.class);
        ds.setTaskContext(oracleTaskContext());
        assertEquals("select ID from NATIVE_SOURCE", ds.createDataSetSelectBuilder(false).buildSql());

        ds.setQueryAlias("qrslt");
        ds.setWherePredicat("qrslt.id = :id");
        String sql = ds.createDataSetSelectBuilder(false).buildSql();
        assertTrue(sql, sql.startsWith("select ID from NATIVE_SOURCE qrslt"));
        assertTrue(sql, sql.contains("qrslt.id = :id"));

        ds.setQueryAlias("entry");
        ds.setWherePredicat("entry.id = :id");
        sql = ds.createDataSetSelectBuilder(false).buildSql();
        assertTrue(sql, sql.startsWith("select ID from NATIVE_SOURCE entry"));
        assertFalse(sql, sql.contains("qrslt"));
    }

    @Test
    public void simpleNativeQueryRejectsConflictingSourceAlias() throws Exception {
        SQLDataSet<JoinedRow> ds = new SQLDataSet<>(JoinedRow.class);
        ds.setTaskContext(oracleTaskContext());
        ds.setNativeQueryName("simple");
        ds.setQueryAlias("other");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ds.createDataSetSelectBuilder(false).buildSql());
        assertTrue(error.getMessage(), error.getMessage().contains("SQL source alias entry"));
    }

    @Test
    public void simpleQueryRejectsAliasThatWouldHideQualifiedTableName() throws Exception {
        SQLDataSet<Row> ds = new SQLDataSet<>(Row.class);
        ds.setTaskContext(oracleTaskContext());
        ds.setSQL("select entry_table.id from schema_name.entry_table");
        ds.setQueryAlias("other");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ds.createDataSetSelectBuilder(false).buildSql());
        assertTrue(error.getMessage(), error.getMessage().contains("hide SQL source name entry_table"));
    }

    @Test
    public void afterOrderByAddsSqlAndTakesPrecedenceOverRowLimit() throws Exception {
        SQLDataSet<Row> ds = new SQLDataSet<>(Row.class);
        ds.setTaskContext(oracleTaskContext());
        ds.setSQL("select ID from NATIVE_SOURCE where ID > :minimum");
        ds.setOrderBy("ID DESC");
        ds.setRowLimit(5);
        ds.needReCreateReader.setValue(false);
        ds.setAfterOrderBy("  OFFSET :skip ROWS FETCH NEXT :take ROWS ONLY  ");
        assertTrue(ds.needReCreateReader.getValue());
        assertEquals("OFFSET :skip ROWS FETCH NEXT :take ROWS ONLY", ds.getAfterOrderBy());

        PreparedSql prepared = ds.prepareDataQuery();
        String sql = prepared.getSql();
        assertTrue(sql, sql.contains("ORDER BY ID DESC"));
        assertTrue(sql, sql.indexOf("ORDER BY ID DESC") < sql.indexOf("OFFSET ? ROWS FETCH NEXT ? ROWS ONLY"));
        assertFalse(sql, sql.contains("FETCH FIRST"));
        assertEquals(3, prepared.getParameters().size());
        assertEquals("minimum", prepared.getParameters().get(0).getName());
        assertEquals("skip", prepared.getParameters().get(1).getName());
        assertEquals("take", prepared.getParameters().get(2).getName());
        assertTrue(ds.createAggregateSourceBuilder(false).prepare().getSql().contains("ORDER BY ID DESC"));

        ds.needReCreateReader.setValue(false);
        ds.setAfterOrderBy(ds.getAfterOrderBy());
        assertFalse(ds.needReCreateReader.getValue());
        ds.setAfterOrderBy(" ");
        assertTrue(ds.needReCreateReader.getValue());
        assertNull(ds.getAfterOrderBy());
        String cleared = ds.prepareDataQuery().getSql();
        assertTrue(cleared, cleared.contains("FETCH FIRST ? ROWS ONLY"));
        assertFalse(cleared, cleared.contains("OFFSET"));
    }

    @Test
    public void changingOrderReusesPreparedBodyAndRestoresSourceOrder() throws Exception {
        SQLDataSet<Row> ds = new SQLDataSet<>(Row.class);
        ds.setTaskContext(oracleTaskContext());
        ds.setSQL("select id from items where category = :category "
                + "order by case when id = :original then 1 else 2 end "
                + "offset :skip rows fetch next :take rows only");

        PreparedSql original = ds.prepareDataQuery();
        ds.setOrderBy("1, case when id = :replacement then 2 else 1 end");
        PreparedSql sorted = ds.prepareDataQuery();

        assertTrue(sorted.getSql(), sorted.getSql().contains("ORDER BY 1, case when id = ? then 2 else 1 end"));
        assertEquals(4, sorted.getParameters().size());
        assertSame(original.getParameters().get(0), sorted.getParameters().get(0));
        assertEquals("replacement", sorted.getParameters().get(1).getName());
        assertSame(original.getParameters().get(2), sorted.getParameters().get(2));
        assertSame(original.getParameters().get(3), sorted.getParameters().get(3));

        ds.setOrderBy(null);
        PreparedSql restored = ds.prepareDataQuery();
        assertEquals(original.getSql(), restored.getSql());
        assertEquals(original.getParameters(), restored.getParameters());

        ds.setWherePredicat("id <> :excluded");
        ds.setOrderBy("id DESC");
        PreparedSql filtered = ds.prepareDataQuery();
        assertEquals("excluded", filtered.getParameters().get(0).getName());
        assertEquals("category", filtered.getParameters().get(1).getName());
        assertNotSame(original.getParameters().get(0), filtered.getParameters().get(1));
    }

    @Test
    public void legacyOrderByPreservesDateLiterals() throws Exception {
        SQLDataSet<Row> ds = new SQLDataSet<>(Row.class);
        ds.setTaskContext(oracleTaskContext());
        ds.setSQL("SELECT dTrcDoc, cNalFlag, iTrcPriority, dTrcCreate, iTrcNum, iTrcANum FROM XTRC");
        ds.setWherePredicat("iTrcNum >= :minNum");
        String order = "CASE WHEN dTrcDoc < Date '2013-12-14' AND cNalFlag IS NOT NULL THEN 5 "
                + "WHEN dTrcDoc < Date '2013-12-14' AND iTrcPriority > 3 THEN iTrcPriority - 1 "
                + "ELSE iTrcPriority END, dTrcCreate, iTrcNum, iTrcANum";
        ds.setOrderBy("~" + order + "~");

        PreparedSql prepared = ds.createDataSetSelectBuilder(false).prepare();
        String sql = prepared.getSql();
        assertTrue(sql, sql.contains("ORDER BY " + order));
        assertFalse(sql, sql.contains("~"));
        assertEquals(1, prepared.getParameters().size());
        assertEquals("minNum", prepared.getParameters().get(0).getName());
    }

    @Test
    public void queryPreparationFailurePrintsSourceAndOrderByInStackTrace() throws Exception {
        SQLDataSet<Row> ds = new SQLDataSet<>(Row.class);
        ds.setTaskContext(oracleTaskContext());
        String sql = "SELECT id, due_date FROM NATIVE_SOURCE WHERE id = :id";
        String order = "CASE WHEN due_date = TIMESTAMP '1999-12-31 00:00:00' THEN 1 ELSE 0 END";
        ds.setSQL(sql);
        ds.setOrderBy(order);
        ds.setParameter("id", "runtime-bind-value");

        DataSetException error = assertThrows(DataSetException.class, () -> ds.executeQuery());
        StringWriter output = new StringWriter();
        error.printStackTrace(new PrintWriter(output));
        String trace = output.toString();
        assertTrue(trace, trace.contains("Unsupported quoted literal at offset 21"));
        assertTrue(trace, trace.contains("Source SQL\n" + sql));
        assertTrue(trace, trace.contains("ORDER BY fragment\n" + order));
        assertFalse(trace, trace.contains("runtime-bind-value"));
    }

    @Test
    public void joinedNativeQueryHasLegacyResultAliasBeforeAddingPredicates() throws Exception {
        SQLDataSet<JoinedRow> ds = new SQLDataSet<>(JoinedRow.class);
        ds.setTaskContext(oracleTaskContext());
        ds.setNativeQueryName("joined");

        String sql = ds.createDataSetSelectBuilder(false).buildSql();
        assertTrue(sql, sql.startsWith("SELECT qrslt.* FROM ("));
        assertTrue(sql, sql.endsWith(") qrslt"));

        ds.setWherePredicat("not exists (select 1 from bsmx where bsmx.kodbsxo = qrslt.kodbsxo and bsmx.type = :type)");
        ds.setOrderBy("namexo desc");
        PreparedSql prepared = ds.createDataSetSelectBuilder(false).prepare();
        sql = prepared.getSql();
        assertTrue(sql, sql.contains(") qrslt WHERE ((not exists"));
        assertTrue(sql, sql.indexOf("bsxo.branch = ?") >= 0 && sql.indexOf("bsxo.branch = ?") < sql.indexOf(") qrslt"));
        assertTrue(sql, sql.indexOf("qrslt.kodbsxo") < sql.indexOf("ORDER BY namexo desc"));
        assertEquals("branch", prepared.getParameters().get(0).getName());
        assertEquals("type", prepared.getParameters().get(2).getName());

        ds.setQueryAlias("entry");
        ds.setWherePredicat("entry.kodbsxo = :id");
        sql = ds.createDataSetSelectBuilder(false).buildSql();
        assertTrue(sql, sql.startsWith("SELECT entry.* FROM ("));
        assertTrue(sql, sql.contains(") entry WHERE ((entry.kodbsxo = :id"));
    }

    @Test
    public void rawQueryKeepsExplicitLegacyWhereLocationInsideWrapper() throws Exception {
        SQLDataSet<Row> ds = new SQLDataSet<>(Row.class);
        ds.setTaskContext(oracleTaskContext());
        ds.setSQL("select a.id from a, b where a.id = b.id <w>;");
        ds.setWherePredicat("b.name = :name");

        String sql = ds.createDataSetSelectBuilder(false).buildSql();
        assertTrue(sql, sql.startsWith("SELECT qrslt.* FROM ("));
        assertTrue(sql, sql.indexOf("b.name = :name") >= 0 && sql.indexOf("b.name = :name") < sql.indexOf(") qrslt"));
        assertFalse(sql, sql.contains("<w>"));
        assertFalse(sql, sql.contains(";"));
    }

    @Test
    public void rowIdMarkReadsRowIdFromDataSetScope() throws Exception {
        XXIDataSet<MarkedRow> ds = new XXIDataSet<>(MarkedRow.class);
        ds.setTaskContext(oracleTaskContext());
        ds.setMarkerID(17L);
        ds.setSQL("select bsnu.rowid, kodbsnu from bsnu where typenu = :type order by typenu, kodbsnu");

        PreparedSql prepared = ds.prepareDataQuery();
        String sql = prepared.getSql();
        assertTrue(sql, sql.contains("CASE WHEN ROWID in ( select a$.RMRKROWID from v_jf_mrk a$"));
        assertTrue(sql, sql.contains("a$.IMRKMARKERID = ?"));
        assertTrue(sql, sql.contains("AS MARK"));
    }

    @Test
    public void rowIdMarkFiltersKeepTheWrappedSourceAlias() throws Exception {
        XXIDataSet<MarkedRow> ds = new XXIDataSet<>(MarkedRow.class);
        ds.setTaskContext(oracleTaskContext());
        ds.setMarkerID(17L);
        ds.setSQL("select bsnu.rowid, bsnu.kodbsnu from bsnu join bsdv on bsdv.kodbsdv = bsnu.kodbsdv");
        ds.setQueryAlias("items");

        String expected = "items.ROWID in ( select a$.RMRKROWID from v_jf_mrk a$";
        String iterator = ds.createMarkedRSIteratorBuilder(false).buildSql();
        assertTrue(iterator, iterator.contains(expected));
        String aggregate = ds.createMarkedAggregateSourceBuilder(Collections.emptyList(), SqlDialectFactory.ORACLE).buildSql();
        assertTrue(aggregate, aggregate.contains(expected));
    }

    @MarkColumns(columns = {"ROWID"})
    public static final class MarkedRow extends RowIDMarkable {
        @Override
        public String getMarkRowID() {
            return null;
        }
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

    @NamedNativeQueries({
            @NamedNativeQuery(name = "simple", query = "select entry.id from schema_name.entry_table entry"),
            @NamedNativeQuery(name = "joined", query = "select bsxo.kodbsxo, bsxo.name namexo "
                    + "from bsxo left join bsos on bsos.id = bsxo.id, bdxs "
                    + "where bsxo.branch = :branch and bsxo.kodbdxs = bdxs.kodbdxs")
    })
    public static final class JoinedRow {
    }
}
