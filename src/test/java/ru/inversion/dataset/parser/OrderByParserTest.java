package ru.inversion.dataset.parser;

import org.junit.Test;
import ru.inversion.dataset.parser.lexer.OracleSqlScanner;
import ru.inversion.dataset.parser.lexer.PostgresSqlScanner;
import ru.inversion.dataset.parser.select.SqlParameterizer;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class OrderByParserTest {

    @Test
    public void emptyExpressionHasNoItems() {
        assertEquals(
                Collections.emptyList(),
                OrderByParser.parseAndGetColumnsList(null)
        );
        assertEquals(
                Collections.emptyList(),
                OrderByParser.parseAndGetColumnsList("")
        );
    }

    @Test
    public void splitsOnlyTopLevelCommas() {
        assertEquals(
                Arrays.asList(
                        "account_id",
                        "coalesce(priority, 0)",
                        "case when state = 1 then created_at else updated_at end"
                ),
                OrderByParser.parseAndGetColumnsList(
                        "account_id, coalesce(priority, 0), "
                        + "case when state = 1 then created_at else updated_at end"
                )
        );
    }

    @Test
    public void preservesCommasInQuotedTextAndComments() {
        assertEquals(
                Arrays.asList(
                        "'a,b'",
                        "\"c,d\"",
                        "value /* keep , here */",
                        "other -- keep , here",
                        "last"
                ),
                OrderByParser.parseAndGetColumnsList(
                        "'a,b', \"c,d\", value /* keep , here */, "
                        + "other -- keep , here\n, last"
                )
        );
    }

    @Test
    public void preservesEmptyItemsForCompatibility() {
        assertEquals(
                Arrays.asList("first", "", "last", ""),
                OrderByParser.parseAndGetColumnsList(
                        "first,,last,"
                )
        );
    }

    @Test
    public void splitsOnlyTopLevelFilterOrderByWithoutRewritingSql() {
        String filter = "id in (select id from history order by created_at) "
                + "and note = 'order by hidden' /* order by hidden */ "
                + "ORDER /* between */ BY coalesce(name, 'a,b') DESC";

        assertArrayEquals(
                new String[] {
                        "id in (select id from history order by created_at) "
                                + "and note = 'order by hidden' /* order by hidden */",
                        "coalesce(name, 'a,b') DESC"
                },
                OrderByParser.splitFilter(filter, new OracleSqlScanner())
        );
    }

    @Test
    public void keepsOrderByInsidePostgresLiteralsAndCastsInPredicate() {
        String filter = "note = 'order by hidden' and value::text = :text "
                + "ORDER BY id DESC";

        assertArrayEquals(
                new String[] {
                        "note = 'order by hidden' and value::text = :text",
                        "id DESC"
                },
                OrderByParser.splitFilter(filter, new PostgresSqlScanner())
        );

        String withoutOrder = "note = 'order by hidden' and id in "
                + "(select id from child order by name)";
        assertArrayEquals(
                new String[] {withoutOrder, null},
                OrderByParser.splitFilter(withoutOrder, new PostgresSqlScanner())
        );
    }

    @Test
    public void terminatesTrailingLineCommentBeforeSqlIsAppended() {
        String[] parts = OrderByParser.splitFilter(
                "id = :id order by name DESC -- tail",
                new OracleSqlScanner()
        );

        assertArrayEquals(
                new String[] {"id = :id", "name DESC -- tail\n"},
                parts
        );
        assertEquals(
                "select id from t where id = ? ORDER BY name DESC -- tail\n",
                SqlParameterizer.prepare(
                        "select id from t where " + parts[0] + " ORDER BY ~" + parts[1] + "~",
                        new OracleSqlScanner()
                ).getSql()
        );
    }

    @Test
    public void removesTrailingLineCommentFromExtractedPredicate() {
        assertArrayEquals(
                new String[] {"id = :id", "name DESC"},
                OrderByParser.splitFilter(
                        "id = :id -- comment\n ORDER BY name DESC",
                        new OracleSqlScanner()
                )
        );
    }
}
