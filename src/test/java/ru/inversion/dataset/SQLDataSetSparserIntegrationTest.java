package ru.inversion.dataset;

import org.junit.Test;
import ru.inversion.dataset.impl.DataSetSelectBuilder;
import ru.inversion.db.dialect.SqlDialectFactory;
import ru.inversion.dataset.parser.jdbc.Parameter;
import ru.inversion.dataset.parser.jdbc.PreparedSql;
import ru.inversion.dataset.parser.lexer.OracleSqlScanner;
import ru.inversion.dataset.parser.lexer.PostgresSqlScanner;
import ru.inversion.utils.ParametersValues;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/** Контракт регистрации predicates и их источников значений, без подключения к БД. */
public class SQLDataSetSparserIntegrationTest {

    @Test
    public void expressionIsStoredWithoutInliningItsValues() throws Exception {
        SQLDataSet<Object[]> ds = new SQLDataSet<>(null, Object[].class);
        SQLExpression expression = new SQLExpression("branch = :branch");
        expression.setParameter("branch", 17);

        int id = ds.setFilter(expression, false, false);
        ISQLDataSet.PredicateItem item = ds.predicates().get(id);

        assertNotNull(item);
        assertSame(expression, item.getExpression());
        assertEquals("branch = :branch", item.getFilterStr());
        assertTrue(item.isPre());
        assertTrue(item.isTemporary());

        expression.setParameter("branch", 25);
        assertEquals("branch = :branch", item.getFilterStr());
        assertEquals(25, item.getExpression().getParameter("branch"));
    }

    @Test
    public void stringPredicateRemainsSupported() {
        SQLDataSet<Object[]> ds = new SQLDataSet<>(null, Object[].class);
        int id = ds.setFilter("branch = :branch", false, false);
        ISQLDataSet.PredicateItem item = ds.predicates().get(id);
        assertNotNull(item);
        assertNull(item.getExpression());
        assertEquals("branch = :branch", item.getFilterStr());
    }

    @Test
    public void stringFilterExtractsTopLevelOrderByWithoutReformattingPredicate() {
        SQLDataSet<Object[]> ds = new SQLDataSet<>(null, Object[].class);
        String predicate = "note = 'order by hidden' and id in "
                + "(select id from child order by created_at)";

        int id = ds.setFilter(predicate + " ORDER BY coalesce(name, 'a,b') DESC", false, false);

        assertEquals(predicate, ds.predicates().get(id).getFilterStr());
        assertEquals("~coalesce(name, 'a,b') DESC~", ds.getOrderBy());
    }

    @Test
    public void stringFilterWithOnlyOrderBySetsSortWithoutPredicate() {
        SQLDataSet<Object[]> ds = new SQLDataSet<>(null, Object[].class);

        assertEquals(-1, ds.setFilter("order by name", false, false));
        assertTrue(ds.predicates().isEmpty());
        assertEquals("~name~", ds.getOrderBy());
    }

    @Test
    public void trailingLineCommentDoesNotSwallowPredicateBoundary() {
        SQLDataSet<Object[]> ds = new SQLDataSet<>(null, Object[].class);

        int id = ds.setFilter("id = :id -- comment\nORDER BY name", false, false);

        assertEquals("id = :id", ds.predicates().get(id).getFilterStr());
        assertEquals("~name~", ds.getOrderBy());
    }

    @Test
    public void expressionWithOrderByOnlyInLiteralIsAccepted() throws Exception {
        SQLDataSet<Object[]> ds = new SQLDataSet<>(null, Object[].class);
        SQLExpression expression = new SQLExpression("note = 'order by hidden' and id = :id");

        int id = ds.setFilter(expression, false, false);

        assertSame(expression, ds.predicates().get(id).getExpression());
        assertNull(ds.getOrderBy());
    }

    @Test
    public void replacementAndPreFinalBehaviorRemainInDataSet() throws Exception {
        SQLDataSet<Object[]> ds = new SQLDataSet<>(null, Object[].class);
        int oldId = ds.setFilter(new SQLExpression("a = :a"), false, false);
        int newId = ds.setFilter(new SQLExpression("b = :b"), false, false);
        assertFalse(ds.predicates().containsKey(oldId));
        ISQLDataSet.PredicateItem current = ds.predicates().get(newId);
        assertTrue(current.isPre());
        current.makeFinal();
        assertTrue(current.isFinal());
    }

