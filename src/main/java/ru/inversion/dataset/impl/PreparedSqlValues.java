package ru.inversion.dataset.impl;

import ru.inversion.dataset.IParameters;
import ru.inversion.utils.Jt2St;
import ru.inversion.utils.sparser.jdbc.Parameter;
import ru.inversion.utils.sparser.jdbc.PreparedSql;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Resolves a PreparedSql parameter plan and converts resolved values for JDBC.
 *
 * Raw resolution and JDBC conversion are intentionally separate:
 * diagnostics render raw application values, while execution uses jdbc(...).
 */
public final class PreparedSqlValues {

    private PreparedSqlValues() {
    }

    /**
     * Raw values in physical JDBC placeholder order.
     *
     * Runtime values are not converted through Jt2St here. A null IParameters
     * is valid only when the plan contains generated literals exclusively.
     */
    public static List<Object> resolve(
            PreparedSql preparedSql,
            IParameters parameters
    ) {
        Objects.requireNonNull(preparedSql, "preparedSql");

        List<Object> values =
                new ArrayList<>(preparedSql.getParameters().size());

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

    private static void requireParameters(
            IParameters parameters,
            String parameter
    ) {
        if( parameters == null )
            throw new IllegalArgumentException(
                    "Runtime parameters are required for " + parameter);
    }

    /** Resolve IParameters and convert the result exactly as JDBC execution expects. */
    public static List<Object> jdbc(
            PreparedSql preparedSql,
            IParameters parameters
    ) {
        return jdbc(preparedSql, resolve(preparedSql, parameters));
    }

    /**
     * Convert already resolved raw values to JDBC-ready values.
     *
     * Automatically generated integral literals preserve the legacy Integer /
     * Long narrowing rules. Runtime BigInteger values are never narrowed here.
     */
    public static List<Object> jdbc(
            PreparedSql preparedSql,
            List<?> values
    ) {
        Objects.requireNonNull(preparedSql, "preparedSql");
        Objects.requireNonNull(values, "values");

        List<Parameter> plan = preparedSql.getParameters();

        if( plan.size() != values.size() )
            throw new IllegalArgumentException(
                    "PreparedSql parameters/value count mismatch: "
                            + plan.size() + " != " + values.size());

        List<Object> jdbcValues = new ArrayList<>(values.size());

        for( int i = 0; i < values.size(); i++ )
            jdbcValues.add(toJdbcValue(plan.get(i), values.get(i)));

        return Collections.unmodifiableList(jdbcValues);
    }

    private static Object toJdbcValue(
            Parameter parameter,
            Object value
    ) {
        if( parameter.getKind() == Parameter.Kind.LITERAL )
            value = legacyIntegralLiteral(value);

        return Jt2St.convert(value);
    }

    /** Preserve legacy JDBC types for automatically generated integer literals. */
    private static Object legacyIntegralLiteral(Object value) {
        if( !(value instanceof BigInteger) )
            return value;

        BigInteger integer = (BigInteger)value;

        if( integer.compareTo(BigInteger.valueOf(Integer.MIN_VALUE)) >= 0
                && integer.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) <= 0 )
            return integer.intValue();

        if( integer.compareTo(BigInteger.valueOf(Long.MIN_VALUE)) >= 0
                && integer.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) <= 0 )
            return integer.longValue();

        return integer;
    }
}
