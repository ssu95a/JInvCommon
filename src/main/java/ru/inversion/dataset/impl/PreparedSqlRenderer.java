package ru.inversion.dataset.impl;

import ru.inversion.dataset.IParameters;
import ru.inversion.utils.sparser.SqlValueFormatter;
import ru.inversion.utils.sparser.jdbc.Parameter;
import ru.inversion.utils.sparser.jdbc.PreparedSql;
import ru.inversion.utils.sparser.lexer.SqlScanner;

import java.util.List;
import java.util.Objects;

/** Diagnostic SQL text rendered from the same parameter plan used by JDBC. */
public final class PreparedSqlRenderer {

    private PreparedSqlRenderer() {
    }

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

    /** Render already resolved raw values in physical JDBC order. */
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
