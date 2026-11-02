package ru.inversion.utils.sparser.lexer;

import org.junit.Test;
import ru.inversion.db.dialect.SqlDialectFactory;
import ru.inversion.utils.sparser.SqlScannerFactory;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;
import static ru.inversion.utils.sparser.lexer.SqlScanner.Kind.*;

/**
 * Contract tests for the intentionally small SQL lexer used by sparser.
 * It is not a general Oracle/PostgreSQL lexer.
 */
public class SqlScannerTest {

    private static final SqlScanner ORACLE = new OracleSqlScanner();
    private static final SqlScanner POSTGRES = new PostgresSqlScanner();

    @Test
    public void factorySelectsDialectScanner() {
        assertTrue(SqlScannerFactory.create(SqlDialectFactory.ORACLE)
                instanceof OracleSqlScanner);
        assertTrue(SqlScannerFactory.create(SqlDialectFactory.POSTGRES)
                instanceof PostgresSqlScanner);
    }

    @Test
    public void recognizesSupportedParametersOutsideQuotedTextAndComments() {
        String sql =
                "select :id, ?, $2, 'ABC123', \"COL\" /* :ignored ? $3 */ -- :x\nfrom t";

        List<SqlScanner.Token> tokens = POSTGRES.scan(sql);

        assertEquals(1, count(tokens, NAMED_PARAMETER));
        assertEquals(1, count(tokens, POSITIONAL_PARAMETER));
        assertEquals(1, count(tokens, NUMBERED_PARAMETER));
        assertEquals(1, count(tokens, STRING));
        assertEquals(1, count(tokens, QUOTED_IDENTIFIER));
        assertEquals(2, count(tokens, COMMENT));
        assertEquals(sql, recreate(sql, tokens));
    }

    @Test
    public void ordinaryNumbersAreSupported() {
        String sql = "select 1, 12.34, .5, 10., 3.25e2 from t";

        List<SqlScanner.Token> tokens = POSTGRES.scan(sql);

        assertEquals(5, count(tokens, NUMBER));
        assertEquals(sql, recreate(sql, tokens));
    }

    @Test
    public void postgresNumberedParametersCastAndJdbcQuestionEscapeAreSupported() {
        String sql =
                "select :x::numeric(10,2), data ?? 'key', $1, ? from t";

        List<SqlScanner.Token> tokens = POSTGRES.scan(sql);

        assertEquals(1, count(tokens, NAMED_PARAMETER));
        assertEquals(1, count(tokens, NUMBERED_PARAMETER));
        assertEquals(1, count(tokens, POSITIONAL_PARAMETER));
        assertTrue(containsText(sql, tokens, OPERATOR, "::"));
        assertTrue(containsText(sql, tokens, OPERATOR, "??"));
        assertEquals(sql, recreate(sql, tokens));
    }


    @Test
    public void postgresOperatorsPreserveLegacyConcatMarkers() {
        String sql =
                "select ~||name||~, value ~ :pattern, data ?? 'key' from t";

        List<SqlScanner.Token> tokens = POSTGRES.scan(sql);

        assertTrue(containsText(sql, tokens, OPERATOR, "~||"));
        assertTrue(containsText(sql, tokens, OPERATOR, "||~"));
        assertTrue(containsText(sql, tokens, OPERATOR, "~"));
        assertTrue(containsText(sql, tokens, OPERATOR, "??"));
        assertEquals(sql, recreate(sql, tokens));
    }

    @Test
    public void commentsHintsAndParameterizeDirectivesAreOpaque() {
        String sql =
                "/* @parameterize:off */ select /*+ INDEX(t i) */ :id "
                + "/* ? :hidden */ /* @parameterize:on */";

        List<SqlScanner.Token> tokens = ORACLE.scan(sql);

        assertEquals(1, count(tokens, PARAMETERIZE_OFF));
        assertEquals(1, count(tokens, PARAMETERIZE_ON));
        assertEquals(1, count(tokens, HINT));
        assertEquals(1, count(tokens, COMMENT));
        assertEquals(1, count(tokens, NAMED_PARAMETER));
        assertEquals(0, count(tokens, POSITIONAL_PARAMETER));
        assertEquals(sql, recreate(sql, tokens));
    }

    @Test
    public void postgresNestedBlockCommentsRemainOpaque() {
        String sql = "/* outer /* inner :x ? */ outer */ select :real";

        List<SqlScanner.Token> tokens = POSTGRES.scan(sql);

        assertEquals(1, count(tokens, COMMENT));
        assertEquals(1, count(tokens, NAMED_PARAMETER));
        assertEquals(0, count(tokens, POSITIONAL_PARAMETER));
        assertEquals(sql, recreate(sql, tokens));
    }

