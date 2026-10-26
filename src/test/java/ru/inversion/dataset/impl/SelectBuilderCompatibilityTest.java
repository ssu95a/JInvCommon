package ru.inversion.dataset.impl;

import org.junit.Test;
import ru.inversion.dataset.aggr.AggrFuncEnum;
import ru.inversion.db.dialect.SqlDialectFactory;

import javax.persistence.Column;
import javax.persistence.Table;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SelectBuilderCompatibilityTest {

    @Table(name = "client")
    public static class Row {
        @Column(name = "id")
        public Long getId() { return null; }

        @Column(name = "name")
        public String getName() { return null; }
    }

    @Test
    public void entityBuilderIsASelectBuilderAgain() {
        SelectBuilder builder =
                new EntitySelectBuilder(Row.class, SqlDialectFactory.POSTGRES);

        builder.queryAlias("q")
               .addPredicat("q.id = :id")
               .orderBy("q.name")
               .mark("0");

        String sql = builder.build();

        assertTrue(sql, sql.contains("SELECT q.id,q.name"));
        assertTrue(sql, sql.contains("FROM client q"));
        assertTrue(sql, sql.contains("q.id = :id"));
        assertTrue(sql, sql.contains("ORDER BY q.name"));
        assertTrue(sql, sql.contains("AS MARK"));
    }

    @Test
    public void sqlSelectBuilderIsASelectBuilderAgain() {
        SelectBuilder builder =
                new SQLSelectBuilder("select id, name from client")
                        .dialect(SqlDialectFactory.POSTGRES);

        builder.queryAlias("q")
               .addPredicat("q.id = :id")
               .orderBy("q.name")
               .mark("0");

        String sql = builder.build();

        assertTrue(sql, sql.contains("SELECT q.* FROM ("));
        assertTrue(sql, sql.contains("q.id = :id"));
        assertTrue(sql, sql.contains("ORDER BY q.name"));
        assertTrue(sql, sql.contains("AS MARK"));
    }

    @Test
    public void oldSqlSelectConstructorWorksForCommonSqlWithoutExplicitDialect() {
        String sql = new SQLSelectBuilder("select id from client")
                .addPredicat("id > 0")
                .build();

        assertTrue(sql, sql.contains("qrslt.*"));
        assertTrue(sql, sql.contains("id > 0"));
    }

    @Test
    public void aggregateSurfaceRemainsAvailable() {
        String sql = new SQLSelectBuilder("select id from client")
                .addAggregator("*", AggrFuncEnum.COUNT, "cnt")
                .build();

        assertTrue(sql, sql.contains("COUNT (*) cnt"));
        assertTrue(sql, sql.contains("( select id from client) qrslt"));
    }

    @Test
    public void entityGetFromObjUsesAlias() {
        SelectBuilder builder =
                new EntitySelectBuilder(Row.class, SqlDialectFactory.POSTGRES);
        builder.queryAlias("q");

        assertEquals("q", builder.getFromObj());
    }
}
