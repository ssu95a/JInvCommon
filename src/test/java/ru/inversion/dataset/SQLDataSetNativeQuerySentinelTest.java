package ru.inversion.dataset;

import org.junit.Test;

import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.NamedNativeQueries;
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

    @Test
    public void unnamedDirectQueryWinsOverTable() {
        SQLDataSet<UnnamedRow> ds = new SQLDataSet<>(UnnamedRow.class);
        assertEquals("select ID from UNNAMED_SOURCE", ds.getSQL());
    }

    @Test
    public void singleNamedQueryInContainerDoesNotReplaceTableDefault() {
        SQLDataSet<ContainerRow> ds = new SQLDataSet<>(ContainerRow.class);
        assertNull(ds.getSQL());
        ds.setNativeQueryName("byEntryId");
        assertEquals(
                "select * from V_SM_003_MF where idsm_entry = ?",
                ds.getSQL()
        );
    }

    @Table(name = "UNNAMED_TABLE")
    @NamedNativeQuery(name = "", query = "select ID from UNNAMED_SOURCE")
    public static final class UnnamedRow {
    }

    @Table(name = "V_SM_003_MF")
    @NamedNativeQueries({
            @NamedNativeQuery(
                    name = "byEntryId",
                    query = "select * from V_SM_003_MF where idsm_entry = ?")
    })
    public static final class ContainerRow {
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
