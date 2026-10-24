package ru.inversion.utils.sparser.select;

import org.junit.Test;
import ru.inversion.utils.sparser.jdbc.Parameter;
import ru.inversion.utils.sparser.jdbc.PreparedSql;
import ru.inversion.utils.sparser.lexer.OracleSqlScanner;
import ru.inversion.utils.sparser.lexer.PostgresSqlScanner;
import ru.inversion.utils.sparser.lexer.SqlScanner;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class SqlSelectPrepareTest {
    private static SqlSelect pg(String sql) { return new SqlSelect(sql, new PostgresSqlScanner()); }
    private static SqlSelect ora(String sql) { return new SqlSelect(sql, new OracleSqlScanner()); }

    private static void assertParameterKinds(PreparedSql sql, Parameter.Kind... expected) {
        assertEquals("parameters size", expected.length, sql.getParameters().size());
        for (int i = 0; i < expected.length; i++) {
            assertEquals("Parameter at JDBC " + (i+1), expected[i], sql.getParameters().get(i).getKind());
        }
    }

    @Test public void mixedParameterSourcesAndRepeatedNames() {
        PreparedSql p = pg("select * from t where a=? and b=:userId and c=$2 and d='OPEN' and e=:userId and f=15 and g=?").prepare();
        assertEquals("select * from t where a=? and b=? and c=? and d=? and e=? and f=? and g=?", p.getSql());
        assertParameterKinds(p, Parameter.Kind.POSITIONAL, Parameter.Kind.NAMED,
                Parameter.Kind.NUMBERED, Parameter.Kind.LITERAL, Parameter.Kind.NAMED,
                Parameter.Kind.LITERAL, Parameter.Kind.POSITIONAL);
        assertEquals("userId", p.getParameters().get(1).getName());
        assertEquals(2, p.getParameters().get(2).getNumber());
        assertEquals("OPEN", p.getParameters().get(3).getValue());
        assertEquals("userId", p.getParameters().get(4).getName());
        assertEquals(new BigInteger("15"), p.getParameters().get(5).getValue());
    }

    @Test public void predicatesAreBeforeSourceWhereAndAreInPhysicalBindingOrder() {
        SqlSelect select = pg("select id from t where (a=? or b=:b) and c=10");
        select.setPredicates(Arrays.asList("tenant=?", "flag='Y'"));
        PreparedSql p = select.prepare();
        assertParameterKinds(p, Parameter.Kind.POSITIONAL, Parameter.Kind.LITERAL,
                Parameter.Kind.POSITIONAL, Parameter.Kind.NAMED, Parameter.Kind.LITERAL);
        assertEquals("Y", p.getParameters().get(1).getValue());
        assertEquals("b", p.getParameters().get(3).getName());
        assertEquals(new BigInteger("10"), p.getParameters().get(4).getValue());
        assertTrue(p.getSql().contains("(a=? or b=?)"));
    }

    @Test public void replacesOrderAndRemovesOldOrderParameter() {
        SqlSelect select = pg("select id from t where id=:id ORDER BY case when id=:old then 0 else 1 end limit 30");
        select.setOrderBy("case when id=:new then 2 else 3 end");
        PreparedSql p = select.prepare();
        assertFalse(p.getSql(), p.getSql().contains(":old"));
        assertTrue(p.getSql(), p.getSql().contains("case when id=? then 2 else 3 end"));
        assertTrue(p.getSql(), p.getSql().contains("limit ?"));
        assertParameterKinds(p, Parameter.Kind.NAMED, Parameter.Kind.NAMED, Parameter.Kind.LITERAL);
        assertEquals("id", p.getParameters().get(0).getName());
        assertEquals("new", p.getParameters().get(1).getName());
        assertEquals(new BigInteger("30"), p.getParameters().get(2).getValue());
    }

    @Test public void castSuppressesLiteralsButNotExplicitParameters() {
        PreparedSql p = ora("select cast(:x as number(10,2)), cast(case when a=:a then 'YES' else 'NO' end as varchar2(100)) from t where id=9").prepare();
        assertTrue(p.getSql(), p.getSql().contains("number(10,2)"));
        assertTrue(p.getSql(), p.getSql().contains("'YES' else 'NO'"));
        assertTrue(p.getSql(), p.getSql().contains("varchar2(100)"));
        assertTrue(p.getSql(), p.getSql().contains("where id=?"));
        assertParameterKinds(p, Parameter.Kind.NAMED, Parameter.Kind.NAMED, Parameter.Kind.LITERAL);
        assertEquals(new BigInteger("9"), p.getParameters().get(2).getValue());
    }

    @Test public void nestedOrderScopeDoesNotSwallowOuterWhereAndLimit() {
        PreparedSql p = pg("select id from (select id from t order by 1 limit 5) x where id=10 order by 1 limit 20").prepare();
        assertTrue(p.getSql(), p.getSql().contains("order by 1 limit ?"));
        assertTrue(p.getSql(), p.getSql().contains("where id=? order by 1 limit ?"));
        assertParameterKinds(p, Parameter.Kind.LITERAL, Parameter.Kind.LITERAL, Parameter.Kind.LITERAL);
        assertEquals(new BigInteger("5"), p.getParameters().get(0).getValue());
        assertEquals(new BigInteger("10"), p.getParameters().get(1).getValue());
        assertEquals(new BigInteger("20"), p.getParameters().get(2).getValue());
    }

    @Test public void allFragmentsParticipateInSharedProtectionState() {
        SqlSelect select = pg("select id from t where /* @parameterize:off */ x = 10");
        select.setPredicates(Collections.singletonList("z=20"));
        select.setOrderBy("id + 30");
        PreparedSql p = select.prepare();
        // Runtime predicate is located BEFORE source WHERE (and therefore BEFORE its OFF).
        assertEquals(new BigInteger("20"), p.getParameters().get(0).getValue());
        assertTrue(p.getSql().contains("x = 10"));
        assertTrue(p.getSql().contains("ORDER BY id + 30"));
        assertEquals(1, p.getParameters().size());
    }

    @Test public void directiveOnInInsertedOrderRestoresLiteralParameterizationForTail() {
        SqlSelect select = pg("select id from t where /* @parameterize:off */ x=7 order by 1 limit 25");
        select.setOrderBy("1 /* @parameterize:on */");
        PreparedSql p = select.prepare();
        assertTrue(p.getSql().contains("x=7"));
        assertTrue(p.getSql().contains("ORDER BY 1"));
        assertTrue(p.getSql().contains("limit ?"));
        assertParameterKinds(p, Parameter.Kind.LITERAL);
        assertEquals(new BigInteger("25"), p.getParameters().get(0).getValue());
    }

    @Test public void hintsCommentsAndQuotedIdentifiersAreUntouched() {
        String sql = "select /*+ INDEX(t t_idx_10) */ \"col:1?\" from t -- ? 123\nwhere x='Y' /* ? :wrong 'BAD' */";
        PreparedSql p = ora(sql).prepare();
        assertTrue(p.getSql(), p.getSql().contains("/*+ INDEX(t t_idx_10) */"));
        assertTrue(p.getSql(), p.getSql().contains("\"col:1?\""));
        assertTrue(p.getSql(), p.getSql().contains("-- ? 123"));
        assertTrue(p.getSql(), p.getSql().contains("/* ? :wrong 'BAD' */"));
        assertParameterKinds(p, Parameter.Kind.LITERAL);
        assertEquals("Y", p.getParameters().get(0).getValue());
    }

    @Test public void typedLiteralsRemainAndOtherLiteralsAreParameterized() {
        PreparedSql p = pg("select id from t where dt >= date '2026-01-01' and tm > time '12:00' and ts > timestamp '2026-01-01 12:00:00' and a > interval '1' day and x=5").prepare();
        assertTrue(p.getSql(), p.getSql().contains("date '2026-01-01'"));
        assertTrue(p.getSql(), p.getSql().contains("time '12:00'"));
        assertTrue(p.getSql(), p.getSql().contains("timestamp '2026-01-01 12:00:00'"));
        assertTrue(p.getSql(), p.getSql().contains("interval '1' day"));
        assertParameterKinds(p, Parameter.Kind.LITERAL);
    }

    @Test public void qQuotedAndDollarStringsDecodeCorrectly() {
        PreparedSql oracle = ora("select q'[a'b]' from dual where x=n'abc''def'").prepare();
        assertParameterKinds(oracle, Parameter.Kind.LITERAL);
        assertEquals("a'b", oracle.getParameters().get(0).getValue());
        assertTrue(oracle.getSql().contains("x=n'abc''def'"));
        PreparedSql pg = pg("select $$hello$$, $tag$there ' hi$tag$ from t where id=1").prepare();
        assertParameterKinds(pg, Parameter.Kind.LITERAL, Parameter.Kind.LITERAL, Parameter.Kind.LITERAL);
        assertEquals("hello", pg.getParameters().get(0).getValue());
        assertEquals("there ' hi", pg.getParameters().get(1).getValue());
    }

    @Test public void postgresNonstandardStringsArePreservedWhenDecoderIsUnsafe() {
        PreparedSql p = pg("select E'a\\n', U&'d\\0061ta' from t where a='x\\y' and b=5").prepare();
        assertTrue(p.getSql(), p.getSql().contains("E'a\\n'"));
        assertTrue(p.getSql(), p.getSql().contains("U&'d\\0061ta'"));
        assertTrue(p.getSql(), p.getSql().contains("a='x\\y'"));
        assertParameterKinds(p, Parameter.Kind.LITERAL);
    }

    @Test public void postgresRegexOperatorIsNotLegacyMarker() {
        PreparedSql p = pg("select id from t where name ~ '^[a-z]+$' and code ~ 'A.*' and id = 12").prepare();
        assertTrue(p.getSql(), p.getSql().contains("name ~ ?"));
        assertTrue(p.getSql(), p.getSql().contains("code ~ ?"));
        assertParameterKinds(p, Parameter.Kind.LITERAL, Parameter.Kind.LITERAL, Parameter.Kind.LITERAL);
    }

    @Test public void legacyTildeProtectsExpressionAndIsRemoved() {
        SqlSelect select = ora("select id from t order by 1 limit 100");
        select.setOrderBy("~replace(code, 'a', 'b')~");
        PreparedSql p = select.prepare();
        assertTrue(p.getSql(), p.getSql().contains("ORDER BY replace(code, 'a', 'b')"));
        assertFalse(p.getSql(), p.getSql().contains("~"));
        assertParameterKinds(p, Parameter.Kind.LITERAL);
        assertEquals(new BigInteger("100"), p.getParameters().get(0).getValue());
    }

    @Test public void cacheIsReusedAndAllMutationsInvalidate() {
        SqlSelect select = pg("select id from t where x=:x and a=10");
        PreparedSql first = select.prepare();
        assertSame(first, select.prepare());
        assertEquals(first.getSql(), select.prepare().getSql());
        select.setPredicates(Collections.singletonList("z=20"));
        PreparedSql second = select.prepare();
        assertNotEquals(first.getSql(), second.getSql());
        assertSame(second, select.prepare());
        select.setMark("1", "marked");
        PreparedSql third = select.prepare();
        assertNotEquals(second.getSql(), third.getSql());
        select.setOrderBy("id DESC");
        assertNotEquals(third.getSql(), select.prepare().getSql());
    }

    @Test public void sourceIsNotModifiedAndBuildSqlIsNotAutoParameterized() {
        SqlSelect select = pg("select id from t where id=:id and x=1");
        assertEquals("select id from t where id=:id and x=1", select.buildSql());
        PreparedSql p = select.prepare();
        assertEquals("select id from t where id=? and x=?", p.getSql());
        assertEquals("select id from t where id=:id and x=1", select.buildSql());
    }

    @Test public void pgRadixAndDecimalBecomeTypedValues() {
        PreparedSql p = pg("select x from t where a=0xFF and b=1_000 and c=3.25e2").prepare();
        assertParameterKinds(p, Parameter.Kind.LITERAL, Parameter.Kind.LITERAL, Parameter.Kind.LITERAL);
        assertEquals(new BigInteger("255"), p.getParameters().get(0).getValue());
        assertEquals(new BigInteger("1000"), p.getParameters().get(1).getValue());
        assertEquals(new BigDecimal("3.25e2"), p.getParameters().get(2).getValue());
    }

    @Test public void orderByNumberedParameterSurvivesProtection() {
        PreparedSql p = pg("select id from t where a=$1 order by case when b=:b then 0 else 1 end, $2 limit 10").prepare();
        assertTrue(p.getSql().contains("order by case when b=? then 0 else 1 end, ? limit ?"));
        assertParameterKinds(p, Parameter.Kind.NUMBERED, Parameter.Kind.NAMED,
                Parameter.Kind.NUMBERED, Parameter.Kind.LITERAL);
    }

    @Test public void sqlWithNoStructuralModificationsCanContainUnion() {
        PreparedSql p = pg("select x from a where id=1 union all select x from b where id=2").prepare();
        assertEquals("select x from a where id=? union all select x from b where id=?", p.getSql());
        assertParameterKinds(p, Parameter.Kind.LITERAL, Parameter.Kind.LITERAL);
    }

    @Test public void markParametersAreBeforeOriginalFromWhereParameters() {
        SqlSelect select = pg("select a from t where a=? and b=:b");
        select.setMark("case when :markFlag = 1 then 'X' else 'Y' end", "marked");
        PreparedSql p = select.prepare();
        assertParameterKinds(p, Parameter.Kind.NAMED, Parameter.Kind.LITERAL, Parameter.Kind.LITERAL,
                Parameter.Kind.LITERAL, Parameter.Kind.POSITIONAL, Parameter.Kind.NAMED);
        assertEquals("markFlag", p.getParameters().get(0).getName());
        assertEquals("b", p.getParameters().get(5).getName());
    }

    @Test public void explicitParametersSurviveOffAndCastAndOrder() {
        PreparedSql p = pg("select cast(:x as numeric(10,2)) from t where /* @parameterize:off */ a=? and b=$3 order by case when x=:k then 1 else 0 end limit 5").prepare();
        assertParameterKinds(p, Parameter.Kind.NAMED, Parameter.Kind.POSITIONAL,
                Parameter.Kind.NUMBERED, Parameter.Kind.NAMED);
        assertTrue(p.getSql().contains("numeric(10,2)"));
        assertTrue(p.getSql().contains("then 1 else 0"));
        assertTrue(p.getSql().contains("limit 5"));
    }

    @Test public void oracleNationalStringsAreNotRewritten() {
        PreparedSql p = ora("select n'café', nq'[national]' from dual where id=42").prepare();
        assertTrue(p.getSql().contains("n'café'"));
        assertTrue(p.getSql().contains("nq'[national]'"));
        assertParameterKinds(p, Parameter.Kind.LITERAL);
        assertEquals(new BigInteger("42"), p.getParameters().get(0).getValue());
    }

    @Test public void pgJdbcOperatorEscapeIsPreserved() {
        PreparedSql p = pg("select data from t where data ?? 'key' and id = :id").prepare();
        assertTrue(p.getSql().contains("data ?? ?"));
        assertParameterKinds(p, Parameter.Kind.LITERAL, Parameter.Kind.NAMED);
    }

    @Test public void postgresCastTypeModifiersAreNeverParameterized() {
        PreparedSql p = pg("select x::numeric(10, 2), y::pg_catalog.numeric(12,5), :v::varchar(32) from t where id=7").prepare();
        assertTrue(p.getSql().contains("::numeric(10, 2)"));
        assertTrue(p.getSql().contains("::pg_catalog.numeric(12,5)"));
        assertTrue(p.getSql().contains("?::varchar(32)"));
        assertParameterKinds(p, Parameter.Kind.NAMED, Parameter.Kind.LITERAL);
        assertEquals(new BigInteger("7"), p.getParameters().get(1).getValue());
    }

    @Test public void prefixedAndPrecisionTypedLiteralsStayVerbatim() {
        PreparedSql p = pg("select n'hello', b'1010' from t where ts > timestamp(3) '2026-01-01 12:00:00' and x=9").prepare();
        assertTrue(p.getSql().contains("n'hello'"));
        assertTrue(p.getSql().contains("b'1010'"));
        assertTrue(p.getSql().contains("timestamp(3) '2026-01-01 12:00:00'"));
        assertParameterKinds(p, Parameter.Kind.LITERAL);
    }
}
