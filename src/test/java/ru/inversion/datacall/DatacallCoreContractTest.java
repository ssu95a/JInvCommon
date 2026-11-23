package ru.inversion.datacall;

import org.junit.jupiter.api.Test;
import ru.inversion.parser.statement.LangEnum;
import ru.inversion.parser.statement.PrepareModeEnum;

import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
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
    void mixedBindParametersKeepZeroBasedPositionsAndReuseNamedMetadata() {
        List<String> inferredNames = new ArrayList<>();

        CallStatement statement = CallBuilder.NEW()
                .sql("begin apply_values(:result, ?, :RESULT, :other, ?); end;")
                .parameter(IN, 1, Types.BIGINT, null, null, null, true)
                .parameterModeStrategy(name -> {
                    inferredNames.add(name);
                    return "result".equalsIgnoreCase(name) ? OUT : IN;
                })
                .build();

        assertEquals(Arrays.asList("result", "other"), inferredNames);
        assertEquals(OUT, statement.getParameter("RESULT").getMode());
        assertEquals(Integer.valueOf(Types.VARCHAR), statement.getParameter("result").getSQLType());
        assertEquals(IN, statement.getParameter("other").getMode());
        assertEquals(IN, statement.getParameter(0).getMode());
        assertNull(statement.getParameter(0).getSQLType());
        assertEquals(Integer.valueOf(Types.BIGINT), statement.getParameter(1).getSQLType());
        assertNull(statement.getParameter(2));
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
    void jdbcPreparationKeepsBindAndLiteralPositionsInSqlOrder() {
        CallStatement statement = CallBuilder.NEW()
                .sql("update t set name=:name, kind='active' where id=? and old_name=:name and score=?")
                .replaceConstants(true)
                .build();
        List<List<?>> parameters = new ArrayList<>();

        String sql = statement.prepareJdbcSql(
                (position, id) -> parameters.add(Arrays.asList(position, "bind", id)),
                (position, value) -> parameters.add(Arrays.asList(position, "literal", value))
        );

        assertEquals(Arrays.asList(
                Arrays.asList(1, "bind", "name"),
                Arrays.asList(2, "literal", "active"),
                Arrays.asList(3, "bind", 0),
                Arrays.asList(4, "bind", "name"),
                Arrays.asList(5, "bind", 1)
        ), parameters);
        assertEquals(5L, sql.chars().filter(ch -> ch == '?').count());
        assertFalse(sql.contains("'active'"));
    }

    @Test
    void queryWithValuesDoesNotDeleteBindTokens() {
        CallStatement statement = CallBuilder.NEW()
                .sql("select * from t where id=:id or parent_id=:ID and kind=?")
                .build();
        List<Object> values = new ArrayList<>();

        String sql = statement.prepareSql(PrepareModeEnum.QUERY_WITH_VALUES, values);

        assertTrue(sql.contains(":id"), sql);
        assertTrue(sql.contains(":ID"), sql);
        assertTrue(sql.contains("?"), sql);
        assertTrue(values.isEmpty());
    }

    @Test
    void commentsOnlyAreRejectedAsNonExecutableSql() {
        assertThrows(CallException.class, () -> CallBuilder.NEW()
                .sql("-- no executable statement")
                .build());
    }

    @Test
    void qualifiedCyrillicBindKeepsItsFullNameAndJdbcPosition() {
        CallStatement statement = CallBuilder.NEW()
                .sql("{? = call p(:БЛОК.Поле_2, ?, :amount_1)}")
                .build();
        List<Object> ids = new ArrayList<>();

        assertEquals(LangEnum.JDBC, statement.getLang());
        assertTrue(statement.prepareJdbcSql((position, id) -> ids.add(id), (position, value) -> {}).contains("{"));
        assertEquals(Arrays.asList(0, "БЛОК.Поле_2", 1, "amount_1"), ids);
        assertEquals("БЛОК.Поле_2", statement.getParameter("блок.поле_2").getName());
    }

    @Test
    void oracleCallAcceptsWhitespaceAfterBindColon() {
        String block = "BEGIN UPDATE t SET c = : simvol_2, note = ': text'; -- : comment\n"
                + ":\tresult := :SIMVOL_2; END;";
        CallDescriptor descriptor = CallDescriptor.create(block, LangEnum.PL_SQL);
        assertTrue(descriptor.hasNamedParameter("simvol_2"));
        assertTrue(descriptor.hasNamedParameter("result"));
        assertFalse(descriptor.hasNamedParameter("text"));
        assertFalse(descriptor.hasNamedParameter("comment"));

        CallStatement statement = CallBuilder.NEW()
                .sql(block)
                .lang(LangEnum.PL_SQL)
                .parameter(OUT, "result", Types.VARCHAR, null, String.class, null, true)
                .removeAdditionalInfo(true)
                .build();
        List<Object> ids = new ArrayList<>();
        String jdbc = statement.prepareJdbcSql((position, id) -> ids.add(id), (position, value) -> {});

        assertEquals(Arrays.asList("simvol_2", "result", "SIMVOL_2"), ids);
        assertEquals("simvol_2", statement.getParameter("simvol_2").getName());
        assertTrue(statement.getParameter("result").isOut());
        assertTrue(jdbc.contains("c = ?, note = ': text'"), jdbc);
        assertTrue(jdbc.contains("? := ?"), jdbc);
    }

    @Test
    void orderByOrdinalIsKeptWhenLiteralsAreReplaced() {
        CallStatement statement = CallBuilder.NEW()
                .sql("select 7 from t ORDER BY 1")
                .replaceConstants(true)
                .build();
        List<Object> values = new ArrayList<>();

        String sql = statement.prepareSql(PrepareModeEnum.QUERY, values);

        assertEquals(Arrays.asList(7), values);
        assertTrue(sql.contains("ORDER BY 1"), sql);
    }

    @Test
    void jdbcPreparationPreservesAdjacentOperatorsAndSkipsOrderByOrdinal() {
        CallStatement statement = CallBuilder.NEW()
                .sql("select :value - -2 from t ORDER /* ordinal */ BY 1")
                .replaceConstants(true)
                .build();
        List<List<?>> parameters = new ArrayList<>();

        String sql = statement.prepareJdbcSql(
                (position, id) -> parameters.add(Arrays.asList(position, id)),
                (position, value) -> parameters.add(Arrays.asList(position, value))
        );

        assertTrue(sql.startsWith("select ? - -?"), sql);
        assertTrue(sql.matches(".*ORDER\\s+BY 1$"), sql);
        assertEquals(Arrays.asList(Arrays.asList(1, "value"), Arrays.asList(2, 2)), parameters);
    }

    @Test
    void legacyMarkersStillToggleLiteralReplacement() {
        CallStatement statement = CallBuilder.NEW()
                .sql("select 1, ~2~, 3 from t")
                .replaceConstants(true)
                .build();
        List<Object> values = new ArrayList<>();

        String sql = statement.prepareSql(PrepareModeEnum.QUERY, values);

        assertFalse(sql.contains("~"), sql);
        assertTrue(sql.contains("2"), sql);
        assertEquals(Arrays.asList(1, 3), values);
    }

    @Test
    void anonymousBlockIsDetectedByItsOpeningTokens() {
        CallDescriptor query = CallDescriptor.create("select t.begin from t", LangEnum.PL_pgSQL);
        assertEquals(LangEnum.PL_pgSQL, query.getLang());
        assertFalse(query.isAnonymousBlock());

        CallDescriptor jdbc = CallDescriptor.create("-- comment\n{call p(?)}");
        assertEquals(LangEnum.JDBC, jdbc.getLang());
        assertFalse(jdbc.isAnonymousBlock());

        CallDescriptor block = CallDescriptor.create("-- comment\n<<block_label>>\nDeClArE\nBEGIN NULL; END;");
        assertEquals(LangEnum.PL_SQL, block.getLang());
        assertTrue(block.isAnonymousBlock());
    }

    @Test
    void preparationCanKeepSourceCommentsAndBindAnnotations() {
        String source = "select /*+ INDEX(t) */ /* source comment */ :value from t";
        CallStatement statement = CallBuilder.NEW().sql(source).removeAdditionalInfo(false).build();
        String withComments = statement.prepareSql(PrepareModeEnum.QUERY, new ArrayList<>());

        assertTrue(withComments.contains("/* source comment */"), withComments);
        assertTrue(withComments.contains("/* 1, :value */"), withComments);

        CallStatement stripped = CallBuilder.NEW().sql(source).build();
        String withoutComments = stripped.prepareSql(PrepareModeEnum.QUERY, new ArrayList<>());
        assertFalse(withoutComments.contains("/* source comment */"), withoutComments);
        assertFalse(withoutComments.contains("/* 1, :value */"), withoutComments);
        assertTrue(withoutComments.contains("/*+ INDEX(t) */"), withoutComments);
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
