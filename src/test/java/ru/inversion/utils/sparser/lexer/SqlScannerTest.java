package ru.inversion.utils.sparser.lexer;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;
import static ru.inversion.utils.sparser.lexer.SqlScanner.Kind.*;

/** Общие инварианты и диалектные регрессии SQL-сканнера. */
public class SqlScannerTest {

    private static final SqlScanner ORACLE = new OracleSqlScanner();
    private static final SqlScanner POSTGRES = new PostgresSqlScanner();

    @Test
    public void recognizesParametersButNotInsideQuotedTextOrComments() {
        String sql = "select :id, ?, $2, '?:x $3', \"?column\" /* :ignored */ -- ?\nfrom t";
        List<SqlScanner.Token> tokens = POSTGRES.scan(sql);
        assertEquals(1, count(tokens, POSITIONAL_PARAMETER));
        assertEquals(1, count(tokens, NAMED_PARAMETER));
        assertEquals(1, count(tokens, NUMBERED_PARAMETER));
        assertEquals(2, count(tokens, COMMENT));
        assertEquals(sql, recreate(sql, tokens));
    }

    @Test
    public void handlesOracleQQuotedStrings() {
        String sql = "select q'[don't :bind ?]', nq'<hello>' from dual where id = :id";
        List<SqlScanner.Token> tokens = ORACLE.scan(sql);
        assertEquals(2, count(tokens, STRING));
        assertEquals(1, count(tokens, NAMED_PARAMETER));
        assertEquals(sql, recreate(sql, tokens));
    }

    @Test
    public void handlesDollarQuotedStringsAndPgEscapes() {
        String sql = "select $func$ begin raise notice '$1 :x ?'; end $func$, "
                + "E'quote\\'inside', $2, U&\"na\\006De\"";
        List<SqlScanner.Token> tokens = POSTGRES.scan(sql);
        assertEquals(2, count(tokens, STRING));
        assertEquals(1, count(tokens, NUMBERED_PARAMETER));
        assertEquals(0, count(tokens, POSITIONAL_PARAMETER));
        assertEquals(sql, recreate(sql, tokens));
    }

    @Test
    public void distinguishesPgAndOracleBlockComments() {
        String sql = "/* one /* two */ three */ select 1";
        assertEquals("/* one /* two */ three */", POSTGRES.scan(sql).get(0).text(sql));
        assertEquals("/* one /* two */", ORACLE.scan(sql).get(0).text(sql));
    }

    @Test
    public void keepsQuestionMarkEscapeForPgJdbc() {
        String sql = "select data ?? 'key', x = ? from t";
        List<SqlScanner.Token> tokens = POSTGRES.scan(sql);
        assertEquals(1, count(tokens, POSITIONAL_PARAMETER));
        assertTrue(containsText(sql, tokens, OPERATOR, "??"));
    }

    @Test
    public void recognizesNumbersIncludingPostgresNondecimalLiterals() {
        String sql = "select 1_000, 3.5e-2, .01, 10., 0xFF_FF, 0b1001, 0o_755";
        List<SqlScanner.Token> tokens = POSTGRES.scan(sql);
        assertEquals(7, count(tokens, NUMBER));
        assertEquals(sql, recreate(sql, tokens));
    }

    @Test
    public void handlesCastsAndWordBoundariesWithoutParsingThem() {
        String sql = "select cast(:x as varchar2(100)) from t where y = :y::int";
        List<SqlScanner.Token> tokens = POSTGRES.scan(sql);
        assertEquals(2, count(tokens, NAMED_PARAMETER));
        assertTrue(containsText(sql, tokens, OPERATOR, "::"));
        assertTrue(containsText(sql, tokens, NUMBER, "100"));
        assertEquals(sql, recreate(sql, tokens));
    }

    @Test
    public void rejectsUnterminatedLexemes() {
        rejects(POSTGRES, "select 'broken");
        rejects(POSTGRES, "select /* broken");
        rejects(POSTGRES, "select $q$broken");
        rejects(ORACLE, "select q'[broken'");
    }

