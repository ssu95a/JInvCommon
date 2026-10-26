package ru.inversion.dataset.impl;

import org.junit.Test;
import ru.inversion.dataset.aggr.AggrFuncEnum;
import ru.inversion.db.dialect.SqlDialectFactory;

import javax.persistence.Column;
import javax.persistence.Table;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class EntitySelectBuilderTest {

    @Table(name = "client")
    public static class Row {
        @Column(name = "id")
        public Long getId() { return null; }

        @Column(name = "name")
        public String getName() { return null; }
    }

    @Test
    public void buildsEntitySelectThroughNewPipeline() {
        String sql = new EntitySelectBuilder(Row.class, SqlDialectFactory.POSTGRES)
                .queryAlias("q")
                .addPredicat("q.id = :id")
                .orderBy("q.name")
                .mark("0")
                .build();

        assertTrue(sql, sql.contains("SELECT q.id,q.name"));
        assertTrue(sql, sql.contains("FROM client q"));
        assertTrue(sql, sql.contains("q.id = :id"));
        assertTrue(sql, sql.contains("ORDER BY q.name"));
        assertTrue(sql, sql.contains("AS MARK"));
    }

    @Test
    public void getFromObjUsesAliasWhenPresent() {
        EntitySelectBuilder builder =
                new EntitySelectBuilder(Row.class, SqlDialectFactory.POSTGRES)
                        .queryAlias("q");

        assertEquals("q", builder.getFromObj());
    }

    @Test
    public void aggregateCompatibilityRemainsLocal() {
        String sql = new EntitySelectBuilder(Row.class, SqlDialectFactory.POSTGRES)
                .queryAlias("q")
                .addPredicat("q.id > 0")
                .addAggregator("*", AggrFuncEnum.COUNT, "cnt")
                .build();

        assertTrue(sql, sql.contains("COUNT (*) cnt"));
        assertTrue(sql, sql.contains("FROM client q"));
        assertTrue(sql, sql.contains("q.id > 0"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsUnsafeAlias() {
        new EntitySelectBuilder(Row.class, SqlDialectFactory.POSTGRES)
                .queryAlias("q where 1=1")
                .build();
    }
}
