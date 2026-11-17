package ru.inversion.datacall;

import org.junit.jupiter.api.Test;
import ru.inversion.dataset.ParametersByName;
import ru.inversion.parser.statement.LangEnum;
import ru.inversion.utils.ParametersValues;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
import java.io.StringReader;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static javax.persistence.ParameterMode.IN;
import static javax.persistence.ParameterMode.INOUT;
import static javax.persistence.ParameterMode.OUT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonSqlCallValueConversionTest {

    @Test
    void scalarRequestPayloadKeepsLauncherContract() throws CallException {
        CallStatement statement = CallBuilder.NEW()
                .sql("begin use_values("
                        + ":Block.Item, :outValue, :ioValue, :nullValue, "
                        + ":BLOCK.ITEM); end;")
                .lang(LangEnum.PL_pgSQL)
                .putParameter(new ParameterMetadataBuilder()
                        .name("Block.Item")
                        .mode(IN)
                        .sqlType(Types.VARCHAR)
                        .build())
                .putParameter(new ParameterMetadataBuilder()
                        .name("outValue")
                        .mode(OUT)
                        .sqlType(Types.INTEGER)
                        .build())
                .putParameter(new ParameterMetadataBuilder()
                        .name("ioValue")
                        .mode(INOUT)
                        .sqlType(Types.INTEGER)
                        .build())
                .putParameter(new ParameterMetadataBuilder()
                        .name("nullValue")
                        .mode(IN)
                        .sqlType(Types.VARCHAR)
                        .build())
                .build();

        Map<String,Object> values = new HashMap<>();
        values.put("Block.Item", "alpha");
        values.put("ioValue", 7);
        values.put("nullValue", null);

        JsonSqlCall call = new JsonSqlCall(
                null,
                statement,
                ParametersByName.of(values),
                "contract-test"
        );
        Map<String,String> substitutions = new HashMap<>();

        final JsonObject root;
        try( JsonReader reader = Json.createReader(new StringReader(
                call.prepareJson(substitutions))) ) {
            root = reader.readObject();
        }

        JsonObject expression = root.getJsonObject("expression");
        assertEquals("contract-test", expression.getString("name"));
        assertEquals("PL_pgSQL", expression.getString("lang"));

        String script = expression.getString("script");
        assertEquals(2, occurrences(script, ":p_block_item"));
        assertFalse(script.contains(":Block.Item"));
        assertFalse(script.contains(":BLOCK.ITEM"));

        JsonArray parameterArray = expression.getJsonArray("parameters");
        assertEquals(4, parameterArray.size());
        Map<String,JsonObject> parameters = parametersByName(parameterArray);

        JsonObject input = parameters.get("p_block_item");
        assertEquals("IN", input.getString("mode"));
        assertEquals("varchar", input.getString("sqlType"));
        assertEquals("alpha", input.getString("value"));

        JsonObject output = parameters.get("p_outvalue");
        assertEquals("OUT", output.getString("mode"));
        assertEquals("INTEGER", output.getString("sqlType"));
        assertFalse(output.containsKey("value"));

        JsonObject inOut = parameters.get("p_iovalue");
        assertEquals("INOUT", inOut.getString("mode"));
        assertEquals("INTEGER", inOut.getString("sqlType"));
        assertEquals("7", inOut.getString("value"));

        JsonObject nullInput = parameters.get("p_nullvalue");
        assertEquals("IN", nullInput.getString("mode"));
        assertEquals("varchar", nullInput.getString("sqlType"));
        assertTrue(nullInput.isNull("value"));

        assertEquals("Block.Item", substitutions.get("p_block_item"));
        assertEquals("outValue", substitutions.get("p_outvalue"));
        assertEquals("ioValue", substitutions.get("p_iovalue"));
        assertEquals("nullValue", substitutions.get("p_nullvalue"));
    }

    @Test
    void distinctNamesThatNormalizeEquallyAreRejected() {
        Map<String,String> names = new HashMap<>();
        String first = JsonSqlCall.normalizeParameterName("A.B");
        String second = JsonSqlCall.normalizeParameterName("A_B");

        assertEquals("p_a_b", first);
        assertEquals(first, second);
        assertTrue(JsonSqlCall.registerNormalizedParameterName(
                names, first, "A.B"));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> JsonSqlCall.registerNormalizedParameterName(
                        names, second, "A_B")
        );

        assertTrue(error.getMessage().contains("A.B"));
        assertTrue(error.getMessage().contains("A_B"));
        assertTrue(error.getMessage().contains("p_a_b"));
    }

    @Test
    void repeatedLogicalNameIsAllowedCaseInsensitively() {
        Map<String,String> names = new HashMap<>();
        String normalized = JsonSqlCall.normalizeParameterName("Block.Item");

        assertTrue(JsonSqlCall.registerNormalizedParameterName(
                names, normalized, "Block.Item"));
        assertFalse(JsonSqlCall.registerNormalizedParameterName(
                names, normalized, "BLOCK.ITEM"));
        assertEquals("Block.Item", names.get(normalized));
    }

    @Test
    void scalarOutWithoutJavaTypeKeepsJsonValue() throws CallException {
        ParameterMetadata<?> parameter = new ParameterMetadataBuilder()
                .name("IFF_INSERT")
                .mode(OUT)
                .sqlType(Types.INTEGER)
                .build();

        Long value = 42L;

        Object converted = JsonSqlCall.convertScalarOutValue(value, parameter);

        assertSame(value, converted);
    }

    @Test
    void scalarOutWithJavaTypeStillUsesTypeConverter() throws CallException {
        ParameterMetadata<?> parameter = new ParameterMetadataBuilder()
                .name("IFF_INSERT")
                .mode(OUT)
                .sqlType(Types.INTEGER)
                .javaType(Integer.class)
                .build();

        Object converted = JsonSqlCall.convertScalarOutValue(42L, parameter);

        assertEquals(Integer.valueOf(42), converted);
    }

    @Test
    void structArrayOutWithoutJavaTypeIsStoredAsStructList() {
        ParametersValues parametersValues = new ParametersValues();
        List<Object> values = new ArrayList<>(Arrays.asList("first", "second"));

        JsonSqlCall.storeStructArrayOutValue(
                parametersValues,
                "OUT_ITEMS",
                values,
                null
        );

        assertSame(values, parametersValues.getParameter("OUT_ITEMS"));
    }

    private static Map<String,JsonObject> parametersByName(JsonArray values) {
        Map<String,JsonObject> result = new HashMap<>();
        for( int i = 0; i < values.size(); i++ ) {
            JsonObject value = values.getJsonObject(i);
            result.put(value.getString("name"), value);
        }
        return result;
    }

    private static int occurrences(String value, String part) {
        int count = 0;
        int offset = 0;
        while( (offset = value.indexOf(part, offset)) >= 0 ) {
            count++;
            offset += part.length();
        }
        return count;
    }
}
