package ru.inversion.datacall;

import org.junit.jupiter.api.Test;
import ru.inversion.utils.ParametersValues;

import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static javax.persistence.ParameterMode.OUT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonSqlCallValueConversionTest {

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
}
