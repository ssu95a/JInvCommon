package ru.inversion.dataset.parser;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

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
}