    @Test
    public void scansEmptyInput() {
        assertEquals(0, ORACLE.scan("").size());
        assertEquals(0, POSTGRES.scan("").size());
    }

    @Test
    public void keepsOracleOptimizerHintsOpaque() {
        String sql = "select /*+ INDEX(t idx) where x=:hidden '?' */ :real from t";
        List<SqlScanner.Token> tokens = ORACLE.scan(sql);
        assertEquals(1, count(tokens, HINT));
        assertEquals(0, count(tokens, COMMENT));
        assertEquals(1, count(tokens, NAMED_PARAMETER));
        assertEquals(0, count(tokens, POSITIONAL_PARAMETER));
        assertEquals(sql, recreate(sql, tokens));
    }

    @Test
    public void oracleNationalStringsAndDifferentQDelimiters() {
        String sql = "select N'It''s :hidden', q'{a'b}', Q'(x)', q'!y!', Q'<z>' from dual";
        List<SqlScanner.Token> tokens = ORACLE.scan(sql);
        assertEquals(5, count(tokens, STRING));
        assertEquals(0, count(tokens, NAMED_PARAMETER));
        assertEquals(sql, recreate(sql, tokens));
    }

    @Test
    public void postgresNestedCommentsAndEmptyDollarStrings() {
        String sql = "/* A /* B /* C */ B */ A */ $$?$3 :no$$ $a1$'?'$a1$ :yes";
        List<SqlScanner.Token> tokens = POSTGRES.scan(sql);
        assertEquals(1, count(tokens, COMMENT));
        assertEquals(2, count(tokens, STRING));
        assertEquals(1, count(tokens, NAMED_PARAMETER));
        assertEquals(0, count(tokens, POSITIONAL_PARAMETER));
        assertEquals(sql, recreate(sql, tokens));
    }

    @Test
    public void postgresOperatorAndNamedParameterPriority() {
        String sql = "select a::integer, b := :x, payload ??| array['a'], :x::text, ?";
        List<SqlScanner.Token> tokens = POSTGRES.scan(sql);
        assertEquals(2, count(tokens, NAMED_PARAMETER));
        assertEquals(1, count(tokens, POSITIONAL_PARAMETER));
        assertEquals(2, textCount(sql, tokens, OPERATOR, "::"));
        assertTrue(containsText(sql, tokens, OPERATOR, ":="));
        assertTrue(containsText(sql, tokens, OPERATOR, "??|"));
        assertEquals(sql, recreate(sql, tokens));
    }

    @Test
    public void distinguishesNumberedParametersFromDollarQuotes() {
        String sql = "select $1, $12, $abc2$:$1?$abc2$, $2";
        List<SqlScanner.Token> tokens = POSTGRES.scan(sql);
        assertEquals(3, count(tokens, NUMBERED_PARAMETER));
        assertEquals(1, count(tokens, STRING));
        assertEquals(0, count(tokens, POSITIONAL_PARAMETER));
        assertEquals(sql, recreate(sql, tokens));
    }

    @Test
    public void postgresUnicodeStringAndIdentifier() {
        String sql = "select U&'d\\0061t', u&\"na\\006De\", E'a\\\\b'";
        List<SqlScanner.Token> tokens = POSTGRES.scan(sql);
        assertEquals(2, count(tokens, STRING));
        assertEquals(1, count(tokens, QUOTED_IDENTIFIER));
        assertEquals(sql, recreate(sql, tokens));
    }

    @Test
    public void normalQuotedStringsWithDoubledQuotes() {
        String sql = "select 'don''t :x ?', \"col" + "\"\"" + "name\" from t";
        for (SqlScanner scanner : Arrays.asList(ORACLE, POSTGRES)) {
            List<SqlScanner.Token> tokens = scanner.scan(sql);
            assertEquals(1, count(tokens, STRING));
            assertEquals(1, count(tokens, QUOTED_IDENTIFIER));
            assertEquals(0, count(tokens, NAMED_PARAMETER));
            assertEquals(0, count(tokens, POSITIONAL_PARAMETER));
            assertEquals(sql, recreate(sql, tokens));
        }
    }

