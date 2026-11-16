package ru.inversion.tds;

import org.junit.Test;
import ru.inversion.dataset.ParametersByName;
import ru.inversion.db.dialect.SqlDialectFactory;
import ru.inversion.utils.sparser.jdbc.Parameter;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class SQLTreeDataSetRefreshCurrentItemPlanTest {

    @Test
    public void pkBindDoesNotOverrideSameNamedSourceParameter() {
        SQLTreeDataSet.RefreshCurrentItemPlan plan =
                SQLTreeDataSet.createRefreshCurrentItemPlan(
                        "select * from (select ID from T "
                                + "where TENANT_ID = :ID) qrslt\n"
                                + "where ID = :ID",
                        "ID",
                        SqlDialectFactory.POSTGRES
                );

        assertEquals(2, plan.preparedSql.getParameters().size());

        Parameter source = plan.preparedSql.getParameters().get(0);
        Parameter pk = plan.preparedSql.getParameters().get(1);

        assertEquals(Parameter.Kind.NAMED, source.getKind());
        assertEquals(Parameter.Kind.NAMED, pk.getKind());
        assertEquals("ID", source.getName());
        assertNotEquals("ID", pk.getName());
        assertEquals(plan.idParameterName, pk.getName());

        Map<String,Object> values = new HashMap<>();
        values.put("ID", 900);

        assertEquals(
                Arrays.<Object>asList(900, 42),
                SQLTreeDataSet.refreshCurrentItemJdbcValues(
                        plan,
                        42,
                        ParametersByName.of(values)
                )
        );
    }

    @Test
    public void generatedPkBindAvoidsInternalNameCollision() {
        SQLTreeDataSet.RefreshCurrentItemPlan plan =
                SQLTreeDataSet.createRefreshCurrentItemPlan(
                        "select * from (select ID from T "
                                + "where TENANT_ID = :jinv_tree_refresh_pk_0) qrslt\n"
                                + "where ID = :ID",
                        "ID",
                        SqlDialectFactory.POSTGRES
                );

        assertEquals(
                "jinv_tree_refresh_pk_0_1",
                plan.idParameterName
        );

        Map<String,Object> values = new HashMap<>();
        values.put("jinv_tree_refresh_pk_0", 700);

        assertEquals(
                Arrays.<Object>asList(700, 42),
                SQLTreeDataSet.refreshCurrentItemJdbcValues(
                        plan,
                        42,
                        ParametersByName.of(values)
                )
        );
    }

    @Test
    public void literalsAndCommentsDoNotParticipateInBindSelection() {
        String sql = "select * from (select ID from T "
                + "where NOTE = ':ID' /* :ID */ and TENANT_ID = :ID) qrslt\n"
                + "where ID = :ID";

        SQLTreeDataSet.RefreshCurrentItemPlan plan =
                SQLTreeDataSet.createRefreshCurrentItemPlan(
                        sql,
                        "ID",
                        SqlDialectFactory.ORACLE
                );

        assertTrue(plan.sql.contains("NOTE = ':ID'"));
        assertTrue(plan.sql.contains("/* :ID */"));
        assertEquals(2, plan.preparedSql.getParameters().size());

        List<Object> values =
                SQLTreeDataSet.refreshCurrentItemJdbcValues(
                        plan,
                        42,
                        ParametersByName.of(name -> 900)
                );

        assertEquals(Arrays.<Object>asList(900, 42), values);
    }

    @Test
    public void customRefreshSqlWithoutIdBindKeepsLegacyResolution() {
        SQLTreeDataSet.RefreshCurrentItemPlan plan =
                SQLTreeDataSet.createRefreshCurrentItemPlan(
                        "select ID from T where EXTERNAL_ID = :EXTERNAL_ID",
                        "ID",
                        SqlDialectFactory.POSTGRES
                );

        assertEquals("ID", plan.idParameterName);

        Map<String,Object> values = new HashMap<>();
        values.put("EXTERNAL_ID", 17);

        assertEquals(
                Arrays.<Object>asList(17),
                SQLTreeDataSet.refreshCurrentItemJdbcValues(
                        plan,
                        42,
                        ParametersByName.of(values)
                )
        );
    }
}
