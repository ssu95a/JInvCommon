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
        Objects.requireNonNull(preparedSql, "preparedSql");
        Objects.requireNonNull(parameters, "parameters");
        Objects.requireNonNull(scanner, "scanner");

        String sql = preparedSql.getSql();
        List<Parameter> parameterList = preparedSql.getParameters();

        StringBuilder result = new StringBuilder(sql.length() + 64);
        int copiedUntil = 0;
        int parameterIndex = 0;
        int positionalIndex = 0;

        for( SqlScanner.Token token : scanner.scan(sql) ) {
            if( token.getKind() != SqlScanner.Kind.POSITIONAL_PARAMETER )
                continue;

            if( parameterIndex >= parameterList.size() )
                throw new IllegalStateException(
                        "PreparedSql has more JDBC placeholders than parameters");

            Parameter parameter = parameterList.get(parameterIndex++);
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

            result.append(sql, copiedUntil, token.getStart());
            result.append(SqlValueFormatter.format(value));
            copiedUntil = token.getEnd();
        }

        if( parameterIndex != parameterList.size() )
            throw new IllegalStateException(
                    "PreparedSql has fewer JDBC placeholders than parameters");

        result.append(sql, copiedUntil, sql.length());
        return result.toString();
    }
}
