package ru.inversion.dataset;

import org.junit.Test;

import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.NamedNativeQuery;
import javax.persistence.Table;

import static org.junit.Assert.*;

public class SQLDataSetNativeQuerySentinelTest {

    @Test
    public void unnamedSentinelUsesValueEquality() {
        SQLDataSet<Row> ds = new SQLDataSet<>(Row.class);

        ds.setNativeQueryName(
                new String(ISQLDataSet.QUERY_UNNAMED)
        );

        assertEquals(
                "select ID from NATIVE_SOURCE",
                ds.getSQL()
        );
    }

    @Test
    public void tableSentinelUsesValueEqualityAndSkipsNativeQuery() {
        SQLDataSet<Row> ds = new SQLDataSet<>(Row.class);

        ds.setNativeQueryName(
                new String(ISQLDataSet.QUERY_TABLE)
        );

        assertNull(ds.getSQL());
    }

    @Table(name = "TABLE_SOURCE")
    @NamedNativeQuery(
            name = "row.native",
            query = "select ID from NATIVE_SOURCE"
    )
    public static final class Row {

        private Long id;

        @Id
        @Column(name = "ID")
        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }
    }
}
