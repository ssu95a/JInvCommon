package ru.inversion.utils.sparser.select;

import org.junit.Test;
import ru.inversion.utils.sparser.SqlDialect;
import ru.inversion.utils.sparser.jdbc.Parameter;
import ru.inversion.utils.sparser.jdbc.PreparedSql;

import java.math.BigInteger;
import java.util.Arrays;
import static org.junit.Assert.*;

/** Regressions for v0.3: dialect gaps and simplified assemble/scan pipeline. */
public class SqlSelectV03Test {
    private static PreparedSql pg(String sql) {
        return new SqlSelect(sql, SqlDialect.POSTGRES).prepare();
    }
    private static PreparedSql ora(String sql) {
        return new SqlSelect(sql, SqlDialect.ORACLE).prepare();
    }

    @Test public void postgresUescapeClauseLiteralIsNotBound() {
        PreparedSql result = pg("select U&'d!0061ta' UESCAPE '!' from t where k=12");
        assertTrue(result.getSql(), result.getSql().contains("UESCAPE '!'"));
        assertTrue(result.getSql().contains("where k=?"));
        assertEquals(1, result.getParameters().size());
    }

    @Test public void postgresUescapeWithTrivias() {
        PreparedSql result = pg("select U&'d!0061ta' UESCAPE /* comment */ '!' from t");
        assertTrue(result.getSql(), result.getSql().contains("UESCAPE /* comment */ '!'"));
        assertTrue(result.getParameters().isEmpty());
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

    @Test public void oracleIntervalPrecisionRemainsUntouched() {
        PreparedSql result = ora("select interval '123-2' year(3) to month from dual where id=2");
        assertTrue(result.getSql().contains("year(3) to month"));
        assertEquals(1, result.getParameters().size());
    }

    @Test public void rejectsSemicolonInsidePredicateParentheses() {
        SqlSelect sql = new SqlSelect("select x from t", SqlDialect.POSTGRES);
        try {
            sql.setPredicates(Arrays.asList("(1; 2)"));
            fail("Expected rejection");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("semicolon"));
        }
    }

    @Test public void quotedSemicolonIsNotStatementSeparator() {
        SqlSelect sql = new SqlSelect("select x from t", SqlDialect.POSTGRES);
        sql.setPredicates(Arrays.asList("name='a;b'"));
        assertTrue(sql.buildSql().contains("name='a;b'"));
    }

    @Test public void preparedSqlAndBuildSqlCachesAreInvalidatedTogether() {
        SqlSelect sql = new SqlSelect("select x from t where y=:y order by 1", SqlDialect.POSTGRES);
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
        SqlSelect sql = new SqlSelect("select x from t where y = :y order by x limit 20", SqlDialect.POSTGRES);
        sql.setPredicates(Arrays.asList("z > 7"));
        sql.setOrderBy("x desc, :sort");
        PreparedSql result = sql.prepare();
        assertTrue(result.getSql().contains("order by") || result.getSql().contains("ORDER BY"));
        assertTrue(result.getSql().contains("limit ?"));
        assertEquals(4, result.getParameters().size());
        assertEquals("sort", result.getParameters().get(2).getName());
    }
}
