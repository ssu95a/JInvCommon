package ru.inversion.dataset.aggr;

import org.junit.Test;

import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.Map;

import static org.junit.Assert.*;

public class AggrRSMapperTest {

    @Test
    public void usesColumnLabelForAggregateAlias() throws Exception {
        ResultSetMetaData meta = metadata("TOTAL", "SUM");

        ResultSet rs = (ResultSet) Proxy.newProxyInstance(
                ResultSet.class.getClassLoader(),
                new Class<?>[]{ResultSet.class},
                (proxy, method, args) -> {
                    if( "getMetaData".equals(method.getName()) )
                        return meta;
                    if( "getObject".equals(method.getName()) )
                        return 17;
                    throw new UnsupportedOperationException(
                            method.getName());
                }
        );

        Map<String,Object> row =
                AggrRSMapper.instance.mapRow(rs, 0);

        assertEquals(17, row.get("TOTAL"));
        assertNull(row.get("SUM"));
    }

    @Test
    public void fallsBackToColumnNameWhenLabelIsEmpty()
            throws Exception {
        ResultSetMetaData meta = metadata("", "TOTAL");

        ResultSet rs = (ResultSet) Proxy.newProxyInstance(
                ResultSet.class.getClassLoader(),
                new Class<?>[]{ResultSet.class},
                (proxy, method, args) -> {
                    if( "getMetaData".equals(method.getName()) )
                        return meta;
                    if( "getObject".equals(method.getName()) )
                        return 23;
                    throw new UnsupportedOperationException(
                            method.getName());
                }
        );

        assertEquals(
                23,
                AggrRSMapper.instance.mapRow(rs, 0)
                        .get("TOTAL")
        );
    }

    private static ResultSetMetaData metadata(
            String label,
            String name
    ) {
        return (ResultSetMetaData) Proxy.newProxyInstance(
                ResultSetMetaData.class.getClassLoader(),
                new Class<?>[]{ResultSetMetaData.class},
                (proxy, method, args) -> {
                    switch( method.getName() ) {
                        case "getColumnCount":
                            return 1;
                        case "getColumnLabel":
                            return label;
                        case "getColumnName":
                            return name;
                        default:
                            throw new UnsupportedOperationException(
                                    method.getName());
                    }
                }
        );
    }
}