    @Test
    public void rejectedEmbeddedOrderByDoesNotMutatePredicates() throws Exception {
        SQLDataSet<Object[]> ds = new SQLDataSet<>(null, Object[].class);
        int id = ds.setFilter("active = 1", false, false);
        try {
            ds.setFilter(new SQLExpression("state = :state order by name"), false, false);
            fail("Expected explicit unsupported-case failure");
        } catch (DataSetException expected) {
            assertTrue(expected.getMessage().contains("ORDER BY"));
        }
        assertEquals(1, ds.predicates().size());
        assertTrue(ds.predicates().containsKey(id));
    }

    @Test
    public void datasourceOverridesExpressionIncludingNull() throws Exception {
        SQLDataSet<Object[]> ds = new SQLDataSet<>(null, Object[].class);
        SQLExpression expr = new SQLExpression("branch = :branch");
        expr.setParameter("branch", 9);
        int id = ds.setFilter(expr, false, false);

        DataSetSelectBuilder builder = new DataSetSelectBuilder("select id from client", SqlDialectFactory.POSTGRES);
        builder.addPredicate(ds.predicates().get(id).getExpression());
        PreparedSql prepared = builder.prepare();
        assertTrue(prepared.getSql().contains("branch = ?"));
        assertEquals(
                Collections.<Object>singletonList(9),
                builder.values(ds.mainSqlParameters)
        );

        ds.setParameter("branch", null);
        assertEquals(
                Collections.<Object>singletonList(null),
                builder.values(ds.mainSqlParameters)
        );
    }

    @Test
    public void datasetValuesOverrideCallbackIncludingExplicitNull() {
        SQLDataSet<Object[]> ds = new SQLDataSet<>(null, Object[].class);
        AtomicInteger callbackChecks = new AtomicInteger();
        ParametersValues callback = new ParametersValues() {
            @Override public boolean hasParameter(String name) {
                callbackChecks.incrementAndGet();
                return super.hasParameter(name);
            }

            @Override public boolean hasParameter(int index) {
                callbackChecks.incrementAndGet();
                return super.hasParameter(index);
            }
        };
        callback.setParameter("branch", null);
        callback.setParameter(0, 23);
        ds.setCallbackParameters(callback);

        DataSetSelectBuilder builder = new DataSetSelectBuilder(
                "select id from client where branch = :branch and region = ?", SqlDialectFactory.POSTGRES);
        assertEquals(Arrays.asList(null, 23), builder.values(ds.mainSqlParameters));

        callbackChecks.set(0);
        ds.setParameter("branch", 12);
        ds.setParameter(0, null);
        assertEquals(Arrays.asList(12, null), builder.values(ds.mainSqlParameters));
        assertEquals(0, callbackChecks.get());

        ds.setParameter("branch", IParameters.NO_PARAMETER_VALUE);
        ds.setParameter(0, IParameters.NO_PARAMETER_VALUE);
        assertEquals(Arrays.asList(null, 23), builder.values(ds.mainSqlParameters));
    }

    @Test
    public void completedWherePredicateUsesExpressionLocalValues() throws Exception {
        SQLDataSet<Object[]> ds = new SQLDataSet<>(null, Object[].class);

        SQLExpression expression =
                new SQLExpression("branch = :branch and state = 'A-1'");

        expression.setParameter("branch", 17);
        ds.setFilter(expression, false, false);

        String predicate =
                ds.renderCompletedWherePredicate(SqlDialectFactory.POSTGRES);

        assertTrue(predicate, predicate.contains("branch = 17"));
        assertTrue(predicate, predicate.contains("state =  'A-1' "));
        assertFalse(predicate, predicate.contains(":branch"));
    }

    @Test
    public void completedWherePredicateUsesJdbcRuntimeConversion() throws Exception {
        SQLDataSet<Object[]> ds = new SQLDataSet<>(null, Object[].class);

        SQLExpression expression =
                new SQLExpression(
                        "empty is not distinct from :empty "
                        + "and chr = :chr"
                );

        expression.setParameter("empty", "");
        expression.setParameter("chr", Character.valueOf('X'));
        ds.setFilter(expression, false, false);

        String predicate =
                ds.renderCompletedWherePredicate(SqlDialectFactory.POSTGRES);

        assertTrue(predicate, predicate.contains(
                "empty is not distinct from  NULL "
        ));
        assertTrue(predicate, predicate.contains(
                "chr =  'X' "
        ));
    }

