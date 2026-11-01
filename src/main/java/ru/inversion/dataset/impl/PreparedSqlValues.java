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

/** Resolves a PreparedSql parameter plan to values in physical JDBC order. */
public final class PreparedSqlValues {

    private PreparedSqlValues() {
    }

    /** Raw runtime values in physical JDBC placeholder order. */
    public static List<Object> resolve(
            PreparedSql preparedSql,
            IParameters parameters
    ) {
        Objects.requireNonNull(preparedSql, "preparedSql");
        Objects.requireNonNull(parameters, "parameters");

        List<Object> values =
                new ArrayList<>(preparedSql.getParameters().size());

        int positionalIndex = 0;

        for( Parameter parameter : preparedSql.getParameters() ) {
            Object value;

            switch( parameter.getKind() ) {
                case POSITIONAL:
                    value = parameters.getParameter(positionalIndex++);
                    break;
                case NAMED:
                    value = parameters.getParameter(parameter.getName());
                    break;
                case NUMBERED:
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

    /** Values converted exactly as JDBC execution expects them. */
    public static List<Object> jdbc(
            PreparedSql preparedSql,
            IParameters parameters
    ) {
        return jdbc(preparedSql, resolve(preparedSql, parameters));
    }

    /** Convert already resolved raw values to JDBC-ready values. */
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

        for( int i = 0; i < values.size(); i++ ) {
            Object value = values.get(i);

            if( plan.get(i).getKind() == Parameter.Kind.LITERAL )
                value = legacyIntegralLiteral(value);

            jdbcValues.add(Jt2St.convert(value));
        }

        return Collections.unmodifiableList(jdbcValues);
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