    @Test
    public void oracleIdentifiersMayContainHash() {
        String sql = "select customer#id from t where id=:a#b";

        List<SqlScanner.Token> tokens = ORACLE.scan(sql);

        assertTrue(containsText(sql, tokens, WORD, "customer#id"));
        assertTrue(containsText(sql, tokens, NAMED_PARAMETER, ":a#b"));
        assertEquals(sql, recreate(sql, tokens));
    }

    @Test
    public void sqlKeywordsAreClassifiedWithoutChangingWordTokens() {
        String sql = "SeLeCt customer FROM orders WHERE id = 1";
        List<SqlScanner.Token> tokens = POSTGRES.scan(sql);

        assertEquals(WORD, tokens.get(0).kind());
        assertEquals(SqlKeyword.SELECT, tokens.get(0).keyword(sql));
        assertEquals(SqlKeyword.FROM, findWord(sql, tokens, "FROM").keyword(sql));
        assertEquals(SqlKeyword.WHERE, findWord(sql, tokens, "WHERE").keyword(sql));
        assertNull(findWord(sql, tokens, "customer").keyword(sql));
    }

    @Test
    public void structuralPunctuationCoversSourceWithoutGaps() {
        String sql = "\tSELECT\r\n(a.b), arr[1]; --end\r\n";

        for (SqlScanner scanner : Arrays.asList(ORACLE, POSTGRES)) {
            List<SqlScanner.Token> tokens = scanner.scan(sql);
            for (String symbol : Arrays.asList("(", ")", ".", ",", "[", "]", ";")) {
                assertTrue("Expected SYMBOL: " + symbol,
                        containsText(sql, tokens, SYMBOL, symbol));
            }
            assertEquals(sql, recreate(sql, tokens));
        }
    }

    @Test
    public void unsupportedPrefixedStringFormsAreRejected() {
        rejects(ORACLE, "select q'[abc]' from dual");
        rejects(ORACLE, "select nq'<abc>' from dual");
        rejects(ORACLE, "select N'abc' from dual");

        rejects(POSTGRES, "select E'abc'");
        rejects(POSTGRES, "select U&'abc'");
        rejects(POSTGRES, "select U&\"name\"");
        rejects(POSTGRES, "select $$abc$$");
        rejects(POSTGRES, "select $tag$abc$tag$");
    }

    @Test
    public void unsupportedNumericFormsAreRejected() {
        for (String sql : Arrays.asList(
                "select 1_000",
                "select 0xFF",
                "select 0b1010",
                "select 0o755")) {
            rejects(POSTGRES, sql);
        }

        rejects(ORACLE, "select 1f from dual");
        rejects(ORACLE, "select 1d from dual");
    }

    @Test
    public void malformedOrUnknownLexemesAreRejected() {
        rejects(POSTGRES, "select 'broken");
        rejects(POSTGRES, "select /* broken");
        rejects(ORACLE, "select 'broken");
        rejects(ORACLE, "select a \\ b from t");
        rejects(POSTGRES, "select {a} from t");
    }

    private static SqlScanner.Token findWord(
            String sql,
            List<SqlScanner.Token> tokens,
            String text
    ) {
        for (SqlScanner.Token token : tokens) {
            if (token.kind() == WORD && token.text(sql).equalsIgnoreCase(text))
                return token;
        }
        fail("WORD not found: " + text);
        return null;
    }

    private static int count(
            List<SqlScanner.Token> tokens,
            SqlScanner.Kind kind
    ) {
        int result = 0;
        for (SqlScanner.Token token : tokens)
            if (token.kind() == kind)
                result++;
        return result;
    }

    private static boolean containsText(
            String sql,
            List<SqlScanner.Token> tokens,
            SqlScanner.Kind kind,
            String text
    ) {
        for (SqlScanner.Token token : tokens) {
            if (token.kind() == kind && token.text(sql).equals(text))
                return true;
        }
        return false;
    }

    private static String recreate(
            String sql,
            List<SqlScanner.Token> tokens
    ) {
        StringBuilder result = new StringBuilder();
        int offset = 0;

        for (SqlScanner.Token token : tokens) {
            assertEquals(offset, token.start());
            assertTrue(token.end() > token.start());
            result.append(token.text(sql));
            offset = token.end();
        }

        assertEquals(sql.length(), offset);
        return result.toString();
    }

    private static void rejects(SqlScanner scanner, String sql) {
        try {
            scanner.scan(sql);
            fail("Expected error for: " + sql);
        }
        catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("offset"));
        }
    }
}
