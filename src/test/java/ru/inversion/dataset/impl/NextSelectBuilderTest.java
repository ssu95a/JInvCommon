package ru.inversion.dataset.impl;

import org.junit.Test;
import ru.inversion.dataset.IParameters;
import ru.inversion.dataset.ISQLExpression;
import ru.inversion.db.dialect.SqlDialectFactory;
import ru.inversion.utils.sparser.jdbc.Parameter;
import ru.inversion.utils.sparser.jdbc.PreparedSql;

import java.util.*;

import static org.junit.Assert.*;

public class NextSelectBuilderTest {

    private static final class Params implements IParameters {
        private final Map<String, Object> names = new HashMap<>();
        private final Map<Integer, Object> indexes = new HashMap<>();
       public Params set(String name, Object value) {names.put(name, value); return this;}
       public Params set(int number, Object value) {indexes.put(number, value); return this;}
        @Override public Object getParameter(String name) {return names.get(name);}
        @Override public Object getParameter(int index) {return indexes.get(index);}
        @Override public boolean hasParameter(String name) {return names.containsKey(name);}
        @Override public boolean hasParameter(int index) {return indexes.containsKey(index);}
    }

    private static final class Expr implements ISQLExpression {
        private final String sql;
        final Params params = new Params();
        Expr(String sql) {this.sql=sql;}
       public Expr set(String name, Object value) {params.set(name,value);return this;}
       public Expr set(int index, Object value) {params.set(index,value);return this;}
        @Override public String getSQL(){return sql;}
        @Override public Object getParameter(String name){return params.getParameter(name);}
        @Override public Object getParameter(int index){return params.getParameter(index);}
        @Override public boolean hasParameter(String name){return params.hasParameter(name);}
        @Override public boolean hasParameter(int index){return params.hasParameter(index);}
    }

    @Test public void duplicatedNamedParameterWithoutDatasetThrows() {
        NextSelectBuilder b = new NextSelectBuilder("select id from t", SqlDialectFactory.POSTGRES);
        b.addPredicate(new Expr("a = :id").set("id",10));
        b.addPredicate(new Expr("b = :id").set("id",20));
        try {b.parameters(new Params()); fail("Missing conflict");}
        catch (IllegalStateException e) {assertTrue(e.getMessage().contains("Conflicting SQL parameter :id"));}
    }

    @Test public void explicitNullDatasetOverridesBothExpressions() {
        NextSelectBuilder b = new NextSelectBuilder("select id from t", SqlDialectFactory.POSTGRES);
        b.addPredicate(new Expr("a = :id").set("id", 10));
        b.addPredicate(new Expr("b = :id").set("id", 20));
        Params main = new Params().set("id", null);
        PreparedSql p = b.prepare();
        assertEquals(2, p.getParameters().size());
        assertEquals(Parameter.Kind.NAMED,p.getParameters().get(0).getKind());
        IParameters merged = b.parameters(main);
        assertTrue(merged.hasParameter("id"));
        assertEquals(null, merged.getParameter("id"));
    }

    @Test public void localQuestionMarksBecomeGlobalSequence() {
        NextSelectBuilder b = new NextSelectBuilder("select id from t", SqlDialectFactory.POSTGRES);
        Expr a = new Expr("a = ?").set(0, 10);
        Expr c = new Expr("b = ?").set(0, 20);
        b.addPredicate(a).addPredicate(c);
        PreparedSql p = b.prepare();
        assertEquals(2,p.getParameters().size());
        assertEquals(Parameter.Kind.POSITIONAL,p.getParameters().get(0).getKind());
        assertEquals(Parameter.Kind.POSITIONAL,p.getParameters().get(1).getKind());
        assertTrue(p.getSql().contains("a = ?"));
        assertTrue(p.getSql().contains("b = ?"));
        IParameters merged=b.parameters(new Params());
        assertEquals(10,merged.getParameter(0));
        assertEquals(20,merged.getParameter(1));
    }

    @Test public void mainQuestionMarkGetsFinalTextualPosition() {
        NextSelectBuilder b = new NextSelectBuilder("select id from t where root = ?", SqlDialectFactory.POSTGRES);
        b.addPredicate(new Expr("a = ?").set(0, 10));
        PreparedSql p = b.prepare();
        assertEquals(2, p.getParameters().size());
        IParameters merged = b.parameters(new Params().set(1, 55));
        assertEquals(10, merged.getParameter(0));
        assertEquals(55, merged.getParameter(1));
    }

