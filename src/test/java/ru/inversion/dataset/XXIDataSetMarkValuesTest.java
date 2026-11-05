package ru.inversion.dataset;

import org.junit.Test;
import ru.inversion.utils.sparser.jdbc.Parameter;
import ru.inversion.utils.sparser.jdbc.PreparedSql;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class XXIDataSetMarkValuesTest {

    @Test
    public void markInsertAddsMarkerIdBeforeAndAfterSourceValues() {
        PreparedSql sql = new PreparedSql(
                "insert into mark select ?, ?, ?",
                Arrays.asList(
                        Parameter.named("jinv_marker_id"),
                        Parameter.named("source"),
                        Parameter.named("jinv_marker_id")
                )
        );

        List<Object> values = XXIDataSet.markInsertJdbcValues(
                sql,
                42L,
                Collections.<Object>singletonList(true)
        );

        assertEquals(
                Arrays.<Object>asList(42, 1, 42),
                values
        );
    }

    @Test
    public void markClearAddsMarkerIdBeforeSourceValues() {
        PreparedSql sql = new PreparedSql(
                "delete from mark where (?, ?)",
                Arrays.asList(
                        Parameter.named("jinv_marker_id"),
                        Parameter.named("source")
                )
        );

        List<Object> values = XXIDataSet.markClearJdbcValues(
                sql,
                42L,
                Collections.<Object>singletonList(true)
        );

        assertEquals(
                Arrays.<Object>asList(42, 1),
                values
        );
    }

    @Test(expected = IllegalArgumentException.class)
    public void markInsertDetectsPlanValueMismatch() {
        PreparedSql sql = new PreparedSql(
                "insert into mark select ?, ?",
                Arrays.asList(
                        Parameter.named("jinv_marker_id"),
                        Parameter.named("source")
                )
        );

        XXIDataSet.markInsertJdbcValues(
                sql,
                42L,
                Collections.<Object>singletonList(true)
        );
    }
}
