package ru.inversion.dataset.parser;

import org.junit.Test;
import ru.inversion.dataset.IParameters;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@SuppressWarnings({"deprecation", "rawtypes"})
public class SQLParserCompatibilityTest {

    @Test
    public void legacyPreparationMethodsRemainAvailable() {
        SQLParser parser = new SQLParser(
                "select :id, 'ACTIVE' from dual"
        );
        List<Function> values = new ArrayList<>();

        String sql = parser.prepareActualSqlEx(
                new IParameters() {
                    @Override
                    public Object getParameter(int parameterIndex) {
                        return null;
                    }

                    @Override
                    public Object getParameter(String parameterName) {
                        return "id".equals(parameterName) ? 17 : null;
                    }
                },
                values
        );

        assertEquals("select ?, ? from dual", compact(sql));
        assertEquals(2, values.size());
        assertEquals(17, values.get(0).apply(null));
        assertEquals("ACTIVE", values.get(1).apply(null));
        assertEquals(
                "select null, null from dual",
                compact(parser.prepareSql4MetaData())
        );
    }

    @Test
    public void legacyLanguageAndValueFormatterRemainAvailable() {
        SQLParser parser = new SQLParser(
                "begin :value := 1; end;",
                SQLParser.Lang.PL_SQL
        );

        assertTrue(parser.iterator().hasNext());
        assertEquals(" 'O''Brien' ", SQLParser.value2Str("O'Brien"));
    }

    private static String compact(String sql) {
        return sql.trim().replaceAll("\\s+", " ")
                .replace(" ,", ",");
    }
}
