package ru.inversion.dataset.impl;

import org.junit.Test;
import ru.inversion.db.dialect.SqlDialectFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class XXIDsDaoFilterTest {

    @Test
    public void substitutesSavedFilterNamesWithoutTouchingLiteralsAndComments() {
        Map<String,Object> parameters = new HashMap<>();
        parameters.put("p1", 17);
        parameters.put("p2", "O'Brien");

        String filter =
                "client_id = :p1 and name = :p2 and status = 'A-1'"
                + " and note = ':p1' /* :p2 */";

        assertEquals(
                "client_id = 17 and name =  'O''Brien'  and status = 'A-1'"
                + " and note = ':p1' /* :p2 */",
                XXIDsDao.renderStoredFilter(
                        filter, parameters, SqlDialectFactory.POSTGRES)
        );
    }

    @Test
    public void missingAndExplicitNullParametersRenderAsNull() {
        Map<String,Object> parameters = new HashMap<>();
        parameters.put("p1", null);
        parameters.put("unused", "anything");

        assertEquals(
                "a =  NULL  and b =  NULL ",
                XXIDsDao.renderStoredFilter(
                        "a = :p1 and b = :p2",
                        parameters,
                        SqlDialectFactory.POSTGRES)
        );
    }

    @Test
    public void preservesPostgresRegexOperatorAndLegacyTildeMarkersForSqlPipeline() {
        Map<String,Object> parameters = Collections.<String,Object>singletonMap("p1", "AX.*");

        assertEquals(
                "code ~  'AX.*'  and ~AND (flag = 1)~",
                XXIDsDao.renderStoredFilter(
                        "code ~ :p1 and ~AND (flag = 1)~",
                        parameters,
                        SqlDialectFactory.POSTGRES)
        );
    }

    @Test
    public void noParametersKeepsFilterSqlUnchanged() {
        String filter = "~WHERE flag = :p1~";

        assertEquals(filter, XXIDsDao.renderStoredFilter(
                filter, Collections.<String,Object>emptyMap(),
                SqlDialectFactory.POSTGRES));
    }

    @Test
    public void oracleQuotedStringIsNotTreatedAsParameter() {
        assertEquals(
                "message = q'[inside :p1]' and id = 8",
                XXIDsDao.renderStoredFilter(
                        "message = q'[inside :p1]' and id = :p1",
                        Collections.<String,Object>singletonMap("p1", 8),
                        SqlDialectFactory.ORACLE));
    }
}
