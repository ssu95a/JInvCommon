package ru.inversion.dataset.impl;

import org.junit.Test;
import ru.inversion.dataset.IParameters;
import ru.inversion.utils.sparser.jdbc.Parameter;
import ru.inversion.utils.sparser.jdbc.PreparedSql;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class PreparedSqlValuesTest {

    @Test
    public void resolvePreservesRawApplicationValues() {
        BigInteger userInteger = new BigInteger("2");

        PreparedSql sql = prepared(
                Parameter.named("empty"),
                Parameter.named("flag"),
                Parameter.named("longValue"),
                Parameter.named("bigInteger"),
                Parameter.literal(new BigInteger("10"))
        );

        Params parameters = new Params()
                .named("empty", "")
                .named("flag", true)
                .named("longValue", 7L)
                .named("bigInteger", userInteger);

        assertEquals(
                Arrays.<Object>asList(
                        "",
                        true,
                        7L,
                        userInteger,
                        new BigInteger("10")
                ),
                PreparedSqlValues.resolve(sql, parameters)
        );
    }

    @Test
    public void jdbcAppliesLegacyRuntimeAndGeneratedLiteralConversion() {
        BigInteger userInteger = new BigInteger("2");

        PreparedSql sql = prepared(
                Parameter.named("empty"),
                Parameter.named("flag"),
                Parameter.named("longValue"),
                Parameter.named("bigInteger"),
                Parameter.literal(new BigInteger("10")),
                Parameter.literal(new BigInteger("2147483648"))
        );

        Params parameters = new Params()
                .named("empty", "")
                .named("flag", true)
                .named("longValue", 7L)
                .named("bigInteger", userInteger);

        assertEquals(
                Arrays.<Object>asList(
                        null,
                        1,
                        7,
                        userInteger,
                        10,
                        2147483648L
                ),
                PreparedSqlValues.jdbc(sql, parameters)
        );
    }

    @Test
    public void alreadyResolvedValuesUseParameterKindForLiteralNarrowing() {
        BigInteger sameNumber = new BigInteger("10");

        PreparedSql sql = prepared(
                Parameter.literal(sameNumber),
                Parameter.named("userValue")
        );

        assertEquals(
                Arrays.<Object>asList(10, sameNumber),
                PreparedSqlValues.jdbc(
                        sql,
                        Arrays.<Object>asList(sameNumber, sameNumber)
                )
        );
    }

    @Test
    public void positionalAndNumberedParametersShareIndexedNamespace() {
        PreparedSql sql = prepared(
                Parameter.positional(),
                Parameter.numbered(1),
                Parameter.positional(),
                Parameter.numbered(2)
        );

        Params parameters = new Params()
                .at(0, "A")
                .at(1, "B");

        assertEquals(
                Arrays.<Object>asList("A", "A", "B", "B"),
                PreparedSqlValues.resolve(sql, parameters)
        );
    }

    @Test
    public void literalOnlyPlanAllowsNullRuntimeParameters() {
        PreparedSql sql = prepared(
                Parameter.literal("A"),
                Parameter.literal(new BigInteger("10"))
        );

        assertEquals(
                Arrays.<Object>asList("A", new BigInteger("10")),
                PreparedSqlValues.resolve(sql, null)
        );

        assertEquals(
                Arrays.<Object>asList("A", 10),
                PreparedSqlValues.jdbc(sql, (IParameters) null)
        );
    }

    @Test
    public void nullRuntimeParametersFailAtFirstRuntimeParameter() {
        PreparedSql sql = prepared(
                Parameter.literal("A"),
                Parameter.named("id")
        );

        try {
            PreparedSqlValues.resolve(sql, null);
            fail("Expected missing runtime parameters");
        }
        catch( IllegalArgumentException expected ) {
            assertTrue(expected.getMessage().contains(":id"));
        }
    }

    @Test
    public void jdbcRejectsResolvedValueCountMismatch() {
        PreparedSql sql = prepared(
                Parameter.named("id"),
                Parameter.literal("A")
        );

        try {
            PreparedSqlValues.jdbc(
                    sql,
                    Collections.<Object>singletonList(1)
            );
            fail("Expected value count mismatch");
        }
        catch( IllegalArgumentException expected ) {
            assertTrue(expected.getMessage().contains("2 != 1"));
        }
    }

    @Test
    public void returnedListsAreUnmodifiable() {
        PreparedSql sql = prepared(Parameter.literal("A"));

        List<Object> raw = PreparedSqlValues.resolve(sql, null);
        List<Object> jdbc = PreparedSqlValues.jdbc(sql, raw);

        assertUnmodifiable(raw);
        assertUnmodifiable(jdbc);
    }

    private static void assertUnmodifiable(List<Object> values) {
        try {
            values.add("X");
            fail("Expected unmodifiable list");
        }
        catch( UnsupportedOperationException expected ) {
            // expected
        }
    }

    private static PreparedSql prepared(Parameter... parameters) {
        StringBuilder sql = new StringBuilder("select ");

        for( int i = 0; i < parameters.length; i++ ) {
            if( i > 0 )
                sql.append(", ");
            sql.append('?');
        }

        return new PreparedSql(sql.toString(), Arrays.asList(parameters));
    }

    private static final class Params implements IParameters {
        private final Map<String,Object> names = new HashMap<>();
        private final Map<Integer,Object> indexes = new HashMap<>();

        Params named(String name, Object value) {
            names.put(name, value);
            return this;
        }

        Params at(int index, Object value) {
            indexes.put(index, value);
            return this;
        }

        @Override
        public Object getParameter(String name) {
            return names.get(name);
        }

        @Override
        public Object getParameter(int index) {
            return indexes.get(index);
        }
    }
}
