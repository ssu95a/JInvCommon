package ru.inversion.dataset;

import org.junit.Test;
import ru.inversion.db.dialect.SqlDialectFactory;

import static org.junit.Assert.*;

public class SQLExpressionCompletedSqlTest {

    @Test
    public void postgresNamedAndPositionalParametersRespectQuotedText() {
        SQLExpression expression = new SQLExpression(
                "name = :name and code = ? and text = '?' /* :ignored ? */");
        expression.setParameter("name", "O'Brien");
        expression.setParameter(0, 42);

        assertEquals(
                "name =  'O''Brien'  and code = 42 and text = '?' /* :ignored ? */",
                expression.getCompletedSQL(SqlDialectFactory.POSTGRES));
    }

    @Test
    public void postgresNumberedParametersUseZeroBasedParameterIndex() {
        SQLExpression expression = new SQLExpression("id = $2 and status = $1");
        expression.setParameter(0, "ACTIVE");
        expression.setParameter(1, 17);

        assertEquals(
                "id = 17 and status =  'ACTIVE' ",
                expression.getCompletedSQL(SqlDialectFactory.POSTGRES));
    }

    @Test
    public void oracleQQuotedStringsAreNotInterpretedAsParameters() {
        SQLExpression expression = new SQLExpression(
                "text = q'[?:name]' and id = :id");
        expression.setParameter("id", 5);

        assertEquals(
                "text = q'[?:name]' and id = 5",
                expression.getCompletedSQL(SqlDialectFactory.ORACLE));
    }

    @Test
    public void explicitNullAndEmptySqlAreSupported() {
        SQLExpression expression = new SQLExpression("value = :value");
        expression.setParameter("value", null);
        assertEquals("value =  NULL ", expression.getCompletedSQL(SqlDialectFactory.POSTGRES));

        expression.setSQL(null);
        assertEquals("", expression.getCompletedSQL(SqlDialectFactory.POSTGRES));
    }
}
