package ru.inversion.datacall;

import org.junit.jupiter.api.Test;
import ru.inversion.utils.ParametersValues;

import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static javax.persistence.ParameterMode.OUT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class JsonSqlCallValueConversionTest {

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
