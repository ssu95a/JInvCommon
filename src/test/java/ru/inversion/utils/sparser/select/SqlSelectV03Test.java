package ru.inversion.utils.sparser.select;

import org.junit.Test;
import ru.inversion.db.dialect.SqlDialectFactory;
import ru.inversion.utils.sparser.jdbc.Parameter;
import ru.inversion.utils.sparser.jdbc.PreparedSql;

import java.math.BigInteger;
import java.util.Arrays;
import static org.junit.Assert.*;

/** Regressions for v0.3: dialect gaps and simplified assemble/scan pipeline. */
public class SqlSelectV03Test {
    private static PreparedSql pg(String sql) {
        return new SqlSelect(sql, SqlDialectFactory.POSTGRES).prepare();
    }
    private static PreparedSql ora(String sql) {
        return new SqlSelect(sql, SqlDialectFactory.ORACLE).prepare();
    }

    @Test public void postgresUnicodeStringIsRejected() {
        try { pg("select U&'data' from t"); fail("Expected rejection"); }
        catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("offset")); }
    }
    @Test public void postgresEscapeStringIsRejected() {
        try { pg("select E'data' from t"); fail("Expected rejection"); }
        catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("offset")); }
    }
    @Test public void oracleBinaryFloatAndDoubleSuffixesPreserved() {
        PreparedSql result = ora("select 1f, 1F, 1d, 1D, 1.25f, 1e10d from dual where n=99");
        assertTrue(result.getSql(), result.getSql().contains("select 1f, 1F, 1d, 1D, 1.25f, 1e10d"));
        assertEquals(1, result.getParameters().size());
        assertEquals(new BigInteger("99"), result.getParameters().get(0).getValue());
    }

    @Test public void postgresMultitokenCastTypesStayUntouched() {
        PreparedSql result = pg("select x::character varying(32), b::bit varying(16) "
                + "from t where id=5");
        assertTrue(result.getSql(), result.getSql().contains("character varying(32)"));
        assertTrue(result.getSql(), result.getSql().contains("bit varying(16)"));
        assertEquals(1, result.getParameters().size());
    }

    @Test public void oracleIntervalStringWithPunctuationIsRejected() {
        try { ora("select interval '123-2' year(3) to month from dual"); fail("Expected rejection"); }
        catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("offset")); }
    }
    @Test public void rejectsSemicolonInsidePredicateParentheses() {
        SqlSelect sql = new SqlSelect("select x from t", SqlDialectFactory.POSTGRES);
        try {
            sql.setPredicates(Arrays.asList("(1; 2)"));
            fail("Expected rejection");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("semicolon"));
        }
    }

    @Test public void semicolonInsideStringIsRejected() {
        SqlSelect sql = new SqlSelect("select x from t", SqlDialectFactory.POSTGRES);
        try { sql.setPredicates(Arrays.asList("name='a;b'")); fail("Expected rejection"); }
        catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("offset")); }
    }
    @Test public void preparedSqlAndBuildSqlCachesAreInvalidatedTogether() {
        SqlSelect sql = new SqlSelect("select x from t where y=:y order by 1", SqlDialectFactory.POSTGRES);
        PreparedSql first = sql.prepare();
        assertSame(first, sql.prepare());
        sql.setPredicates(Arrays.asList("z=?", "a=9"));
        PreparedSql next = sql.prepare();
        assertTrue(next != first);
        assertEquals(3, next.getParameters().size());
        assertEquals(Parameter.Kind.POSITIONAL, next.getParameters().get(0).getKind());
        assertEquals("y", next.getParameters().get(2).getName());
    }

    @Test public void predicateAndOrderByEditsTogether() {
        SqlSelect sql = new SqlSelect("select x from t where y = :y order by x limit 20", SqlDialectFactory.POSTGRES);
        sql.setPredicates(Arrays.asList("z > 7"));
        sql.setOrderBy("x desc, :sort");
        PreparedSql result = sql.prepare();
        assertTrue(result.getSql().contains("order by") || result.getSql().contains("ORDER BY"));
        assertTrue(result.getSql().contains("limit ?"));
        assertEquals(4, result.getParameters().size());
        assertEquals("sort", result.getParameters().get(2).getName());
    }
}
