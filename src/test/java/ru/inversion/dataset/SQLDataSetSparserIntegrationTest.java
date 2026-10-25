package ru.inversion.dataset;

import org.junit.Test;
import ru.inversion.dataset.impl.NextSelectBuilder;
import ru.inversion.db.dialect.SqlDialectFactory;
import ru.inversion.utils.sparser.jdbc.PreparedSql;

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

        NextSelectBuilder builder = new NextSelectBuilder("select id from client", SqlDialectFactory.POSTGRES);
        builder.addPredicate(ds.predicates().get(id).getExpression());
        PreparedSql prepared = builder.prepare();
        assertTrue(prepared.getSql().contains("branch = ?"));
        assertEquals(9, builder.parameters(values(ds)).getParameter("branch"));

        ds.setParameter("branch", null);
        assertTrue(builder.parameters(values(ds)).hasParameter("branch"));
        assertNull(builder.parameters(values(ds)).getParameter("branch"));
    }

    @Test
    public void callbackNullOverridesDatasetAndExpression() throws Exception {
        SQLDataSet<Object[]> ds = new SQLDataSet<>(null, Object[].class);
        ds.setParameter("branch", 12);
        SQLExpression expr = new SQLExpression("branch = :branch");
        expr.setParameter("branch", 17);
        int id = ds.setFilter(expr, true, false);
        ds.setCallbackParameters(new ParametersByName() {
            @Override public Object getParameter(String name) { return null; }
            @Override public boolean hasParameter(String name) { return "branch".equals(name); }
        });

        NextSelectBuilder builder = new NextSelectBuilder("select id from client", SqlDialectFactory.POSTGRES);
        builder.addPredicate(ds.predicates().get(id).getExpression());
        assertNull(builder.parameters(values(ds)).getParameter("branch"));
    }

    private static IParameters values(SQLDataSet<?> ds) {
        return new IParameters() {
            @Override public Object getParameter(String name) { return ds.getParameterValue(name); }
            @Override public Object getParameter(int index) { return ds.getParameterValue(index); }
            @Override public boolean hasParameter(String name) {
                IParameters callback = ds.getCallbackParameters();
                return (callback != null && callback.hasParameter(name)) || ds.hasParameter(name);
            }
            @Override public boolean hasParameter(int index) {
                IParameters callback = ds.getCallbackParameters();
                return (callback != null && callback.hasParameter(index)) || ds.hasParameter(index);
            }
        };
    }
}