    @Test
    public void caseInsensitiveWordDetection() {
        String sql = "SeLeCt selected SELECT";
        List<SqlScanner.Token> tokens = ORACLE.scan(sql);
        assertTrue(tokens.get(0).isWord(sql, "select"));
        assertFalse(tokens.get(2).isWord(sql, "select"));
        assertTrue(tokens.get(4).isWord(sql, "SELECT"));
    }

    @Test
    public void returnsImmutableTokensAndList() {
        List<SqlScanner.Token> tokens = POSTGRES.scan("select 1");
        try {
            tokens.clear();
            fail("Must be immutable");
        } catch (UnsupportedOperationException expected) {
            // okay
        }
    }

    @Test
    public void directivesAreSpecialTokensButHaveNoEffectOnScanner() {
        String sql = "/* @parameterize:off */ select 'value', :id /* @parameterize:on */";
        List<SqlScanner.Token> tokens = ORACLE.scan(sql);
        assertEquals(1, count(tokens, PARAMETERIZE_OFF));
        assertEquals(1, count(tokens, PARAMETERIZE_ON));
        assertEquals(0, count(tokens, COMMENT));
        assertEquals(1, count(tokens, STRING));
        assertEquals(1, count(tokens, NAMED_PARAMETER));
        assertEquals(sql, recreate(sql, tokens));
    }

    @Test
    public void punctuationAndWhitespacePreserveAllSourceBytes() {
        String sql = "\tSELECT\r\n(a.b), 1..2; --end\r\n";
        for (SqlScanner scanner : Arrays.asList(ORACLE, POSTGRES)) {
            assertEquals(sql, recreate(sql, scanner.scan(sql)));
        }
    }

    @Test
    public void oracleDoesNotRecognizePostgresNumberedParameters() {
        String sql = "select $1, $tag$abc$tag$ from dual";
        List<SqlScanner.Token> tokens = ORACLE.scan(sql);
        assertEquals(0, count(tokens, NUMBERED_PARAMETER));
        assertEquals(0, count(tokens, STRING));
        assertEquals(sql, recreate(sql, tokens));
    }

    @Test
    public void errorsReportStartOfPrefixedLiteral() {
        rejects(POSTGRES, "select E'unclosed");
        rejects(POSTGRES, "select U&\"unclosed");
        rejects(ORACLE, "select N'unclosed");
        rejects(ORACLE, "select nq'{unclosed'");
    }

    @Test
    public void commentsHintsAndDirectivesHaveExplicitKinds() {
        String sql = "/* @parameterize:off */select /*+ index(t idx) */ :id "
                + "/* just a comment */ /* @parameterize:on */";
        for (SqlScanner scanner : Arrays.asList(ORACLE, POSTGRES)) {
            List<SqlScanner.Token> tokens = scanner.scan(sql);
            assertEquals(1, count(tokens, PARAMETERIZE_OFF));
            assertEquals(1, count(tokens, PARAMETERIZE_ON));
            assertEquals(1, count(tokens, HINT));
            assertEquals(1, count(tokens, COMMENT));
            assertEquals(1, count(tokens, NAMED_PARAMETER));
            assertEquals(sql, recreate(sql, tokens));
        }
    }

    @Test
    public void invalidParameterizeDirectiveDoesNotSilentlyBecomeComment() {
        for (SqlScanner scanner : Arrays.asList(ORACLE, POSTGRES)) {
            rejects(scanner, "/* @parameterize:of */ select 1");
            rejects(scanner, "/* @parameterize:on trailing */ select 1");
        }
    }

    @Test
    public void postgresOperatorsIncludeUserDefinedAndJdbcEscapes() {
        String sql = "select a @?? b, a ??| array['k'], a ??& array['a'], "
                + "x->>'id', x#>>'{a}', x !~* 'pattern', col::integer, :id, ? from t";
        List<SqlScanner.Token> tokens = POSTGRES.scan(sql);
        assertEquals(1, count(tokens, POSITIONAL_PARAMETER));
        assertEquals(1, count(tokens, NAMED_PARAMETER));
        for (String operator : Arrays.asList("@??", "??|", "??&", "->>", "#>>", "!~*", "::")) {
            assertTrue("Expected OPERATOR: " + operator, containsText(sql, tokens, OPERATOR, operator));
        }
        assertEquals(sql, recreate(sql, tokens));
    }

