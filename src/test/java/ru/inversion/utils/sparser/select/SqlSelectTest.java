package ru.inversion.utils.sparser.select;

import ru.inversion.utils.sparser.lexer.*;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

public class SqlSelectTest {
    private static SqlSelect pg(String sql) { return new SqlSelect(sql, new PostgresSqlScanner()); }
    private static SqlSelect ora(String sql) { return new SqlSelect(sql, new OracleSqlScanner()); }

    @Test public void unchangedSqlIsPreservedByteForByte() {
        String sql = "/* c */ SeLeCt a /*note*/ FROM t --comment\nWHERE a = :id; --end";
        assertEquals(sql, pg(sql).buildSql());
    }

    @Test public void predicateWithExistingWhereKeepsOperatorPrecedence() {
        SqlSelect select = pg("select id from t where a = 1 OR b = 2 order by id");
        select.setPredicates(Arrays.asList("tenant = :tenant", "flag = 1"));
        String result = select.buildSql();
        assertTrue(result, result.contains("where"));
        assertTrue(result, result.contains("((tenant = :tenant\n) AND (flag = 1\n)) AND ("));
        assertTrue(result, result.contains("a = 1 OR b = 2)"));
        assertTrue(result, result.contains("order by id"));
    }

    @Test public void noWhereBeforeGroupByHavingOrderBy() {
        SqlSelect select = pg("SELECT x, count(*) FROM t GROUP BY x HAVING count(*) > 1 ORDER BY x");
        select.setPredicates(Collections.singletonList("x > :low"));
        String sql = select.buildSql();
        assertTrue(sql, sql.indexOf("WHERE") < sql.indexOf("GROUP BY"));
        assertTrue(sql, sql.indexOf("FROM t") < sql.indexOf("WHERE"));
    }

    @Test public void noWhereBeforeOrderAndFetch() {
        SqlSelect select = ora("SELECT a FROM t ORDER BY a FETCH FIRST 10 ROWS ONLY");
        select.setPredicates(Collections.singletonList("a > 3"));
        String sql = select.buildSql();
        assertTrue(sql, sql.indexOf("WHERE") > sql.indexOf("FROM"));
        assertTrue(sql, sql.indexOf("WHERE") < sql.indexOf("ORDER BY"));
        assertTrue(sql, sql.contains("FETCH FIRST 10 ROWS ONLY"));
    }

    @Test public void replacesOnlyOuterOrderByAndKeepsLimit() {
        SqlSelect select = pg("select (select x from b order by y) as v from a ORDER BY v DESC limit 20 offset 2");
        select.setOrderBy("v ASC, :sort");
        String sql = select.buildSql();
        assertTrue(sql, sql.contains("from b order by y"));
        assertFalse(sql, sql.contains("ORDER BY v DESC"));
        assertTrue(sql, sql.contains("ORDER BY v ASC, :sort"));
        assertTrue(sql, sql.contains("limit 20 offset 2"));
    }

    @Test public void addsOrderByBeforeLimit() {
        SqlSelect select = pg("select a from t limit 10 offset 4");
        select.setOrderBy("a DESC");
        String sql = select.buildSql();
        assertTrue(sql, sql.indexOf("ORDER BY") < sql.indexOf("limit 10"));
    }

    @Test public void insertsSortingBeforePostgresRowLock() {
        SqlSelect select = pg("select id from t for key share");
        select.setOrderBy("id desc");
        String sql = select.buildSql();
        assertTrue(sql, sql.indexOf("ORDER BY") < sql.indexOf("for key share"));
    }

    @Test public void keepsParameterNamesAndNumberedParametersUntouched() {
        SqlSelect select = pg("select id from t where x = :x and y = $2 order by 1");
        select.setPredicates(Collections.singletonList("z = ?"));
        String sql = select.buildSql();
        assertTrue(sql, sql.contains("z = ?"));
        assertTrue(sql, sql.contains(":x"));
        assertTrue(sql, sql.contains("$2"));
        assertTrue(sql, sql.contains("order by 1"));
    }

    @Test public void markIsLastBeforeFromAndDoesNotMoveOrderOrdinals() {
        SqlSelect select = ora("select a, b from t order by 1, 2");
        select.setMark("select 1 from dual", "MARKED");
        String sql = select.buildSql();
        assertTrue(sql, sql.contains("a, b, (select 1 from dual\n) AS MARKED"));
        assertTrue(sql, sql.contains("order by 1, 2"));
    }

    @Test public void allChangesAreAppliedTogetherAndCached() {
        SqlSelect select = pg("select id from t limit 5");
        select.setMark("2", "extra");
        select.setPredicates(Collections.singletonList("id > :id"));
        select.setOrderBy("id desc");
        String sql = select.buildSql();
        assertSame(sql, select.buildSql());
        assertTrue(sql, sql.indexOf("extra") < sql.indexOf("from t"));
        assertTrue(sql, sql.indexOf("WHERE") < sql.indexOf("ORDER BY"));
        assertTrue(sql, sql.indexOf("ORDER BY") < sql.indexOf("limit 5"));
    }

