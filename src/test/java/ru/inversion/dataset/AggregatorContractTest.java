package ru.inversion.dataset;

import org.junit.Test;
import ru.inversion.dataset.aggr.AggrFuncEnum;
import ru.inversion.dataset.aggr.IAggregator;
import ru.inversion.dataset.aggr.IAggregatorBuilder;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.Assert.*;

public class AggregatorContractTest {

    @Test(expected = IllegalStateException.class)
    public void ordinaryAggregatorRejectsEmptyDefinition()
            throws Exception {
        new SQLDataSet<Object>()
                .createAggregatorBuilder(false)
                .build();
    }

    @Test
    public void builderMutationDoesNotChangeBuiltAggregator()
            throws Exception {
        SQLDataSet<Object> ds = new SQLDataSet<>();
        IAggregatorBuilder<Object> builder =
                ds.createAggregatorBuilder(false);

        builder.add("id", AggrFuncEnum.COUNT);
        IAggregator<Object> aggregator = builder.build();

        builder.add("amount", AggrFuncEnum.SUM);

        Field field = aggregator.getClass()
                .getDeclaredField("aggrList");
        field.setAccessible(true);

        List<?> aggregates = (List<?>)field.get(aggregator);

        assertEquals(1, aggregates.size());

        aggregator.close();
    }

    @Test
    public void markedCountUsesGeneralAggregatorPath()
            throws Exception {
        XXIDataSet<Object> ds = new XXIDataSet<>();

        IAggregator<Object> aggregator =
                ds.createMarkedAggregatorBuilder(false)
                        .add("nullable_value", AggrFuncEnum.COUNT)
                        .build();

        assertTrue(
                aggregator.getClass().getSimpleName(),
                aggregator.getClass().getSimpleName()
                        .contains("MarkAggregator")
        );

        aggregator.close();
    }

    @Test
    public void quotedSqlAliasMapsToJdbcResultKey()
            throws Exception {
        Method method = SQLDataSet.class.getDeclaredMethod(
                "aggregateResultKey",
                String.class);
        method.setAccessible(true);

        assertEquals("A$id", method.invoke(null, "\"A$id\""));
        assertEquals("total", method.invoke(null, "total"));
    }

    @Test
    public void ordinaryCloseDetachesRefreshListener()
            throws Exception {
        TestSQLDataSet ds = new TestSQLDataSet();
        ds.setProperty("ds.execute_count", 1);

        IAggregator<Object> aggregator =
                ds.createAggregatorBuilder(true)
                        .add("id", AggrFuncEnum.COUNT)
                        .build();

        aggregator.addListener(source -> { });
        aggregator.close();
        aggregator.close();

        ds.fireAfterExecute();
    }

    @Test
    public void markedCloseDetachesDataSetAndMarkListeners()
            throws Exception {
        TestXXIDataSet ds = new TestXXIDataSet();
        ds.setRowClass(MarkRow.class);
        ds.setMarkerID(10L);

        IAggregator<Object> aggregator =
                ds.createMarkedAggregatorBuilder(true)
                        .add("id", AggrFuncEnum.SUM)
                        .build();

        aggregator.addListener(source -> { });
        aggregator.close();
        aggregator.close();

        ds.fireAfterExecute();
        ds.fireMarkAll();
    }

    @Test(expected = IllegalStateException.class)
    public void executeAfterCloseIsRejected()
            throws Exception {
        IAggregator<Object> aggregator =
                new SQLDataSet<Object>()
                        .createAggregatorBuilder(false)
                        .add("id", AggrFuncEnum.COUNT)
                        .build();

        aggregator.close();
        aggregator.execute();
    }

    private static final class TestSQLDataSet
            extends SQLDataSet<Object> {

        void fireAfterExecute() {
            fireDataSetEvent(
                    DataSetEvent.DataSetEventType.EXECUTE,
                    false);
        }
    }

    private static final class TestXXIDataSet
            extends XXIDataSet<Object> {

        void fireAfterExecute() {
            fireDataSetEvent(
                    DataSetEvent.DataSetEventType.EXECUTE,
                    false);
        }

        void fireMarkAll() {
            fireMarkDataSetEvent(
                    DataSetMarkEvent.MarkActionEnum.MARK_ALL);
        }
    }

    public static final class MarkRow
            implements ru.inversion.dataset.mark.IIDMarkable {

        @Override
        public boolean isMark() {
            return false;
        }

        @Override
        public void setMark(boolean mark) {
        }

        @javax.persistence.Id
        @javax.persistence.Column(name = "ID")
        @Override
        public Long getMarkLongID() {
            return 1L;
        }
    }


    @Test
    public void countOneIsGeneratedAsCountStar() {
        ru.inversion.utils.Triplet<String, AggrFuncEnum, String> aggregate =
                ru.inversion.utils.Triplet.makeTriplet(
                        "1",
                        AggrFuncEnum.COUNT,
                        "A$1"
                );

        assertEquals(
                "COUNT(*) A$1",
                SQLDataSet.aggregateSql(aggregate)
        );
    }