    @Test public void datasetIndexedOverridesLocalExpression() {
        NextSelectBuilder b = new NextSelectBuilder("select id from t", SqlDialectFactory.POSTGRES);
        b.addPredicate(new Expr("a = ?").set(0, 10));
        IParameters merged = b.parameters(new Params().set(0,null));
        assertEquals(null, merged.getParameter(0));
    }

    @Test public void runtimeValuesAreReadAtExecutionWithoutRecompile() {
        NextSelectBuilder b = new NextSelectBuilder("select id from t", SqlDialectFactory.ORACLE);
        Expr x = new Expr("a = :name").set("name","x");
        b.addPredicate(x);
        PreparedSql first = b.prepare();
        IParameters merged = b.parameters(new Params());
        assertEquals("x", merged.getParameter("name"));
        x.set("name","y");
        assertTrue(first == b.prepare());
        assertEquals("y", merged.getParameter("name"));
    }

    @Test public void questionMarkInsideStringIsRejected() {
        NextSelectBuilder b = new NextSelectBuilder("select id from t", SqlDialectFactory.POSTGRES);
        b.addPredicate(new Expr("txt = '?' and x = ? /* ? */").set(0,77));
        try {
            b.prepare();
            fail("Expected unsupported string literal");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("offset"));
        }
    }
    @Test public void changeOfPredicateInvalidatesPlan() {
        NextSelectBuilder b = new NextSelectBuilder("select id from t", SqlDialectFactory.POSTGRES);
        PreparedSql before=b.prepare();
        b.addPredicate("a = :x");
        PreparedSql after=b.prepare();
        assertFalse(before==after);
        assertTrue(after.getSql().contains("a = ?"));
    }

    @Test public void numberedParameterCanUseExpressionFallback() {
        NextSelectBuilder b = new NextSelectBuilder("select id from t", SqlDialectFactory.POSTGRES);
        b.addPredicate(new Expr("a = $2").set(1,7));
        PreparedSql p=b.prepare();
        assertEquals(Parameter.Kind.NUMBERED,p.getParameters().get(0).getKind());
        assertEquals(7,b.parameters(new Params()).getParameter(1));
    }

    @Test public void reservedNamesInOriginalSqlAreRejected() {
        NextSelectBuilder b = new NextSelectBuilder(
                "select id from t where x = :__sparser_local_pos_0_0", SqlDialectFactory.POSTGRES);
        b.addPredicate(new Expr("a = ?").set(0,11));
        try {b.prepare(); fail("Expected reserved-name error");}
        catch (IllegalArgumentException e) {assertTrue(e.getMessage().contains("Reserved"));}
    }

    @Test public void repeatSameExpressionDoesNotConflictWithItself() {
        NextSelectBuilder b = new NextSelectBuilder("select id from t", SqlDialectFactory.POSTGRES);
        Expr one = new Expr("a = :id").set("id", 99);
        b.addPredicate(one).addPredicate(one);
        assertEquals(99, b.parameters(new Params()).getParameter("id"));
    }

    @Test public void generatedLiteralsKeepTheirParameterType() {
        NextSelectBuilder b = new NextSelectBuilder("select id from t", SqlDialectFactory.POSTGRES);
        b.addPredicate(new Expr("a = ? and b = 42").set(0, 100));
        PreparedSql result = b.prepare();
        assertEquals(Parameter.Kind.POSITIONAL, result.getParameters().get(0).getKind());
        assertEquals(Parameter.Kind.LITERAL, result.getParameters().get(1).getKind());
        assertEquals(100, b.parameters(new Params()).getParameter(0));
    }

    @Test public void conflictOnNumberedParameterIsDetected() {
        NextSelectBuilder b = new NextSelectBuilder("select id from t", SqlDialectFactory.POSTGRES);
        b.addPredicate(new Expr("a = $1").set(0,11));
        b.addPredicate(new Expr("b = $1").set(0,12));
        try { b.parameters(new Params()); fail("Expected conflict"); }
        catch (IllegalStateException e) {assertTrue(e.getMessage().contains("Conflicting"));}
    }
    @Test public void numberedAndLocalPositionalCollisionThrows() {
        NextSelectBuilder b = new NextSelectBuilder("select id from t", SqlDialectFactory.POSTGRES);
        b.addPredicate(new Expr("a = ?").set(0, 11));
        b.addPredicate(new Expr("b = $1").set(0, 12));
        try { b.parameters(new Params()); fail("Expected mixed positional conflict"); }
        catch (IllegalStateException e) { assertTrue(e.getMessage().contains("Conflicting")); }
    }

    @Test public void datasetOverridesMixedPositionalConflictWithNull() {
        NextSelectBuilder b = new NextSelectBuilder("select id from t", SqlDialectFactory.POSTGRES);
        b.addPredicate(new Expr("a = ?").set(0, 11));
        b.addPredicate(new Expr("b = $1").set(0, 12));
        IParameters p = b.parameters(new Params().set(0, null));
        assertEquals(null, p.getParameter(0));
    }

    @Test public void orderByExpressionSupportsLocalQuestionMark() {
        NextSelectBuilder b = new NextSelectBuilder("select id from t", SqlDialectFactory.POSTGRES);
        b.orderBy(new Expr("case when id = ? then 0 else 1 end").set(0, 99));
        PreparedSql prepared = b.prepare();
        assertEquals(1, prepared.getParameters().size());
        assertEquals(Parameter.Kind.POSITIONAL, prepared.getParameters().get(0).getKind());
        assertEquals(99, b.parameters(new Params()).getParameter(0));
    }

    @Test public void sameExpressionInstanceCanSupplyNamedFromOrderByAndWhere() {
        NextSelectBuilder b = new NextSelectBuilder("select id from t", SqlDialectFactory.POSTGRES);
        Expr e = new Expr("x = :id").set("id", 17);
        b.addPredicate(e);
        b.orderBy(new Expr("case when id = :id then 0 else 1 end"));
        // Второе expression не определяет параметр, первое обеспечивает fallback.
        assertEquals(17, b.parameters(new Params()).getParameter("id"));
    }

    @Test public void malformedPredicateDoesNotCorruptCachedPreparedSql() {
        NextSelectBuilder b = new NextSelectBuilder("select id from t", SqlDialectFactory.ORACLE);
        PreparedSql initial = b.prepare();
        b.addPredicate("x = 1; delete from t");
        try { b.prepare(); fail("Expected invalid SQL fragment"); }
        catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("semicolon")); }
        // Исправленный новый builder создаётся из актуального SQLDataSet snapshot.
        assertEquals("select id from t", initial.getSql());
    }

    @Test public void mixedSourcesFollowFinalSqlTextOrder() {
        NextSelectBuilder b = new NextSelectBuilder(
                "select id from t where root = ? and code = :code", SqlDialectFactory.POSTGRES);
        b.addPredicate(new Expr("a = ? and state = 'A'").set(0, 10));
        b.addPredicate(new Expr("b = ?").set(0, 20));
        b.orderBy(new Expr("id + ?").set(0, 30));
        PreparedSql plan = b.prepare();
        IParameters values = b.parameters(new Params().set(2, 50).set("code", "X"));

        List<Object> resolved = new ArrayList<>();
        int positional = 0;
        for (Parameter parameter : plan.getParameters()) {
            switch (parameter.getKind()) {
                case POSITIONAL: resolved.add(values.getParameter(positional++)); break;
                case NAMED: resolved.add(values.getParameter(parameter.getName())); break;
                case NUMBERED: resolved.add(values.getParameter(parameter.getNumber() - 1)); break;
                case LITERAL: resolved.add(parameter.getValue()); break;
            }
        }
        assertEquals(Arrays.<Object>asList(10, "A", 20, 50, "X", 30), resolved);
        assertEquals(4, positional);
    }

    @Test public void changingSortingDropsOldBindings() {
        NextSelectBuilder b = new NextSelectBuilder("select id from t order by :old", SqlDialectFactory.POSTGRES);
        b.orderBy("id DESC");
        PreparedSql p = b.prepare();
        assertEquals(0, p.getParameters().size());
        assertTrue(p.getSql().contains("ORDER BY id DESC"));
        b.orderBy((String) null);
        assertEquals(Parameter.Kind.NAMED, b.prepare().getParameters().get(0).getKind());
    }

    @Test public void markComesAfterOriginalSelectItems() {
        NextSelectBuilder b = new NextSelectBuilder("select x, y from t order by 1", SqlDialectFactory.ORACLE);
        b.mark("0");
        String sql = b.buildSql().toLowerCase(Locale.ROOT);
        assertTrue(sql.indexOf("x, y") < sql.indexOf("as mark"));
        assertTrue(sql.indexOf("as mark") < sql.indexOf("from t"));
        assertTrue(sql.contains("order by 1"));
    }

}