    @Test
    public void unescapedPostgresQuestionMarkStaysJdbcParameter() {
        // Input to PreparedStatement must escape the ? in an operator as ??.
        String sql = "select data ?| array['a'], j @? '$.x', value = ?";
        List<SqlScanner.Token> tokens = POSTGRES.scan(sql);
        assertEquals(3, count(tokens, POSITIONAL_PARAMETER));
        assertEquals(sql, recreate(sql, tokens));
    }

    @Test
    public void oracleOperatorsDoNotConsumeNamedParameters() {
        String sql = "select a||b, f(a => :name), a <= 3, b <> 2, c ** 2, :id from dual";
        List<SqlScanner.Token> tokens = ORACLE.scan(sql);
        assertEquals(2, count(tokens, NAMED_PARAMETER));
        for (String operator : Arrays.asList("||", "=>", "<=", "<>", "**")) {
            assertTrue("Expected OPERATOR: " + operator, containsText(sql, tokens, OPERATOR, operator));
        }
        assertEquals(sql, recreate(sql, tokens));
    }

    @Test
    public void operatorStopsBeforeCommentsAndPgSplitSuffixes() {
        String sql = "select x *- y, y+--comment\n z/* block */+1";
        List<SqlScanner.Token> tokens = POSTGRES.scan(sql);
        assertTrue(containsText(sql, tokens, OPERATOR, "*"));
        assertTrue(containsText(sql, tokens, OPERATOR, "-"));
        assertTrue(containsText(sql, tokens, OPERATOR, "+"));
        assertEquals(2, count(tokens, COMMENT));
        assertEquals(sql, recreate(sql, tokens));
    }

    @Test
    public void postgresHashIsOperatorNotPartOfIdentifier() {
        String sql = "select obj#>>'{a}', obj#>$1, obj#$2 from t";
        List<SqlScanner.Token> tokens = POSTGRES.scan(sql);
        assertEquals(2, count(tokens, NUMBERED_PARAMETER));
        assertTrue(containsText(sql, tokens, OPERATOR, "#>>"));
        assertTrue(containsText(sql, tokens, OPERATOR, "#>"));
        assertTrue(containsText(sql, tokens, OPERATOR, "#"));
        assertEquals(sql, recreate(sql, tokens));
    }

    @Test
    public void oracleIdentifierMayContainHash() {
        String sql = "select customer#id from t where id=:a#b";
        List<SqlScanner.Token> tokens = ORACLE.scan(sql);
        assertTrue(containsText(sql, tokens, WORD, "customer#id"));
        assertTrue(containsText(sql, tokens, NAMED_PARAMETER, ":a#b"));
        assertEquals(sql, recreate(sql, tokens));
    }

    @Test
    public void directivesAcceptWhitespaceAndMixedCase() {
        String sql = "/*  @PaRaMeTeRiZe : off  */ select 'x' /*\n@parameterize\t: ON\n*/";
        for (SqlScanner scanner : Arrays.asList(ORACLE, POSTGRES)) {
            List<SqlScanner.Token> tokens = scanner.scan(sql);
            assertEquals(1, count(tokens, PARAMETERIZE_OFF));
            assertEquals(1, count(tokens, PARAMETERIZE_ON));
            assertEquals(sql, recreate(sql, tokens));
        }
    }

    @Test
    public void malformedDirectivesAreRejected() {
        String[] examples = {
            "/* @parameterize : maybe */",
            "/* @parameterize:off trailing */",
            "/* @parameterize:onward */",
            "/* @parameterize:off */ /* @parameterize:invalid */"
        };
        for (SqlScanner scanner : Arrays.asList(ORACLE, POSTGRES)) {
            for (String sql : examples) rejects(scanner, sql);
        }
    }