    @Test
    public void nonCountLiteralRemainsAvailableForParameterization() {
        ru.inversion.utils.Triplet<String, AggrFuncEnum, String> aggregate =
                ru.inversion.utils.Triplet.makeTriplet(
                        "1",
                        AggrFuncEnum.SUM,
                        "TOTAL"
                );

        assertEquals(
                "SUM (1) TOTAL",
                SQLDataSet.aggregateSql(aggregate)
        );
    }

    @Test
    public void markExistsUsesStructuralNullNotBoundOne()
            throws Exception {
        TestXXIDataSet ds = new TestXXIDataSet();
        ds.setRowClass(MarkRow.class);

        Method method = XXIDataSet.class.getDeclaredMethod(
                "markExistsPredicate",
                String.class
        );
        method.setAccessible(true);

        String predicate = (String)method.invoke(ds, "");

        assertTrue(predicate, predicate.contains("select NULL"));
        assertFalse(predicate, predicate.contains("select 1"));

        ru.inversion.utils.sparser.jdbc.PreparedSql prepared =
                new ru.inversion.utils.sparser.select.SqlSelect(
                        "select count(*) from t where " + predicate,
                        ru.inversion.db.dialect.SqlDialectFactory.POSTGRES
                ).prepare();

        assertEquals(1, prepared.getParameters().size());
        assertEquals(
                ru.inversion.utils.sparser.jdbc.Parameter.Kind.NAMED,
                prepared.getParameters().get(0).getKind()
        );
        assertEquals(
                "jinv_marker_id",
                prepared.getParameters().get(0).getName()
        );
    }


    @Test
    public void aggregateSourceKeepsSelectContextAndExpressionFallbacks() {
        SQLExpression fallback =
                new SQLExpression("q.id > ? and q.code = :code");
        fallback.setParameter(0, 7);
        fallback.setParameter("code", "X");

        ru.inversion.dataset.impl.DataSetSelectBuilder source =
                new ru.inversion.dataset.impl.DataSetSelectBuilder(
                        "select id, code, amount from t",
                        ru.inversion.db.dialect.SqlDialectFactory.POSTGRES
                )
                        .queryAlias("q")
                        .optimizerHint("+ AGG_TEST")
                        .addPredicate(fallback)
                        .orderBy("q.id DESC")
                        .rowLimit(5);

        java.util.List<ru.inversion.utils.Triplet<String, AggrFuncEnum, String>>
                aggregates = java.util.Collections.singletonList(
                        ru.inversion.utils.Triplet.makeTriplet(
                                "amount",
                                AggrFuncEnum.SUM,
                                "TOTAL"
                        )
                );

        ru.inversion.utils.sparser.jdbc.PreparedSql prepared =
                SQLDataSet.prepareAggregateSql(
                        aggregates,
                        source,
                        ru.inversion.db.dialect.SqlDialectFactory.POSTGRES
                );

        assertTrue(
                prepared.getSql(),
                prepared.getSql().contains("/*+ AGG_TEST*/")
        );
        assertTrue(
                prepared.getSql(),
                prepared.getSql().contains(") q")
        );
        assertTrue(
                prepared.getSql(),
                prepared.getSql().contains("ORDER BY q.id DESC")
        );
        assertTrue(
                prepared.getSql(),
                prepared.getSql().contains("LIMIT ?")
        );

        assertEquals(
                java.util.Arrays.<Object>asList(7, "X", 5),
                SQLDataSet.aggregateJdbcValues(
                        prepared,
                        source,
                        new SQLExpression()
                )
        );
    }

    @Test
    public void aggregatePrefixQuestionMarkOffsetsSourcePrimaryIndex() {
        SQLExpression fallback =
                new SQLExpression("id > ?");
        fallback.setParameter(0, 7);

        ru.inversion.dataset.impl.DataSetSelectBuilder source =
                new ru.inversion.dataset.impl.DataSetSelectBuilder(
                        "select id, amount from t",
                        ru.inversion.db.dialect.SqlDialectFactory.POSTGRES
                ).addPredicate(fallback);

        java.util.List<ru.inversion.utils.Triplet<String, AggrFuncEnum, String>>
                aggregates = java.util.Collections.singletonList(
                        ru.inversion.utils.Triplet.makeTriplet(
                                "?",
                                AggrFuncEnum.SUM,
                                "TOTAL"
                        )
                );

        ru.inversion.utils.sparser.jdbc.PreparedSql prepared =
                SQLDataSet.prepareAggregateSql(
                        aggregates,
                        source,
                        ru.inversion.db.dialect.SqlDialectFactory.POSTGRES
                );

        SQLExpression primary = new SQLExpression();
        primary.setParameter(0, 100);

        assertEquals(
                java.util.Arrays.<Object>asList(100, 7),
                SQLDataSet.aggregateJdbcValues(
                        prepared,
                        source,
                        primary
                )
        );

        primary.setParameter(1, 200);

        assertEquals(
                java.util.Arrays.<Object>asList(100, 200),
                SQLDataSet.aggregateJdbcValues(
                        prepared,
                        source,
                        primary
                )
        );
    }

