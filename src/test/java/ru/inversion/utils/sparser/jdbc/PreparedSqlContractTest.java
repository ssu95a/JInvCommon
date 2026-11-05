package ru.inversion.utils.sparser.jdbc;

import org.junit.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class PreparedSqlContractTest {

    @Test
    public void parameterFactoriesExposeOnlyKindSpecificData() {
        Parameter positional = Parameter.positional();
        Parameter named = Parameter.named("id");
        Parameter numbered = Parameter.numbered(2);
        Parameter literal = Parameter.literal(new BigInteger("10"));

        assertEquals(Parameter.Kind.POSITIONAL, positional.getKind());

        assertEquals(Parameter.Kind.NAMED, named.getKind());
        assertEquals("id", named.getName());

        assertEquals(Parameter.Kind.NUMBERED, numbered.getKind());
        assertEquals(2, numbered.getNumber());

        assertEquals(Parameter.Kind.LITERAL, literal.getKind());
        assertEquals(new BigInteger("10"), literal.getValue());
    }

    @Test
    public void kindSpecificGetterRejectsWrongKind() {
        Parameter named = Parameter.named("id");

        try {
            named.getValue();
            fail("Expected wrong-kind access failure");
        }
        catch( IllegalStateException expected ) {
            assertTrue(expected.getMessage().contains("Expected LITERAL"));
        }
    }

    @Test
    public void preparedSqlCopiesAndProtectsParameterOrder() {
        List<Parameter> source = new ArrayList<>(Arrays.asList(
                Parameter.named("id"),
                Parameter.literal("A")
        ));

        PreparedSql prepared = new PreparedSql(
                "select ?, ?",
                source
        );

        source.clear();

        assertEquals(2, prepared.getParameters().size());
        assertEquals(
                Parameter.Kind.NAMED,
                prepared.getParameters().get(0).getKind()
        );
        assertEquals(
                Parameter.Kind.LITERAL,
                prepared.getParameters().get(1).getKind()
        );

        try {
            prepared.getParameters().add(Parameter.positional());
            fail("Expected immutable PreparedSql parameter list");
        }
        catch( UnsupportedOperationException expected ) {
            // expected
        }
    }

    @Test
    public void nullParameterListRemainsCompatibleWithEmptyPlan() {
        PreparedSql prepared = new PreparedSql(
                "select 1",
                null
        );

        assertSame(Collections.emptyList(), prepared.getParameters());
    }

    @Test
    @SuppressWarnings("deprecation")
    public void deprecatedSourceIdApiRemainsCompatible() {
        Parameter defaultParameter = Parameter.named("id");
        Parameter explicitSource = Parameter.named("id", 7);

        assertEquals(
                Parameter.GLOBAL_SOURCE,
                defaultParameter.getSourceId()
        );
        assertEquals(7, explicitSource.getSourceId());

        assertEquals(Parameter.Kind.NAMED, explicitSource.getKind());
        assertEquals("id", explicitSource.getName());
    }
}
