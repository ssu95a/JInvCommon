package ru.inversion.dataset.impl;

import org.junit.Test;
import ru.inversion.dataset.IParameters;
import ru.inversion.db.dialect.SqlDialectFactory;
import ru.inversion.utils.sparser.SqlScannerFactory;
import ru.inversion.utils.sparser.jdbc.Parameter;
import ru.inversion.utils.sparser.jdbc.PreparedSql;
import ru.inversion.utils.sparser.select.SqlSelect;

import java.math.BigInteger;
import java.util.Arrays;

import static org.junit.Assert.*;

/** Verifies diagnostic values use the same parameter order as JDBC execution. */
public class PreparedSqlRendererTest {

    @Test
    public void renderMatchesCompiledSelectParameterOrder() {
        String source =
                "select id from t where tenant = :tenant"
                + " and state = 'A-1' and ref = ?"
                + " and level = 12 and parent = $2 /* ? :ignored */";

        PreparedSql prepared = new SqlSelect(
                source, SqlDialectFactory.POSTGRES).prepare();

        assertEquals(Arrays.asList(
                Parameter.Kind.NAMED,
                Parameter.Kind.LITERAL,
                Parameter.Kind.POSITIONAL,
                Parameter.Kind.LITERAL,
                Parameter.Kind.NUMBERED
        ), Arrays.asList(
                prepared.getParameters().get(0).getKind(),
                prepared.getParameters().get(1).getKind(),
                prepared.getParameters().get(2).getKind(),
                prepared.getParameters().get(3).getKind(),
                prepared.getParameters().get(4).getKind()
        ));

        IParameters values = new IParameters() {
            @Override
            public Object getParameter(String name) {
                return "tenant".equals(name) ? 7 : null;
            }

            @Override
            public Object getParameter(int index) {
                if( index == 0 ) return 55;
                if( index == 1 ) return 99;
                throw new AssertionError("Unexpected parameter index: " + index);
            }
        };

        String rendered = PreparedSqlRenderer.render(
                prepared, values,
                SqlScannerFactory.create(SqlDialectFactory.POSTGRES)
        );

        assertEquals(
                "select id from t where tenant = 7"
                + " and state =  'A-1'  and ref = 55"
                + " and level = 12 and parent = 99 /* ? :ignored */",
                rendered
        );
    }

    @Test
    public void ignoresQuestionMarksInSqlStrings() {
        PreparedSql prepared = new PreparedSql(
                "select '?' as literal_text, ? from t where x = ?",
                Arrays.asList(Parameter.literal("A"), Parameter.positional())
        );
        IParameters values = new IParameters() {
            @Override public Object getParameter(String name) { return null; }
            @Override public Object getParameter(int index) {
                assertEquals(0, index);
                return null;
            }
        };

        assertEquals(
                "select '?' as literal_text,  'A'  from t where x =  NULL ",
                PreparedSqlRenderer.render(
                        prepared,
                        values,
                        SqlScannerFactory.create(SqlDialectFactory.POSTGRES)
                )
        );
    }
}
