package ru.inversion.dataset.impl;

import ru.inversion.dataset.IParameters;
import ru.inversion.utils.Checks;
import ru.inversion.utils.Jt2St;
import ru.inversion.dataset.parser.jdbc.Parameter;
import ru.inversion.dataset.parser.jdbc.PreparedSql;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Resolves a PreparedSql parameter plan and converts resolved values for JDBC.
 *
 */
public final class PreparedSqlValues {

    private PreparedSqlValues() {
    }

    /**
     * Raw values in physical JDBC placeholder order.
     */
    public static List<Object> resolve( PreparedSql preparedSql, IParameters parameters )
    {
        Checks.Require.object(preparedSql, "preparedSql");

        List<Object> values = new ArrayList<>(preparedSql.getParameters().size());

        int positionalIndex = 0;

        for( Parameter parameter : preparedSql.getParameters() ) {
            Object value;

            switch( parameter.getKind() ) {
                case POSITIONAL:
                    requireParameters(parameters, "?");
                    value = parameters.getParameter(positionalIndex++);
                    break;
                case NAMED:
                    requireParameters(
                            parameters, ":" + parameter.getName());
                    value = parameters.getParameter(parameter.getName());
                    break;
                case NUMBERED:
                    requireParameters(
                            parameters, "$" + parameter.getNumber());
                    value = parameters.getParameter(parameter.getNumber() - 1);
                    break;
                case LITERAL:
                    value = parameter.getValue();
                    break;
                default:
                    throw new IllegalStateException(
                            "Unknown parameter kind: " + parameter.getKind());
            }

            values.add(value);
        }

        return Collections.unmodifiableList(values);
    }

    private static void requireParameters( IParameters parameters, String parameter )
    {
        if( parameters == null )
            throw new IllegalArgumentException( "Runtime parameters are required for " + parameter);
    }

    /** Resolve IParameters and convert the result exactly as JDBC execution expects. */
    public static List<Object> jdbc( PreparedSql preparedSql, IParameters parameters )
    {
        return jdbc(preparedSql, resolve(preparedSql, parameters));
    }

    /** Convert already resolved raw values to JDBC-ready values. */
    public static List<Object> jdbc( PreparedSql preparedSql, List<?> values )
    {
        Checks.Require.object( preparedSql, "preparedSql");
        Checks.Require.object( values, "values");

        List<Parameter> plan = preparedSql.getParameters();

        if( plan.size() != values.size() )
            throw new IllegalArgumentException( "PreparedSql parameters/value count mismatch: " + plan.size() + " != " + values.size());

        List<Object> jdbcValues = new ArrayList<>(values.size());

        for( int i = 0; i < values.size(); i++ )
             jdbcValues.add(toJdbcValue(plan.get(i), values.get(i)));

        return Collections.unmodifiableList(jdbcValues);
    }


    /** */
    private static Object toJdbcValue( Parameter parameter, Object value )
    {
        return Jt2St.convert(value);
    }
}
