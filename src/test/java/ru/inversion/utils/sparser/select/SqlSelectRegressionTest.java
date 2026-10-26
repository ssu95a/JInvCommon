package ru.inversion.utils.sparser.select;

import org.junit.Test;
import ru.inversion.utils.sparser.jdbc.Parameter;
import ru.inversion.utils.sparser.jdbc.PreparedSql;
import ru.inversion.utils.sparser.lexer.OracleSqlScanner;
import ru.inversion.utils.sparser.lexer.PostgresSqlScanner;

import java.math.BigInteger;
import java.util.Arrays;

import static org.junit.Assert.*;

/** Targeted regressions for v0.2 code-review findings. */
public class SqlSelectRegressionTest {

    private static SqlSelect ora(String sql) { return new SqlSelect(sql, new OracleSqlScanner()); }
    private static SqlSelect pg(String sql) { return new SqlSelect(sql, new PostgresSqlScanner()); }

    @Test public void intervalStringsWithPunctuationAreRejected() {
        try { ora("select interval '2-3' year(2) to month from dual"); fail("Expected rejection"); }
        catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("offset")); }
    }
    @Test public void dateTimePrecisionFunctionsKeepNumbers() {
        PreparedSql p = pg("select current_timestamp(3), current_time(2), "
                + "localtime(5), localtimestamp(6) from t where id=9").prepare();
        assertTrue(p.getSql().contains("current_timestamp(3)"));
        assertTrue(p.getSql().contains("current_time(2)"));
        assertTrue(p.getSql().contains("localtime(5)"));
        assertTrue(p.getSql().contains("localtimestamp(6)"));
        assertEquals(1, p.getParameters().size());
    }

    @Test public void timeZoneStringsWithPunctuationAreRejected() {
        try { pg("select timestamp(3) with time zone '2026-10-23 22:30:00+03' from t"); fail("Expected rejection"); }
        catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("offset")); }
    }
    @Test public void intervalPrecisionStringWithDigitIsRejected() {
        try { pg("select interval(4) '2 day' from t"); fail("Expected rejection"); }
        catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("offset")); }
    }
    @Test public void legacyMarkersInSelectListBeforeAliasAndFrom() {
        PreparedSql p = ora("select ~replace(name, 'X', 'Y')~ as label, ~coalesce(s, 'N')~ from t where id=:id").prepare();
        assertTrue(p.getSql().contains("select replace(name, 'X', 'Y') as label, coalesce(s, 'N') from"));
        assertFalse(p.getSql().contains("~"));
        assertEquals(1, p.getParameters().size());
        assertEquals(Parameter.Kind.NAMED, p.getParameters().get(0).getKind());
    }

    @Test public void legacyMarkersBeforeDescAndNulls() {
        PreparedSql p = pg("select id from t order by ~coalesce(name, 'X')~ desc nulls last limit 25").prepare();
        assertTrue(p.getSql().contains("order by coalesce(name, 'X') desc nulls last limit ?"));
        assertEquals(1, p.getParameters().size());
        assertEquals(new BigInteger("25"), p.getParameters().get(0).getValue());
    }

    @Test public void postgresRegexOperatorRemainsOperator() {
        PreparedSql p = pg("select name from t where name ~ :pattern and x !~ :other and code=7").prepare();
        assertTrue(p.getSql().contains("name ~ ?"));
        assertTrue(p.getSql().contains("x !~ ?"));
        assertEquals(3, p.getParameters().size());
    }
    @Test public void explicitParametersSurviveProtectedLegacyExpression() {
        PreparedSql p = ora("select ~case when :p=10 then 'Y' else 'N' end~ AS flag from dual").prepare();
        assertTrue(p.getSql().contains("case when ?=10 then 'Y' else 'N' end AS flag"));
        assertEquals(1, p.getParameters().size());
        assertEquals("p", p.getParameters().get(0).getName());
    }

    @Test public void oracleLegacyTildeAfterAndBeforeLiteralsInUnion() {
        SqlSelect s = ora("SELECT ID, CWHERENAME, CUSER from FRM_FILTER "
                + "where cAutoSetup = 10 ~|| 'prefix' || user || 'suffix' ||~ 20 "
                + "union SELECT F.ID, F.CWHERENAME, F.cUser FROM FRM_FILTER F "
                + "WHERE F.cFormName = :form");
        PreparedSql p = s.prepare();
        assertFalse(p.getSql(), p.getSql().contains("~"));
        assertTrue(p.getSql(), p.getSql().contains(
                "cAutoSetup = ? || 'prefix' || user || 'suffix' || ?"));
        assertEquals(3, p.getParameters().size());
        assertEquals(new BigInteger("10"), p.getParameters().get(0).getValue());
        assertEquals(new BigInteger("20"), p.getParameters().get(1).getValue());
        assertEquals("form", p.getParameters().get(2).getName());
    }

    @Test public void oracleLegacyTildeAroundExplicitJdbcParameters() {
        PreparedSql p = ora("select * from t where x = ? ~|| 'text' ||~ ? ").prepare();
        assertFalse(p.getSql().contains("~"));
        assertTrue(p.getSql(), p.getSql().contains("x = ? || 'text' || ?"));
        assertEquals(2, p.getParameters().size());
        assertEquals(Parameter.Kind.POSITIONAL, p.getParameters().get(0).getKind());
        assertEquals(Parameter.Kind.POSITIONAL, p.getParameters().get(1).getKind());
    }

    @Test public void postgresLegacyTildeConcatInReportedUnion() {
        String sql = "SELECT ID, CWHERENAME, CUSER from FRM_FILTER "
                + "where cAutoSetup = ? ~|| ? || user || ? ||~ ? \n"
                + "union SELECT F.ID, F.CWHERENAME, F.cUser FROM FRM_FILTER F "
                + "WHERE F.cFormName = ? AND (F.cBlockName IS NULL OR F.cBlockName = ?) "
                + "AND F.ID IN (SELECT G.IDFilter FROM FRM_FILTER_ODB_GROUP G, "
                + "ODB_GRP_MEMBER M WHERE G.IDGroup = M.Grp_ID "
                + "AND M.iUsrID = to_number(SYS_CONTEXT(:namespace, :contextKey)))";
        PreparedSql p = pg(sql).prepare();
        assertTrue(p.getSql(), p.getSql().contains(
                "cAutoSetup = ? || ? || user || ? || ?"));
        assertFalse(p.getSql(), p.getSql().contains("~||"));
        assertFalse(p.getSql(), p.getSql().contains("||~"));
        // 6 source JDBC parameters + 2 named SYS_CONTEXT parameters.
        assertEquals(8, p.getParameters().size());
        for (int i = 0; i < 6; i++) {
            assertEquals(Parameter.Kind.POSITIONAL, p.getParameters().get(i).getKind());
        }
        assertEquals(Parameter.Kind.NAMED, p.getParameters().get(6).getKind());
        assertEquals(Parameter.Kind.NAMED, p.getParameters().get(7).getKind());
    }

    @Test public void postgresLegacyTildeConcatProtectsLiterals() {
        PreparedSql p = pg("select id from t where auto = 7 ~|| 'A' || user || 'B' ||~ 8").prepare();
        assertTrue(p.getSql(), p.getSql().contains(
                "auto = ? || 'A' || user || 'B' || ?"));
        assertEquals(2, p.getParameters().size());
        assertEquals(new BigInteger("7"), p.getParameters().get(0).getValue());
        assertEquals(new BigInteger("8"), p.getParameters().get(1).getValue());
    }

    @Test public void postgresUnpairedCustomConcatTildeOperatorsArePreserved() {
        PreparedSql left = pg("select a ~|| b from t").prepare();
        assertTrue(left.getSql().contains("a ~|| b"));
        PreparedSql right = pg("select a ||~ b from t").prepare();
        assertTrue(right.getSql().contains("a ||~ b"));
    }

    @Test public void postgresRegexOperatorsAreNotLegacyConcatMarkers() {
        PreparedSql p = pg("select name from t where name ~ :a and name !~ :b and id=2").prepare();
        assertTrue(p.getSql(), p.getSql().contains("name ~ ?"));
        assertTrue(p.getSql(), p.getSql().contains("name !~ ?"));
        assertEquals(3, p.getParameters().size());
    }
    @Test public void unmatchedOracleTildeFailsBeforeJdbc() {
        try {
            ora("select * from t where x = 1 ~|| 'text'").prepare();
            fail("Unpaired Oracle legacy marker must be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Unpaired Oracle legacy"));
        }
    }

    @Test public void distinctMarkRejectedWithoutMutatingSqlOrCachedPreparedSql() {
        SqlSelect s = pg("select distinct name from t where id=7");
        PreparedSql previous = s.prepare();
        try { s.setMark("random()", "marked"); fail("Expected UnsupportedOperationException"); }
        catch (UnsupportedOperationException expected) { /* pass */ }
        assertSame(previous, s.prepare());
        assertEquals("select distinct name from t where id=7", s.buildSql());
    }

    @Test public void distinctInsideScalarSubqueryDoesNotBlockPlainSelectMark() {
        SqlSelect s = pg("select (select distinct x from t2) as x, id from t");
        s.setMark("1", "marked");
        assertTrue(s.buildSql().contains("AS marked"));
    }

    @Test public void groupedAndHavingMarkRejected() {
        SqlSelect grouped = ora("select code, count(*) from t group by code");
        try { grouped.setMark("random()", "mark"); fail("Expected UnsupportedOperationException"); }
        catch (UnsupportedOperationException expected) { /* pass */ }
        SqlSelect having = pg("select count(*) from t having count(*) > 1");
        try { having.setMark("1", "mark"); fail("Expected UnsupportedOperationException"); }
        catch (UnsupportedOperationException expected) { /* pass */ }
    }

    @Test public void aggregateFunctionNameDoesNotTriggerSemanticGuessing() {
        SqlSelect select = pg("select count(*) from t");
        select.setMark("id", "mark");
        assertTrue(select.buildSql().contains("count(*), (id"));
        assertTrue(select.buildSql().contains("AS mark"));
    }

    @Test public void legacyMarkerScanDoesNotRepeatedlySearchSuffix() {
        // Many unmatched unary PG regex/bitwise tildes: old nested scan was O(N^2).
        StringBuilder sql = new StringBuilder("select x from t where ");
        for (int i = 0; i < 8000; i++) {
            if (i != 0) sql.append(" AND ");
            sql.append("~flag");
        }
        long start = System.nanoTime();
        PreparedSql p = pg(sql.toString()).prepare();
        long elapsed = (System.nanoTime() - start) / 1_000_000L;
        assertEquals(0, p.getParameters().size());
        assertTrue("Too slow for a linear token pass: " + elapsed + "ms", elapsed < 4000L);
    }

    @Test public void allEditsStillPreserveJdbcParameterOrderAndCache() {
        SqlSelect s = pg("select a from t where b=:b order by x desc limit 15");
        s.setPredicates(Arrays.asList("x=?", "n=2"));
        s.setOrderBy("case when :sort=1 then a else b end");
        PreparedSql p = s.prepare();
        assertSame(p, s.prepare());
        assertEquals(5, p.getParameters().size());
        assertEquals(Parameter.Kind.POSITIONAL, p.getParameters().get(0).getKind());
        assertEquals(Parameter.Kind.LITERAL, p.getParameters().get(1).getKind());
        assertEquals("b", p.getParameters().get(2).getName());
        assertEquals("sort", p.getParameters().get(3).getName());
    }
}
