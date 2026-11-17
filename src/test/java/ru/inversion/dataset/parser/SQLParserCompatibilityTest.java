package ru.inversion.dataset.parser;

import org.junit.Test;
import ru.inversion.dataset.IParameters;
import ru.inversion.dataset.parser.old.SQLItem;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@SuppressWarnings({"deprecation", "rawtypes"})
public class SQLParserCompatibilityTest {

    @Test
    public void facadeDoesNotUseLegacyParserAsImplementation() {
        assertEquals(Object.class, SQLParser.class.getSuperclass());
    }

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

    @Test
    public void plSqlIteratorPreservesQualifiedParameterName() {
        SQLParser parser = new SQLParser(
                "begin :EDIT.cTrnPurp := :value; end;",
                SQLParser.Lang.PL_SQL
        );
        Iterator<SQLItem> items = parser.iterator();
        List<String> names = new ArrayList<>();

        while (items.hasNext()) {
            SQLItem item = items.next();
            if (item.getType() == SQLItem.ItemTypeEnum.NAMED_PARAMETER)
                names.add((String) item.getValue());
        }

        assertEquals(2, names.size());
        assertEquals("EDIT.cTrnPurp", names.get(0));
        assertEquals("value", names.get(1));
    }

    @Test
    public void literalsCommentsAndOrderByUseNewParameterizerRules() {
        SQLParser parser = new SQLParser(
                "select ':hidden', :id from dual "
                + "where code='OPEN' /* :comment 'X' */ order by 1"
        );
        List<Object> values = new ArrayList<>();

        String sql = parser.prepareActualSql(
                new IParameters() {
                    @Override
                    public Object getParameter(int parameterIndex) {
                        return null;
                    }

                    @Override
                    public Object getParameter(String parameterName) {
                        return "id".equals(parameterName) ? 42 : null;
                    }
                },
                values
        );

        assertEquals(
                "select ?, ? from dual where code=? /* :comment 'X' */ order by 1",
                compact(sql)
        );
        assertEquals(3, values.size());
        assertEquals(":hidden", values.get(0));
        assertEquals(42, values.get(1));
        assertEquals("OPEN", values.get(2));
    }

    private static String compact(String sql) {
        return sql.trim().replaceAll("\\s+", " ")
                .replace(" ,", ",");
    }
}
