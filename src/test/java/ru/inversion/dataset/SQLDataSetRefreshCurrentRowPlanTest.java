package ru.inversion.dataset;

import org.junit.Test;
import ru.inversion.db.dialect.SqlDialectFactory;
import ru.inversion.dataset.parser.jdbc.Parameter;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class SQLDataSetRefreshCurrentRowPlanTest {

    @Test
    public void pkBindDoesNotOverrideSameNamedSourceParameter() {
        TestDataSet ds = new TestDataSet();
        ds.setSQL(
                "select ID, TENANT_ID from T "
                        + "where TENANT_ID = :ID"
        );

        SQLDataSet.RefreshCurrentRowPlan plan =
                ds.createRefreshCurrentRowPlan(
                        Collections.singletonList("ID"),
                        SqlDialectFactory.POSTGRES
                );

        assertEquals(2, plan.preparedSql.getParameters().size());

        Parameter pk =
                plan.preparedSql.getParameters().get(0);
        Parameter source =
                plan.preparedSql.getParameters().get(1);

        assertEquals(Parameter.Kind.NAMED, pk.getKind());
        assertEquals(Parameter.Kind.NAMED, source.getKind());
        assertNotEquals("ID", pk.getName());
        assertEquals("ID", source.getName());

        SQLExpression primary = new SQLExpression();
        primary.setParameter("ID", 900);

        assertEquals(
                Arrays.<Object>asList(42, 900),
                SQLDataSet.refreshCurrentRowJdbcValues(
                        plan,
                        Collections.<Object>singletonList(42),
                        primary
                )
        );
    }

    @Test
    public void generatedPkBindAvoidsInternalNameCollisionToo() {
        TestDataSet ds = new TestDataSet();
        ds.setSQL(
                "select ID from T "
                        + "where tenant_id = :jinv_refresh_pk_0"
        );

        SQLDataSet.RefreshCurrentRowPlan plan =
                ds.createRefreshCurrentRowPlan(
                        Collections.singletonList("ID"),
                        SqlDialectFactory.POSTGRES
                );

        assertEquals(
                "jinv_refresh_pk_0_1",
                plan.idParameterNames.get(0)
        );

        SQLExpression primary = new SQLExpression();
        primary.setParameter("jinv_refresh_pk_0", 700);

        assertEquals(
                Arrays.<Object>asList(42, 700),
                SQLDataSet.refreshCurrentRowJdbcValues(
                        plan,
                        Collections.<Object>singletonList(42),
                        primary
                )
        );
    }

    @Test
    public void explicitNullPkStillWinsOverPrimaryParameter() {
        TestDataSet ds = new TestDataSet();
        ds.setSQL("select ID from T");

        SQLDataSet.RefreshCurrentRowPlan plan =
                ds.createRefreshCurrentRowPlan(
                        Collections.singletonList("ID"),
                        SqlDialectFactory.POSTGRES
                );

        SQLExpression primary = new SQLExpression();
        primary.setParameter(
                plan.idParameterNames.get(0),
                999
        );

        List<Object> values =
                SQLDataSet.refreshCurrentRowJdbcValues(
                        plan,
                        Collections.singletonList(null),
                        primary
                );

        assertEquals(1, values.size());
        assertNull(values.get(0));
    }

    @Test
    public void explicitQueryAliasIsUsedForPkPredicate() {
        TestDataSet ds = new TestDataSet();
        ds.setSQL("select ID, AMOUNT from T");
        ds.setQueryAlias("q");

        SQLDataSet.RefreshCurrentRowPlan plan =
                ds.createRefreshCurrentRowPlan(
                        Collections.singletonList("ID"),
                        SqlDialectFactory.POSTGRES
                );

        assertTrue(
                plan.sql,
                plan.sql.contains("SELECT q.* FROM (")
        );
        assertTrue(
                plan.sql,
                plan.sql.contains(") q")
        );
        String pkParameter = plan.idParameterNames.get(0);

        assertTrue(
                plan.sql,
                plan.sql.contains(
                        "q.ID = :" + pkParameter)
        );
        assertTrue(
                plan.preparedSql.getSql(),
                plan.preparedSql.getSql().contains("q.ID = ?")
        );
    }

    @Test
    public void compositePkValuesFollowMetadataOrder() {
        TestDataSet ds = new TestDataSet();
        ds.setSQL("select ID1, ID2 from T");

        SQLDataSet.RefreshCurrentRowPlan plan =
                ds.createRefreshCurrentRowPlan(
                        Arrays.asList("ID2", "ID1"),
                        SqlDialectFactory.POSTGRES
                );

        assertEquals(2, plan.idParameterNames.size());

        assertEquals(
                Arrays.<Object>asList(20, 10),
                SQLDataSet.refreshCurrentRowJdbcValues(
                        plan,
                        Arrays.<Object>asList(20, 10),
                        new SQLExpression()
                )
        );
    }

    private static final class TestDataSet
            extends SQLDataSet<Object> {
    }

    @Test
    public void optimizerHintIsPreservedInRefreshPlan() {
        TestDataSet ds = new TestDataSet();
        ds.setSQL("select ID, AMOUNT from T");
        ds.setQueryAlias("q");
        ds.setOptimizerHint("+ INDEX(q IDX_T_ID)");

        SQLDataSet.RefreshCurrentRowPlan plan =
                ds.createRefreshCurrentRowPlan(
                        Collections.singletonList("ID"),
                        SqlDialectFactory.POSTGRES
                );

        assertTrue(
                plan.sql,
                plan.sql.contains(
                        "SELECT /*+ INDEX(q IDX_T_ID)*/  q.*"
                )
        );
        assertEquals(1, plan.preparedSql.getParameters().size());
        assertEquals(
                plan.idParameterNames.get(0),
                plan.preparedSql.getParameters().get(0).getName()
        );
    }

    @Test
    public void refreshPlanDoesNotInheritFiltersOrderOrRowLimit() {
        TestDataSet ds = new TestDataSet();
        ds.setSQL("select ID, AMOUNT from T");
        ds.setFilter("AMOUNT > 10", true, true);
        ds.setOrderBy("AMOUNT desc");
        ds.setRowLimit(1);

        SQLDataSet.RefreshCurrentRowPlan plan =
                ds.createRefreshCurrentRowPlan(
                        Collections.singletonList("ID"),
                        SqlDialectFactory.POSTGRES
                );

        String sql = plan.sql.toUpperCase(java.util.Locale.ROOT);

        assertFalse(plan.sql, sql.contains("AMOUNT > 10"));
        assertFalse(plan.sql, sql.contains("ORDER BY"));
        assertFalse(plan.sql, sql.contains("LIMIT"));
        assertFalse(plan.sql, sql.contains("FETCH FIRST"));
    }

}
