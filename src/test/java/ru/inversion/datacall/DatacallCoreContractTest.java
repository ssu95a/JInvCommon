package ru.inversion.datacall;

import org.junit.jupiter.api.Test;
import ru.inversion.parser.statement.PrepareModeEnum;

import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import static javax.persistence.ParameterMode.IN;
import static javax.persistence.ParameterMode.OUT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatacallCoreContractTest {

    @Test
    void statementWithoutParametersHasNoOutParameters() {
        CallStatement statement = CallBuilder.NEW()
                .sql("select 1")
                .build();

        assertFalse(statement.hasOutParameters());
    }

    @Test
    void parameterMetadataBuilderDoesNotKeepNumericSqlTypeBetweenBuilds() {
        ParameterMetadataBuilder builder = new ParameterMetadataBuilder();

        builder.name("first").mode(IN).sqlType(Types.INTEGER).build();
        ParameterMetadata<?> second = builder.name("second").mode(IN).build();

        assertNull(second.getSQLType());
    }

    @Test
    void callBuilderAcceptsUntypedInputParameter() {
        CallStatement statement = CallBuilder.NEW()
                .sql("begin use_value(:value); end;")
                .parameter(IN, "value", null, null, null, null, true)
                .build();

        assertNull(statement.getParameter("value").getSQLType());
    }

    @Test
    void builtStatementIsIsolatedFromFurtherBuilderChanges() {
        CallBuilder builder = CallBuilder.NEW().sql("begin use_value(:value); end;");
        CallStatement first = builder.build();

        builder.putParameter(new ParameterMetadataBuilder()
                .name("value")
                .mode(OUT)
                .sqlType(Types.INTEGER)
                .build());

        assertEquals(IN, first.getParameter("VALUE").getMode());
    }

    @Test
    void customJsonSqlTypesCanBeDeclaredNumerically() {
        ParameterMetadata<?> json = new ParameterMetadataBuilder()
                .name("payload")
                .mode(IN)
                .sqlType(-1024)
                .build();

        assertEquals(Integer.valueOf(-1024), json.getSQLType());
    }

    @Test
    void queryExUsesParameterHandlerInsteadOfRemovedInternalList() {
        CallStatement statement = CallBuilder.NEW()
                .sql("select * from t where id=:id and kind='A'")
                .replaceConstants(true)
                .build();
        List<Object> values = new ArrayList<>();

        String sql = statement.prepareSql(PrepareModeEnum.QUERY_EX, values);

        assertTrue(sql.contains("?"));
        assertEquals(2, values.size());
    }

    @Test
    void queryWithValuesDoesNotDeleteBindTokens() {
        CallStatement statement = CallBuilder.NEW()
                .sql("select * from t where id=:id")
                .build();

        String sql = statement.prepareSql(
                PrepareModeEnum.QUERY_WITH_VALUES,
                new ArrayList<Object>()
        );

        assertTrue(sql.contains(":id"));
    }

    @Test
    void commentsOnlyAreRejectedAsNonExecutableSql() {
        assertThrows(CallException.class, () -> CallBuilder.NEW()
                .sql("-- no executable statement")
                .build());
    }

    @Test
    void unsupportedDataCallLinkCombinationFailsDuringBuild() {
        assertThrows(UnsupportedOperationException.class, () -> DataCallLinkBuilder.NEW()
                .source(new Object())
                .destination(new Object())
                .join(value -> value, "id")
                .build());
    }
}
