package ru.inversion.dataset.aggr;

import ru.inversion.dataset.IRowMapper;
import ru.inversion.utils.S;
import ru.inversion.utils.Triplet;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 *
 * @author ssu
 */
public class AggrRSMapper implements IRowMapper<Map<String,Object>> {

    final static public AggrRSMapper instance = new AggrRSMapper();

    private final BitSet countColumns;

    public AggrRSMapper() {
        this(new BitSet(0));
    }

    private AggrRSMapper(BitSet countColumns) {
        this.countColumns = countColumns;
    }

    /** Возвращает COUNT как Long независимо от числового типа JDBC. */
    public static AggrRSMapper forAggregates(List<Triplet<String, AggrFuncEnum, String>> aggregates) {
        BitSet countColumns = new BitSet(aggregates.size());
        for (int i = 0; i < aggregates.size(); i++) {
            if (aggregates.get(i).getSecond() == AggrFuncEnum.COUNT)
                countColumns.set(i);
        }
        return countColumns.isEmpty() ? instance : new AggrRSMapper(countColumns);
    }

    /** */
    @Override
    public Map<String, Object> mapRow( ResultSet rs, int rowNum ) throws SQLException {

        final Map<String, Object> retMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        final ResultSetMetaData metaData = rs.getMetaData();

        for( int i = metaData.getColumnCount(); i > 0; i-- ) {

            String column = metaData.getColumnLabel(i);

            if( S.isNullOrEmpty(column) )
                column = metaData.getColumnName(i);

            Object value = countColumns.get(i - 1) ? readCount(rs, i, column) : rs.getObject(i);

            retMap.put( column, value );
        }

        return retMap;
    }

    private static Long readCount(ResultSet rs, int index, String column) throws SQLException {
        BigDecimal value = rs.getBigDecimal(index);
        if (value == null)
            return null;
        try {
            return value.longValueExact();
        } catch (ArithmeticException ex) {
            throw new SQLException("Значение COUNT для столбца " + column + " нельзя представить как Long", ex);
        }
    }
}