    @Test
    public void completedWherePredicateKeepsDatasetOverrideIncludingNull() throws Exception {
        SQLDataSet<Object[]> ds = new SQLDataSet<>(null, Object[].class);

        SQLExpression expression =
                new SQLExpression("branch is not distinct from :branch");

        expression.setParameter("branch", 17);
        ds.setFilter(expression, false, false);
        ds.setParameter("branch", null);

        String predicate =
                ds.renderCompletedWherePredicate(SqlDialectFactory.POSTGRES);

        assertTrue(predicate, predicate.contains(
                "branch is not distinct from  NULL "
        ));
    }

    @Test
    public void sameNamedParameterInTwoFiltersHasLocalValuesUnlessDatasetDefinesIt() throws Exception {
        SQLDataSet<Object[]> ds = new SQLDataSet<>(null, Object[].class);
        SQLExpression first = new SQLExpression("kind = :cat");
        SQLExpression second = new SQLExpression("group_id = :cat");
        first.setParameter("cat", 10);
        second.setParameter("cat", null);
        ds.setFilter(first, true, true);
        ds.setFilter(second, false, true);

        assertFalse(ds.hasParameter("cat"));
        assertTrue(second.hasParameter("cat"));
        String local = ds.renderCompletedWherePredicate(SqlDialectFactory.POSTGRES);
        assertTrue(local, local.contains("kind = 10"));
        assertTrue(local, local.contains("group_id =  NULL "));

        ds.setParameter("cat", 30);
        String overridden = ds.renderCompletedWherePredicate(SqlDialectFactory.POSTGRES);
        assertTrue(overridden, overridden.contains("kind = 30"));
        assertTrue(overridden, overridden.contains("group_id = 30"));

        ds.setParameter("cat", null);
        assertTrue(ds.hasParameter("cat"));
        String explicitNull = ds.renderCompletedWherePredicate(SqlDialectFactory.POSTGRES);
        assertTrue(explicitNull, explicitNull.contains("kind =  NULL "));
        assertTrue(explicitNull, explicitNull.contains("group_id =  NULL "));
    }

    @Test
    public void completedSqlRenderingUsesPreparedParameterPlan() {
        PreparedSql prepared = new PreparedSql(
                "select '?' as literal_text, ? from client where owner_id = ? and amount = ?",
                Arrays.asList(
                        Parameter.literal("ACTIVE"),
                        Parameter.named("owner"),
                        Parameter.literal(new BigInteger("10"))
                )
        );

        IParameters parameters = new ParametersByName() {
            @Override public Object getParameter(String name) {
                return "owner".equals(name) ? 17 : null;
            }
            @Override public boolean hasParameter(String name) {
                return "owner".equals(name);
            }
        };

        String sql = SQLDataSet.renderPreparedSql(
                prepared,
                parameters,
                new PostgresSqlScanner()
        );

        assertEquals(
                "select '?' as literal_text,  'ACTIVE'  from client where owner_id = 17 and amount = 10",
                sql
        );
    }

    @Test
    public void completedSqlRenderingUsesJdbcRuntimeConversion() {
        PreparedSql prepared = new PreparedSql(
                "select ? empty_value, ? char_value",
                Arrays.asList(
                        Parameter.named("empty"),
                        Parameter.named("character")
                )
        );

        IParameters parameters = new ParametersByName() {
            @Override public Object getParameter(String name) {
                if( "empty".equals(name) ) return "";
                if( "character".equals(name) ) return Character.valueOf('X');
                return null;
            }
            @Override public boolean hasParameter(String name) {
                return "empty".equals(name)
                        || "character".equals(name);
            }
        };

        assertEquals(
                "select  NULL  empty_value,  'X'  char_value",
                SQLDataSet.renderPreparedSql(
                        prepared,
                        parameters,
                        new PostgresSqlScanner()
                )
        );
    }

    @Test
    public void explainRenderingReplacesOnlyJdbcPlaceholders() {
        PreparedSql prepared = new PreparedSql(
                "select '?' as literal_text, ? from client where owner_id = ?",
                Arrays.asList(
                        Parameter.literal("ACTIVE"),
                        Parameter.named("owner")
                )
        );

        String sql = XXIDataSet.renderExplainPlanSql(
                prepared,
                new OracleSqlScanner()
        );

        assertEquals(
                "select '?' as literal_text, :p1  from client where owner_id = :p2 ",
                sql
        );
    }

}
