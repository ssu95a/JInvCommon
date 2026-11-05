package ru.inversion.dataset.impl;

import org.junit.Test;
import ru.inversion.dataset.IParameters;
import ru.inversion.dataset.ISQLExpression;
import ru.inversion.db.dialect.SqlDialectFactory;
import ru.inversion.utils.sparser.jdbc.Parameter;
import ru.inversion.utils.sparser.jdbc.PreparedSql;

import java.util.*;

import static org.junit.Assert.*;

public class DataSetSelectBuilderTest {

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
        DataSetSelectBuilder b = new DataSetSelectBuilder("select id from t", SqlDialectFactory.POSTGRES);
        b.addPredicate(new Expr("a = :id").set("id",10));
        b.addPredicate(new Expr("b = :id").set("id",20));
        try {b.values(new Params()); fail("Missing conflict");}
        catch (IllegalStateException e) {assertTrue(e.getMessage().contains("Conflicting SQL parameter :id"));}
    }

    @Test public void explicitNullDatasetOverridesBothExpressions() {
        DataSetSelectBuilder b = new DataSetSelectBuilder("select id from t", SqlDialectFactory.POSTGRES);
        b.addPredicate(new Expr("a = :id").set("id", 10));
        b.addPredicate(new Expr("b = :id").set("id", 20));
        Params main = new Params().set("id", null);
        PreparedSql p = b.prepare();
        assertEquals(2, p.getParameters().size());
        assertEquals(Parameter.Kind.NAMED,p.getParameters().get(0).getKind());
        assertEquals(Arrays.<Object>asList(null, null), b.values(main));
    }

    @Test public void positionalParametersUseGlobalIndexes() {
        DataSetSelectBuilder b = new DataSetSelectBuilder(
                "select id from t",
                SqlDialectFactory.POSTGRES
        );

        b.addPredicate(new Expr("a = ?").set(0, 10));
        b.addPredicate(new Expr("b = ?").set(1, 20));

        assertEquals(
                Arrays.<Object>asList(10, 20),
                b.values(new Params())
        );
    }

    @Test public void duplicatedPositionalParameterWithoutDatasetThrows() {
        DataSetSelectBuilder b = new DataSetSelectBuilder(
                "select id from t",
                SqlDialectFactory.POSTGRES
        );

        b.addPredicate(new Expr("a = ?").set(0, 10));
        b.addPredicate(new Expr("b = ?").set(0, 20));

        try {
            b.values(new Params());
            fail("Missing positional conflict");
        }
        catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains(
                    "Conflicting SQL parameter index 0"));
        }
    }

    @Test public void mainQuestionMarkGetsFinalTextualPosition() {
        DataSetSelectBuilder b = new DataSetSelectBuilder("select id from t where root = ?", SqlDialectFactory.POSTGRES);
        b.addPredicate(new Expr("a = ?").set(0, 10));
        PreparedSql p = b.prepare();
        assertEquals(2, p.getParameters().size());
        assertEquals(
                Arrays.<Object>asList(10, 55),
                b.values(new Params().set(1, 55))
        );
    }

    @Test public void datasetIndexedOverridesPredicateFallback() {
        DataSetSelectBuilder b = new DataSetSelectBuilder("select id from t", SqlDialectFactory.POSTGRES);
        b.addPredicate(new Expr("a = ?").set(0, 10));
        assertEquals(
                Collections.<Object>singletonList(null),
                b.values(new Params().set(0, null))
        );
    }

    @Test public void runtimeValuesAreReadAtExecutionWithoutRecompile() {
        DataSetSelectBuilder b = new DataSetSelectBuilder("select id from t", SqlDialectFactory.ORACLE);
        Expr x = new Expr("a = :name").set("name","x");
        b.addPredicate(x);
        PreparedSql first = b.prepare();
        assertEquals("x", b.values(new Params()).get(0));
        x.set("name","y");
        assertTrue(first == b.prepare());
        assertEquals("y", b.values(new Params()).get(0));
    }

    @Test public void questionMarkInsideStringIsBoundWithoutStealingPositionalSlot() {
        DataSetSelectBuilder b = new DataSetSelectBuilder("select id from t", SqlDialectFactory.POSTGRES);
        b.addPredicate(new Expr("txt = '?' and x = ? /* ? */").set(0,77));

        PreparedSql p = b.prepare();

        assertTrue(p.getSql(), p.getSql().contains("txt = ? and x = ? /* ? */"));
        assertEquals(2, p.getParameters().size());
        assertEquals(Parameter.Kind.LITERAL, p.getParameters().get(0).getKind());
        assertEquals("?", p.getParameters().get(0).getValue());
        assertEquals(Parameter.Kind.POSITIONAL, p.getParameters().get(1).getKind());
        assertEquals(Arrays.<Object>asList("?", 77), b.values(new Params()));
    }
    @Test public void changeOfPredicateInvalidatesPlan() {
        DataSetSelectBuilder b = new DataSetSelectBuilder("select id from t", SqlDialectFactory.POSTGRES);
        PreparedSql before=b.prepare();
        b.addPredicate("a = :x");
        PreparedSql after=b.prepare();
        assertFalse(before==after);
        assertTrue(after.getSql().contains("a = ?"));
    }

    @Test public void numberedParameterCanUseExpressionFallback() {
        DataSetSelectBuilder b = new DataSetSelectBuilder("select id from t", SqlDialectFactory.POSTGRES);
        b.addPredicate(new Expr("a = $2").set(1,7));
        PreparedSql p=b.prepare();
        assertEquals(Parameter.Kind.NUMBERED,p.getParameters().get(0).getKind());
        assertEquals(Collections.<Object>singletonList(7), b.values(new Params()));
    }


    @Test public void missingIndexedParameterReportsGlobalIndex() {
        DataSetSelectBuilder b = new DataSetSelectBuilder(
                "select id from t where a = ?",
                SqlDialectFactory.POSTGRES
        );

        try {
            b.values(new Params());
            fail("Expected missing indexed parameter");
        }
        catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains(
                    "Missing SQL parameter index 0"));
        }
    }

    @Test public void missingNamedParameterReportsName() {
        DataSetSelectBuilder b = new DataSetSelectBuilder(
                "select id from t where a = :id",
                SqlDialectFactory.POSTGRES
        );

        try {
            b.values(new Params());
            fail("Expected missing named parameter");
        }
        catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains(
                    "Missing SQL parameter :id"));
        }
    }

    @Test public void formerInternalPrefixIsAnOrdinaryNamedParameter() {
        String name = "__sparser_local_pos_0_0";
        DataSetSelectBuilder b = new DataSetSelectBuilder(
                "select id from t where x = :" + name, SqlDialectFactory.POSTGRES);
        PreparedSql prepared = b.prepare();

        assertEquals(Parameter.Kind.NAMED, prepared.getParameters().get(0).getKind());
        assertEquals(Collections.<Object>singletonList(42), b.values(new Params().set(name, 42)));
    }

    @Test public void repeatSameExpressionDoesNotConflictWithItself() {
        DataSetSelectBuilder b = new DataSetSelectBuilder("select id from t", SqlDialectFactory.POSTGRES);
        Expr one = new Expr("a = :id").set("id", 99);
        b.addPredicate(one).addPredicate(one);
        assertEquals(Arrays.<Object>asList(99, 99), b.values(new Params()));
    }

    @Test public void generatedLiteralsKeepTheirParameterType() {
        DataSetSelectBuilder b = new DataSetSelectBuilder("select id from t", SqlDialectFactory.POSTGRES);
        b.addPredicate(new Expr("a = ? and b = 42").set(0, 100));
        PreparedSql result = b.prepare();
        assertEquals(Parameter.Kind.POSITIONAL, result.getParameters().get(0).getKind());
        assertEquals(Parameter.Kind.LITERAL, result.getParameters().get(1).getKind());
        assertEquals(Arrays.<Object>asList(100, new java.math.BigInteger("42")), b.values(new Params()));
    }

    @Test public void conflictOnNumberedParameterIsDetected() {
        DataSetSelectBuilder b = new DataSetSelectBuilder("select id from t", SqlDialectFactory.POSTGRES);
        b.addPredicate(new Expr("a = $1").set(0,11));
        b.addPredicate(new Expr("b = $1").set(0,12));
        try { b.values(new Params()); fail("Expected conflict"); }
        catch (IllegalStateException e) {assertTrue(e.getMessage().contains("Conflicting"));}
    }
    @Test public void numberedAndPositionalDuplicateThrows() {
        DataSetSelectBuilder b = new DataSetSelectBuilder("select id from t", SqlDialectFactory.POSTGRES);
        b.addPredicate(new Expr("a = ?").set(0, 11));
        b.addPredicate(new Expr("b = $1").set(0, 12));
        try { b.values(new Params()); fail("Expected mixed positional conflict"); }
        catch (IllegalStateException e) { assertTrue(e.getMessage().contains("Conflicting")); }
    }

    @Test public void datasetOverridesMixedPositionalConflictWithNull() {
        DataSetSelectBuilder b = new DataSetSelectBuilder("select id from t", SqlDialectFactory.POSTGRES);
        b.addPredicate(new Expr("a = ?").set(0, 11));
        b.addPredicate(new Expr("b = $1").set(0, 12));
        assertEquals(
                Arrays.<Object>asList(null, null),
                b.values(new Params().set(0, null))
        );
    }

    @Test public void orderByExpressionUsesGlobalPositionalIndex() {
        DataSetSelectBuilder b = new DataSetSelectBuilder("select id from t", SqlDialectFactory.POSTGRES);
        b.orderBy(new Expr("case when id = ? then 0 else 1 end").set(0, 99));
        PreparedSql prepared = b.prepare();
        assertEquals(1, prepared.getParameters().size());
        assertEquals(Parameter.Kind.POSITIONAL, prepared.getParameters().get(0).getKind());
        assertEquals(Collections.<Object>singletonList(99), b.values(new Params()));
    }

    @Test public void sameExpressionInstanceCanSupplyNamedFromOrderByAndWhere() {
        DataSetSelectBuilder b = new DataSetSelectBuilder("select id from t", SqlDialectFactory.POSTGRES);
        Expr e = new Expr("x = :id").set("id", 17);
        b.addPredicate(e);
        b.orderBy(new Expr("case when id = :id then 0 else 1 end"));
        // Второе expression не определяет параметр, первое обеспечивает fallback.
        assertEquals(Arrays.<Object>asList(17, 17), b.values(new Params()));
    }

    @Test public void malformedPredicateDoesNotCorruptCachedPreparedSql() {
        DataSetSelectBuilder b = new DataSetSelectBuilder("select id from t", SqlDialectFactory.ORACLE);
        PreparedSql initial = b.prepare();
        b.addPredicate("x = 1; delete from t");
        try { b.prepare(); fail("Expected invalid SQL fragment"); }
        catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("semicolon")); }
        // Исправленный новый builder создаётся из актуального SQLDataSet snapshot.
        assertEquals("select id from t", initial.getSql());
    }

    @Test public void mixedSourcesFollowFinalSqlTextOrder() {
        DataSetSelectBuilder b = new DataSetSelectBuilder(
                "select id from t where root = ? and code = :code", SqlDialectFactory.POSTGRES);
        b.addPredicate(new Expr("a = ? and state = 'A'").set(0, 10));
        b.addPredicate(new Expr("b = ?").set(1, 20));
        b.orderBy(new Expr("id + ?").set(3, 30));
        PreparedSql plan = b.prepare();
        List<Object> resolved =
                b.values(new Params().set(2, 50).set("code", "X"));

        assertEquals(6, plan.getParameters().size());
        assertEquals(
                Arrays.<Object>asList(10, "A", 20, 50, "X", 30),
                resolved
        );
    }

    @Test public void changingSortingDropsOldBindings() {
        DataSetSelectBuilder b = new DataSetSelectBuilder("select id from t order by :old", SqlDialectFactory.POSTGRES);
        b.orderBy("id DESC");
        PreparedSql p = b.prepare();
        assertEquals(0, p.getParameters().size());
        assertTrue(p.getSql().contains("ORDER BY id DESC"));
        b.orderBy((String) null);
        assertEquals(Parameter.Kind.NAMED, b.prepare().getParameters().get(0).getKind());
    }

    @Test public void markComesAfterOriginalSelectItems() {
        DataSetSelectBuilder b = new DataSetSelectBuilder("select x, y from t order by 1", SqlDialectFactory.ORACLE);
        b.mark("0");
        String sql = b.buildSql().toLowerCase(Locale.ROOT);
        assertTrue(sql.indexOf("x, y") < sql.indexOf("as mark"));
        assertTrue(sql.indexOf("as mark") < sql.indexOf("from t"));
        assertTrue(sql.contains("order by 1"));
    }

    @Test public void queryAliasWrapsSourceAndPredicatesUseOuterAlias() {
        DataSetSelectBuilder b =
                new DataSetSelectBuilder("select id, name from client", SqlDialectFactory.POSTGRES);
        b.queryAlias("q");
        b.addPredicate("q.id = :id");

        PreparedSql sql = b.prepare();

        assertTrue(sql.getSql().contains("SELECT q.* FROM ("));
        assertTrue(sql.getSql().contains(") q"));
        assertTrue(sql.getSql().contains("q.id = ?"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void queryAliasMustBeSingleIdentifier() {
        new DataSetSelectBuilder("select id from client", SqlDialectFactory.POSTGRES)
                .queryAlias("q where 1=1");
    }

    @Test public void optimizerHintIsInsertedAfterTopLevelSelect() {
        DataSetSelectBuilder b =
                new DataSetSelectBuilder("select id from client", SqlDialectFactory.ORACLE);
        b.optimizerHint("+ index(client idx_client)");

        String sql = b.buildSql();

        assertTrue(sql, sql.contains(
                "select /*+ index(client idx_client)*/  id from client"));
    }

    @Test public void optimizerHintTargetsOuterSelectWhenAliasWrapsSource() {
        DataSetSelectBuilder b =
                new DataSetSelectBuilder("select id from client", SqlDialectFactory.POSTGRES);
        b.queryAlias("q");
        b.optimizerHint("+ test_hint");

        String sql = b.buildSql();

        assertTrue(sql, sql.startsWith("SELECT /*+ test_hint*/  q.* FROM ("));
        assertTrue(sql, sql.contains("select id from client"));
    }

    @Test public void optimizerHintSkipsSelectInsideCte() {
        DataSetSelectBuilder b = new DataSetSelectBuilder(
                "with x as (select id from client) select id from x",
                SqlDialectFactory.POSTGRES);
        b.optimizerHint("+ outer_hint");

        String sql = b.buildSql();

        assertTrue(sql, sql.contains(
                "with x as (select id from client) select /*+ outer_hint*/  id from x"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void optimizerHintRejectsCommentTerminator() {
        new DataSetSelectBuilder("select id from client", SqlDialectFactory.POSTGRES)
                .optimizerHint("+ bad */ delete");
    }


    @Test public void rowLimitIsAppliedAndParameterized() {
        DataSetSelectBuilder b = new DataSetSelectBuilder(
                "select id from client order by id",
                SqlDialectFactory.POSTGRES);

        PreparedSql p = b.rowLimit(10).prepare();

        assertTrue(p.getSql(), p.getSql().contains("LIMIT ?"));
        assertEquals(1, p.getParameters().size());
        assertEquals(
                Parameter.Kind.LITERAL,
                p.getParameters().get(0).getKind()
        );
        assertEquals(
                new java.math.BigInteger("10"),
                p.getParameters().get(0).getValue()
        );
    }

    @Test(expected = IllegalArgumentException.class)
    public void rowLimitRejectsNegativeValue() {
        new DataSetSelectBuilder(
                "select id from client",
                SqlDialectFactory.POSTGRES
        ).rowLimit(-1);
    }

    @Test public void ordinaryStringLiteralWithPunctuationIsBound() {
        DataSetSelectBuilder b = new DataSetSelectBuilder(
                "select id from t where code = 'A-1/2'",
                SqlDialectFactory.POSTGRES);

        PreparedSql p = b.prepare();

        assertEquals("select id from t where code = ?", p.getSql());
        assertEquals(1, p.getParameters().size());
        assertEquals(Parameter.Kind.LITERAL, p.getParameters().get(0).getKind());
        assertEquals("A-1/2", p.getParameters().get(0).getValue());
    }

    @Test public void escapedQuoteInStringLiteralIsDecodedForBinding() {
        DataSetSelectBuilder b = new DataSetSelectBuilder(
                "select id from t where name = 'O''Brien'",
                SqlDialectFactory.ORACLE);

        PreparedSql p = b.prepare();

        assertEquals("select id from t where name = ?", p.getSql());
        assertEquals("O'Brien", p.getParameters().get(0).getValue());
    }

    @Test(expected = IllegalArgumentException.class)
    public void typedStringLiteralIsRejected() {
        new DataSetSelectBuilder(
                "select id from t where d = DATE '2026-10-26'",
                SqlDialectFactory.ORACLE)
                .prepare();
    }

}