    @Test
    public void unrelatedCommentsAreNotDirectives() {
        String sql = "/* random @parameterize:off */ /* @other:off */ select 1";
        for (SqlScanner scanner : Arrays.asList(ORACLE, POSTGRES)) {
            List<SqlScanner.Token> tokens = scanner.scan(sql);
            assertEquals(2, count(tokens, COMMENT));
            assertEquals(0, count(tokens, PARAMETERIZE_OFF));
            assertEquals(sql, recreate(sql, tokens));
        }
    }

    @Test
    public void oracleQQuoteRejectsWhitespaceAndQuoteDelimiters() {
        rejects(ORACLE, "select q' abc ' from dual");
        rejects(ORACLE, "select q'\tabc\t' from dual");
        rejects(ORACLE, "select nq''abc'' from dual");
    }

    @Test
    public void oracleQQuoteSupportsPairedAndUnpairedDelimiters() {
        String sql = "select q'[a ? :x]', q'{b}', q'(c)', q'<d>', q'!e!' from dual";
        List<SqlScanner.Token> tokens = ORACLE.scan(sql);
        assertEquals(5, count(tokens, STRING));
        assertEquals(0, count(tokens, NAMED_PARAMETER));
        assertEquals(sql, recreate(sql, tokens));
    }

    @Test
    public void underscoresInNumbersArePostgresSpecific() {
        String sql = "select 1_000, 0xFF, 0b1010 from t";
        assertTrue(containsText(sql, POSTGRES.scan(sql), NUMBER, "1_000"));
        assertTrue(containsText(sql, POSTGRES.scan(sql), NUMBER, "0xFF"));
        assertTrue(containsText(sql, POSTGRES.scan(sql), NUMBER, "0b1010"));
        assertFalse(containsText(sql, ORACLE.scan(sql), NUMBER, "1_000"));
        assertFalse(containsText(sql, ORACLE.scan(sql), NUMBER, "0xFF"));
        assertEquals(sql, recreate(sql, ORACLE.scan(sql)));
        assertEquals(sql, recreate(sql, POSTGRES.scan(sql)));
    }

    @Test
    public void directiveAndHintPrecedence() {
        String sql = "/* @parameterize : off */ /*+ INDEX(t i) */ /* normal */";
        for (SqlScanner scanner : Arrays.asList(ORACLE, POSTGRES)) {
            List<SqlScanner.Token> tokens = scanner.scan(sql);
            assertEquals(1, count(tokens, PARAMETERIZE_OFF));
            assertEquals(1, count(tokens, HINT));
            assertEquals(1, count(tokens, COMMENT));
        }
    }

    private static int count(List<SqlScanner.Token> tokens, SqlScanner.Kind kind) {
        int result = 0;
        for (SqlScanner.Token token : tokens) if (token.getKind() == kind) result++;
        return result;
    }

    private static int textCount(String sql, List<SqlScanner.Token> tokens,
                                 SqlScanner.Kind kind, String text) {
        int result = 0;
        for (SqlScanner.Token token : tokens) {
            if (token.getKind() == kind && token.text(sql).equals(text)) result++;
        }
        return result;
    }

    private static boolean containsText(String sql, List<SqlScanner.Token> tokens,
                                        SqlScanner.Kind kind, String text) {
        return textCount(sql, tokens, kind, text) > 0;
    }

    private static String recreate(String sql, List<SqlScanner.Token> tokens) {
        StringBuilder result = new StringBuilder();
        int offset = 0;
        for (SqlScanner.Token token : tokens) {
            assertEquals(offset, token.getStart());
            assertTrue(token.getEnd() > token.getStart());
            result.append(token.text(sql));
            offset = token.getEnd();
        }
        assertEquals(sql.length(), offset);
        return result.toString();
    }

    private static void rejects(SqlScanner scanner, String sql) {
        try {
            scanner.scan(sql);
            fail("Expected error for: " + sql);
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("offset"));
        }
    }
}
