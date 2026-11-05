package ru.inversion.dataset.impl;

import ru.inversion.dataset.IParameters;
import ru.inversion.utils.sparser.SqlValueFormatter;
import ru.inversion.utils.sparser.jdbc.PreparedSql;
import ru.inversion.utils.sparser.lexer.SqlScanner;

import java.util.List;
import java.util.Objects;

/**
 * Diagnostic SQL text rendered from a PreparedSql parameter plan.
 *
 * render(...) preserves supplied/raw values.
 * renderJdbc(...) first applies the same value conversion as JDBC execution.
 */
public final class PreparedSqlRenderer {

    private PreparedSqlRenderer() {
    }

    /** Compatibility/raw-value rendering from IParameters. */
    public static String render(
            PreparedSql preparedSql,
            IParameters parameters,
            SqlScanner scanner
    ) {
        return render(
                preparedSql,
                PreparedSqlValues.resolve(preparedSql, parameters),
                scanner
        );
    }

    /**
     * Render values exactly as they would be passed to PreparedStatement.
     */
    public static String renderJdbc(
            PreparedSql preparedSql,
            IParameters parameters,
            SqlScanner scanner
    ) {
        return render(
                preparedSql,
                PreparedSqlValues.jdbc(preparedSql, parameters),
                scanner
        );
    }

    /**
     * Convert already resolved raw values exactly as JDBC execution does,
     * then render them in physical placeholder order.
     */
    public static String renderJdbc(
            PreparedSql preparedSql,
            List<?> rawValues,
            SqlScanner scanner
    ) {
        return render(
                preparedSql,
                PreparedSqlValues.jdbc(preparedSql, rawValues),
                scanner
        );
    }

    /** Render already resolved values in physical JDBC placeholder order. */
    public static String render(
            PreparedSql preparedSql,
            List<?> values,
            SqlScanner scanner
    ) {
        Objects.requireNonNull(preparedSql, "preparedSql");
        Objects.requireNonNull(values, "values");
        Objects.requireNonNull(scanner, "scanner");

        if (preparedSql.getParameters().size() != values.size())
            throw new IllegalArgumentException(
                    "PreparedSql parameters/value count mismatch");

        String sql = preparedSql.getSql();
        StringBuilder result = new StringBuilder(sql.length() + 64);

        int copiedUntil = 0;
        int valueIndex = 0;

        for( SqlScanner.Token token : scanner.scan(sql) ) {
            if( token.kind() != SqlScanner.Kind.POSITIONAL_PARAMETER )
                continue;

            if( valueIndex >= values.size() )
                throw new IllegalStateException(
                        "PreparedSql has more JDBC placeholders than values");

            result.append(sql, copiedUntil, token.start());
            result.append(SqlValueFormatter.format(values.get(valueIndex++)));
            copiedUntil = token.end();
        }

        if( valueIndex != values.size() )
            throw new IllegalStateException(
                    "PreparedSql has fewer JDBC placeholders than values");

        result.append(sql, copiedUntil, sql.length());
        return result.toString();
    }

}