    @Test public void mutationInvalidatesCacheAndRemovalRestoresSource() {
        String original = "select a from t ORDER BY a";
        SqlSelect select = pg(original);
        select.setOrderBy("a desc");
        assertTrue(select.buildSql().contains("a desc"));
        select.setOrderBy(null);
        assertEquals(original, select.buildSql());
        select.setMark("1", "marked");
        assertNotEquals(original, select.buildSql());
        select.setMark(null, null);
        assertEquals(original, select.buildSql());
    }

    @Test public void handlesCteAndIgnoresNestedWhereOrder() {
        SqlSelect select = pg("WITH x AS (select id from t where flag=1 order by id) " +
                "select id from x order by id");
        select.setPredicates(Collections.singletonList("id > 10"));
        select.setOrderBy("id desc");
        String result = select.buildSql();
        assertTrue(result, result.contains("where flag=1 order by id"));
        assertTrue(result, result.contains("WHERE ((id > 10\n))"));
        assertTrue(result, result.contains("ORDER BY id desc"));
    }

    @Test public void leadingAndTrailingCommentsDoNotSwallowAppendedSql() {
        SqlSelect select = pg("select x from t -- tail");
        select.setPredicates(Collections.singletonList("x = 2"));
        select.setOrderBy("x");
        String sql = select.buildSql();
        assertTrue(sql, sql.indexOf("WHERE") < sql.indexOf("-- tail"));
        assertTrue(sql, sql.indexOf("ORDER BY") < sql.indexOf("-- tail"));
    }

    @Test public void embeddedCommentsInWhereArePreserved() {
        SqlSelect select = pg("SELECT a FROM t WHERE /* @parameterize:off */ x = 10 -- hi\n ORDER BY a");
        select.setPredicates(Collections.singletonList("a > 0"));
        String sql = select.buildSql();
        assertTrue(sql, sql.contains("/* @parameterize:off */"));
        assertTrue(sql, sql.contains("x = 10)  -- hi"));
    }

    @Test public void oracleConnectByHasCorrectWhereInsertion() {
        SqlSelect select = ora("select id from t START WITH id = 1 CONNECT BY PRIOR id = pid ORDER SIBLINGS BY id");
        select.setPredicates(Collections.singletonList("active = 1"));
        String sql = select.buildSql();
        assertTrue(sql, sql.indexOf("WHERE") < sql.indexOf("START WITH"));
        assertTrue(sql, sql.contains("ORDER SIBLINGS BY id"));
    }

    @Test public void queryWithSetOperationUnchangedButMutationsRejected() {
        SqlSelect select = pg("select id from a union all select id from b order by 1");
        assertEquals("select id from a union all select id from b order by 1", select.buildSql());
        assertUnsupported(() -> select.setOrderBy("id"));
        assertUnsupported(() -> select.setPredicates(Collections.singletonList("id > 1")));
        assertUnsupported(() -> select.setMark("1", "mark"));
    }

    @Test public void replacingOrderSiblingsIsRejected() {
        SqlSelect select = ora("select x from t order siblings by x");
        assertUnsupported(() -> select.setOrderBy("x desc"));
    }

    @Test public void maliciousOrMalformedFragmentsAreRejected() {
        SqlSelect select = pg("select id from t");
        assertInvalid(() -> select.setPredicates(Collections.singletonList("id = 2; delete from t")));
        assertInvalid(() -> select.setPredicates(Collections.singletonList("(id = 2")));
        assertInvalid(() -> select.setMark("x", "x y"));
    }

    @Test public void multipleSqlStatementsRejected() {
        assertInvalid(() -> pg("SELECT 1; SELECT 2"));
    }

    @Test public void unbalancedParenthesesRejected() {
        assertInvalid(() -> pg("SELECT (1 FROM t"));
        assertInvalid(() -> pg("SELECT 1) FROM t"));
    }

    @Test public void preservesHintsAndPgParametersWithoutChangingThem() {
        String original = "select /*+ Index(t idx) */ x from t where id = $2";
        SqlSelect select = pg(original);
        select.setMark("'X'", "mark");
        String sql = select.buildSql();
        assertTrue(sql, sql.contains("/*+ Index(t idx) */"));
        assertTrue(sql, sql.contains("where id = $2"));
    }

    @Test public void handlesOrderByInWindowFunction() {
        SqlSelect select = pg("select row_number() over (partition by x order by y) as rn from t where x > 1");
        select.setOrderBy("rn desc");
        String result = select.buildSql();
        assertTrue(result, result.contains("partition by x order by y"));
        assertTrue(result, result.contains("ORDER BY rn desc"));
    }

    private static void assertInvalid(Runnable r) {
        try { r.run(); fail("Expected IllegalArgumentException"); }
        catch (IllegalArgumentException expected) { /* pass */ }
    }

    private static void assertUnsupported(Runnable r) {
        try { r.run(); fail("Expected UnsupportedOperationException"); }
        catch (UnsupportedOperationException expected) { /* pass */ }
    }
}
