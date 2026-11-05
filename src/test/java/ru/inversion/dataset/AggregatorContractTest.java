package ru.inversion.dataset;

import org.junit.Test;
import ru.inversion.dataset.aggr.AggrFuncEnum;
import ru.inversion.dataset.aggr.IAggregator;
import ru.inversion.dataset.aggr.IAggregatorBuilder;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.Assert.*;

public class AggregatorContractTest {

    @Test(expected = IllegalStateException.class)
    public void ordinaryAggregatorRejectsEmptyDefinition()
            throws Exception {
        new SQLDataSet<Object>()
                .createAggregatorBuilder(false)
                .build();
    }

    @Test
    public void builderMutationDoesNotChangeBuiltAggregator()
            throws Exception {
        SQLDataSet<Object> ds = new SQLDataSet<>();
        IAggregatorBuilder<Object> builder =
                ds.createAggregatorBuilder(false);

        builder.add("id", AggrFuncEnum.COUNT);
        IAggregator<Object> aggregator = builder.build();

        builder.add("amount", AggrFuncEnum.SUM);

        Field field = aggregator.getClass()
                .getDeclaredField("aggrList");
        field.setAccessible(true);

        List<?> aggregates = (List<?>)field.get(aggregator);

        assertEquals(1, aggregates.size());

        aggregator.close();
    }

    @Test
    public void markedCountUsesGeneralAggregatorPath()
            throws Exception {
        XXIDataSet<Object> ds = new XXIDataSet<>();

        IAggregator<Object> aggregator =
                ds.createMarkedAggregatorBuilder(false)
                        .add("nullable_value", AggrFuncEnum.COUNT)
                        .build();

        assertTrue(
                aggregator.getClass().getSimpleName(),
                aggregator.getClass().getSimpleName()
                        .contains("MarkAggregator")
        );

        aggregator.close();
    }

    @Test
    public void quotedSqlAliasMapsToJdbcResultKey()
            throws Exception {
        Method method = SQLDataSet.class.getDeclaredMethod(
                "aggregateResultKey",
                String.class);
        method.setAccessible(true);

        assertEquals("A$id", method.invoke(null, "\"A$id\""));
        assertEquals("total", method.invoke(null, "total"));
    }
}
