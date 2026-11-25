package ru.inversion.dataset.aggr;

import org.junit.Test;
import ru.inversion.utils.Triplet;

import javax.sql.rowset.CachedRowSet;
import javax.sql.rowset.RowSetMetaDataImpl;
import javax.sql.rowset.RowSetProvider;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Arrays;
import java.util.Map;

import static org.junit.Assert.*;

public class AggrRSMapperTest {

    private static final AggrRSMapper AMOUNT_AND_COUNT = AggrRSMapper.forAggregates(Arrays.asList(
            Triplet.makeTriplet("amount", AggrFuncEnum.SUM, "AMOUNT"),
            Triplet.makeTriplet("1", AggrFuncEnum.COUNT, "\"A$1\"")));

    @Test
    public void countUsesLongAndSumKeepsDecimalPrecision() throws Exception {
        BigDecimal amount = new BigDecimal("12345678901234567890.125");
        try (CachedRowSet rs = aggregateRow(amount, new BigDecimal("2147483648"))) {
            Map<String, Object> row = AMOUNT_AND_COUNT.mapRow(rs, 0);
            assertEquals(Long.valueOf(2147483648L), row.get("a$1"));
            assertEquals(amount, row.get("AMOUNT"));
        }
    }

    @Test
    public void countBeyondLongRangeIsRejected() throws Exception {
        try (CachedRowSet rs = aggregateRow(BigDecimal.ZERO, new BigDecimal("9223372036854775808"))) {
            SQLException error = assertThrows(SQLException.class, () -> AMOUNT_AND_COUNT.mapRow(rs, 0));
            assertTrue(error.getMessage(), error.getMessage().contains("A$1"));
        }
    }

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

    private static CachedRowSet aggregateRow(BigDecimal amount, BigDecimal count) throws SQLException {
        RowSetMetaDataImpl meta = new RowSetMetaDataImpl();
        meta.setColumnCount(2);
        meta.setColumnType(1, Types.NUMERIC);
        meta.setColumnLabel(1, "AMOUNT");
        meta.setColumnType(2, Types.NUMERIC);
        meta.setColumnLabel(2, "A$1");

        CachedRowSet rs = RowSetProvider.newFactory().createCachedRowSet();
        rs.setMetaData(meta);
        rs.moveToInsertRow();
        rs.updateBigDecimal(1, amount);
        rs.updateBigDecimal(2, count);
        rs.insertRow();
        rs.moveToCurrentRow();
        rs.beforeFirst();
        assertTrue(rs.next());
        return rs;
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