    @Test
    public void aggregatePrefixDoesNotShiftSourceNumberedParameter() {
        SQLExpression fallback =
                new SQLExpression("id > $1");
        fallback.setParameter(0, 7);

        ru.inversion.dataset.impl.DataSetSelectBuilder source =
                new ru.inversion.dataset.impl.DataSetSelectBuilder(
                        "select id, amount from t",
                        ru.inversion.db.dialect.SqlDialectFactory.POSTGRES
                ).addPredicate(fallback);

        java.util.List<ru.inversion.utils.Triplet<String, AggrFuncEnum, String>>
                aggregates = java.util.Collections.singletonList(
                        ru.inversion.utils.Triplet.makeTriplet(
                                "?",
                                AggrFuncEnum.SUM,
                                "TOTAL"
                        )
                );

        ru.inversion.utils.sparser.jdbc.PreparedSql prepared =
                SQLDataSet.prepareAggregateSql(
                        aggregates,
                        source,
                        ru.inversion.db.dialect.SqlDialectFactory.POSTGRES
                );

        SQLExpression primary = new SQLExpression();
        primary.setParameter(0, 100);

        assertEquals(
                java.util.Arrays.<Object>asList(100, 100),
                SQLDataSet.aggregateJdbcValues(
                        prepared,
                        source,
                        primary
                )
        );
    }


    @Test
    public void markedAggregateUsesCommonPreparedComposition() {
        ru.inversion.dataset.impl.DataSetSelectBuilder source =
                new ru.inversion.dataset.impl.DataSetSelectBuilder(
                        "select id, amount from t",
                        ru.inversion.db.dialect.SqlDialectFactory.POSTGRES
                )
                        .addPredicate(
                                "exists (select NULL from marks "
                                        + "where marker_id = :jinv_marker_id)"
                        );

        java.util.List<ru.inversion.utils.Triplet<String, AggrFuncEnum, String>>
                aggregates = java.util.Collections.singletonList(
                        ru.inversion.utils.Triplet.makeTriplet(
                                "amount",
                                AggrFuncEnum.SUM,
                                "TOTAL"
                        )
                );

        ru.inversion.utils.sparser.jdbc.PreparedSql prepared =
                SQLDataSet.prepareAggregateSql(
                        aggregates,
                        source,
                        ru.inversion.db.dialect.SqlDialectFactory.POSTGRES
                );

        SQLExpression parameters = new SQLExpression();
        parameters.setParameter("jinv_marker_id", 77L);

        java.util.List<Object> values =
                SQLDataSet.aggregateJdbcValues(
                        prepared,
                        source,
                        parameters
                );

        assertEquals(
                java.util.Collections.<Object>singletonList(77),
                values
        );
        assertEquals(Integer.class, values.get(0).getClass());
    }

    @Test
    public void aggregateTableKeepsDirectTableSqlShape() {
        java.util.List<ru.inversion.utils.Triplet<String, AggrFuncEnum, String>>
                aggregates = java.util.Collections.singletonList(
                        ru.inversion.utils.Triplet.makeTriplet(
                                "AGGR_T.amount",
                                AggrFuncEnum.SUM,
                                "TOTAL"
                        )
                );

        ru.inversion.utils.sparser.jdbc.PreparedSql prepared =
                SQLDataSet.prepareAggregateTableSql(
                        aggregates,
                        "AGGR_T",
                        java.util.Arrays.asList(
                                "AGGR_T.kind = :kind",
                                "exists (select NULL from marks "
                                        + "where marks.id = AGGR_T.id "
                                        + "and marks.marker_id = :marker)"
                        ),
                        ru.inversion.db.dialect.SqlDialectFactory.POSTGRES
                );

        assertTrue(
                prepared.getSql(),
                prepared.getSql().contains(
                        "SUM (AGGR_T.amount) TOTAL FROM AGGR_T")
        );
        assertFalse(
                prepared.getSql(),
                prepared.getSql().contains("FROM (")
        );
        assertEquals(2, prepared.getParameters().size());
        assertEquals(
                "kind",
                prepared.getParameters().get(0).getName()
        );
        assertEquals(
                "marker",
                prepared.getParameters().get(1).getName()
        );
    }


    @Test
    public void aggregateDataSetRemainsUnsupportedCompatibilitySurface()
            throws Exception {
        java.lang.reflect.Method method =
                IAggregatorBuilder.class.getMethod("buildDataSet");

        assertTrue(method.isDefault());
        assertTrue(method.isAnnotationPresent(Deprecated.class));

        IAggregatorBuilder<Object> builder =
                new SQLDataSet<Object>()
                        .createAggregatorBuilder(false);

        try {
            builder.buildDataSet();
            fail("Expected unsupported aggregate DataSet");
        }
        catch( UnsupportedOperationException ex ) {
            assertEquals("Not supported yet.", ex.getMessage());
        }
    }

}
