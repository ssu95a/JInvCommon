package ru.inversion.dataset.parser;

import ru.inversion.dataset.IParameters;
import ru.inversion.dataset.parser.jdbc.Parameter;
import ru.inversion.dataset.parser.jdbc.PreparedSql;
import ru.inversion.dataset.parser.lexer.OracleSqlScanner;
import ru.inversion.dataset.parser.lexer.SqlScanner;
import ru.inversion.dataset.parser.old.ExprInfo;
import ru.inversion.dataset.parser.old.SQLItem;
import ru.inversion.dataset.parser.old.anlzr.SQLAnalyzerHelper;
import ru.inversion.dataset.parser.select.SqlParameterizer;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import static ru.inversion.dataset.parser.old.SQLItem.ItemTypeEnum.COMMENT;
import static ru.inversion.dataset.parser.old.SQLItem.ItemTypeEnum.DECIMAL;
import static ru.inversion.dataset.parser.old.SQLItem.ItemTypeEnum.HINT;
import static ru.inversion.dataset.parser.old.SQLItem.ItemTypeEnum.INDEXED_PARAMETER;
import static ru.inversion.dataset.parser.old.SQLItem.ItemTypeEnum.INTEGER;
import static ru.inversion.dataset.parser.old.SQLItem.ItemTypeEnum.NAMED_PARAMETER;
import static ru.inversion.dataset.parser.old.SQLItem.ItemTypeEnum.STRING;
import static ru.inversion.dataset.parser.old.SQLItem.ItemTypeEnum.TEXT_EXPRESSION;

/**
 * Compatibility facade for the legacy SQLParser public API.
 *
 * @deprecated new code must use the dialect-aware parser classes from this
 * package directly. Parsing and parameterization are delegated to the new
 * scanner/parameterizer implementation. Legacy SQLItem and ExprInfo are kept
 * only as return types of the historical public API.
 */
@Deprecated
public class SQLParser implements Iterable<SQLItem> {

    /** Legacy language selector retained for source and binary compatibility. */
    public enum Lang {
        SQL,
        PL_SQL
    }

    private final String source;
    private final Lang lang;
    private final SqlScanner scanner;

    private List<SqlScanner.Token> tokens;
    private List<SQLItem> items;
    private PreparedSql preparedSql;

    public SQLParser(String sql) {
        this(sql, Lang.SQL);
    }

    public SQLParser(String sql, Lang lang) {
        this.source = Objects.requireNonNull(sql, "sql");
        this.lang = Objects.requireNonNull(lang, "lang");

        // The historical API has no dialect argument and was Oracle-oriented.
        // Dialect-aware callers must use SqlScannerFactory/SqlSelect directly.
        this.scanner = new OracleSqlScanner();
    }

    @Override
    public Iterator<SQLItem> iterator() {
        return legacyItems().iterator();
    }

    public String prepareActualSql(
            IParameters callbackParameters,
            List<Object> parameters
    ) {
        int positionalIndex = 0;

        for (Parameter parameter : prepared().getParameters()) {
            switch (parameter.getKind()) {
                case POSITIONAL:
                    if (callbackParameters != null)
                        parameters.add(callbackParameters.getParameter(positionalIndex++));
                    break;
                case NAMED:
                    if (callbackParameters != null)
                        parameters.add(callbackParameters.getParameter(parameter.getName()));
                    break;
                case NUMBERED:
                    if (callbackParameters != null)
                        parameters.add(callbackParameters.getParameter(parameter.getNumber() - 1));
                    break;
                case LITERAL:
                    parameters.add(parameter.getValue());
                    break;
                default:
                    throw new IllegalStateException(
                            "Unsupported parameter kind: " + parameter.getKind());
            }
        }

        return prepared().getSql();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public String prepareActualSqlEx(
            final IParameters callbackParameters,
            List<Function> parameters
    ) {
        int positionalIndex = 0;

        for (Parameter parameter : prepared().getParameters()) {
            switch (parameter.getKind()) {
                case POSITIONAL:
                    if (callbackParameters != null) {
                        final int index = positionalIndex++;
                        parameters.add((Function) value ->
                                callbackParameters.getParameter(index));
                    }
                    break;
                case NAMED:
                    if (callbackParameters != null) {
                        final String name = parameter.getName();
                        parameters.add((Function) value ->
                                callbackParameters.getParameter(name));
                    }
                    break;
                case NUMBERED:
                    if (callbackParameters != null) {
                        final int index = parameter.getNumber() - 1;
                        parameters.add((Function) value ->
                                callbackParameters.getParameter(index));
                    }
                    break;
                case LITERAL:
                    final Object literal = parameter.getValue();
                    parameters.add((Function) value -> literal);
                    break;
                default:
                    throw new IllegalStateException(
                            "Unsupported parameter kind: " + parameter.getKind());
            }
        }

        return prepared().getSql();
    }

    public String prepareActualSqlEx(List<Object> parameters) {
        int positionalIndex = 0;

        for (Parameter parameter : prepared().getParameters()) {
            switch (parameter.getKind()) {
                case POSITIONAL:
                    final int index = positionalIndex++;
                    parameters.add((Function<IParameters, Object>) values ->
                            values.getParameter(index));
                    break;
                case NAMED:
                    final String name = parameter.getName();
                    parameters.add((Function<IParameters, Object>) values ->
                            values.getParameter(name));
                    break;
                case NUMBERED:
                    final int number = parameter.getNumber() - 1;
                    parameters.add((Function<IParameters, Object>) values ->
                            values.getParameter(number));
                    break;
                case LITERAL:
                    parameters.add(parameter.getValue());
                    break;
                default:
                    throw new IllegalStateException(
                            "Unsupported parameter kind: " + parameter.getKind());
            }
        }

        return prepared().getSql();
    }

    public String prepareSqlWithParametersValue(IParameters callbackParameters) {
        StringBuilder result = new StringBuilder(source.length() + 64);
        int positionalIndex = 0;

        for (SqlScanner.Token token : tokens()) {
            switch (token.kind()) {
                case POSITIONAL_PARAMETER:
                    result.append(callbackParameters == null
                            ? token.text(source)
                            : SqlValueFormatter.format(
                                    callbackParameters.getParameter(positionalIndex++)));
                    break;
                case NAMED_PARAMETER:
                    result.append(callbackParameters == null
                            ? token.text(source)
                            : SqlValueFormatter.format(callbackParameters.getParameter(
                                    token.text(source).substring(1))));
                    break;
                case NUMBERED_PARAMETER:
                    result.append(callbackParameters == null
                            ? token.text(source)
                            : SqlValueFormatter.format(callbackParameters.getParameter(
                                    numberedIndex(token.text(source)))));
                    break;
                default:
                    result.append(token.text(source));
            }
        }

        return result.toString();
    }

    public String prepareExplainPlanSql() {
        return replacePreparedParameters(true);
    }

    public String prepareSql4MetaData() {
        return replacePreparedParameters(false);
    }

    public ExprInfo analyze() {
        return new SQLAnalyzerHelper(iterator()).analyze();
    }

    /** Compatibility entry point; formatting is implemented by the new utility. */
    public static String value2Str(Object value) {
        return SqlValueFormatter.format(value);
    }

    private PreparedSql prepared() {
        if (preparedSql == null) {
            preparedSql = lang == Lang.PL_SQL
                    ? preparePlSql()
                    : SqlParameterizer.prepare(source, scanner);
        }
        return preparedSql;
    }

    /** PL/SQL compatibility: numeric literals were never converted to binds. */
    private PreparedSql preparePlSql() {
        StringBuilder sql = new StringBuilder(source.length());
        List<Parameter> parameters = new ArrayList<>();

        for (SqlScanner.Token token : tokens()) {
            String text = token.text(source);

            switch (token.kind()) {
                case POSITIONAL_PARAMETER:
                    sql.append('?');
                    parameters.add(Parameter.positional());
                    break;
                case NAMED_PARAMETER:
                    sql.append('?');
                    parameters.add(Parameter.named(text.substring(1)));
                    break;
                case NUMBERED_PARAMETER:
                    sql.append('?');
                    parameters.add(Parameter.numbered(numberedIndex(text) + 1));
                    break;
                case STRING:
                    sql.append('?');
                    parameters.add(Parameter.literal(stringValue(text)));
                    break;
                default:
                    sql.append(text);
            }
        }

        return new PreparedSql(sql.toString(), parameters);
    }

    private List<SqlScanner.Token> tokens() {
        if (tokens == null)
            tokens = scanner.scan(source);
        return tokens;
    }

    private List<SQLItem> legacyItems() {
        if (items == null) {
            List<SQLItem> result = new ArrayList<>();

            for (SqlScanner.Token token : tokens())
                result.add(toLegacyItem(token));

            items = Collections.unmodifiableList(result);
        }

        return items;
    }

    private SQLItem toLegacyItem(SqlScanner.Token token) {
        String text = token.text(source);

        switch (token.kind()) {
            case POSITIONAL_PARAMETER:
                return new SQLItem(null, INDEXED_PARAMETER);
            case NAMED_PARAMETER:
                return new SQLItem(text.substring(1), NAMED_PARAMETER);
            case STRING:
                return new SQLItem(stringValue(text), STRING);
            case NUMBER:
                if (lang == Lang.PL_SQL)
                    return new SQLItem(text, TEXT_EXPRESSION);
                return numberItem(text);
            case COMMENT:
            case PARAMETERIZE_OFF:
            case PARAMETERIZE_ON:
                return new SQLItem(text, COMMENT);
            case HINT:
                return new SQLItem(text, HINT);
            default:
                return new SQLItem(text, TEXT_EXPRESSION);
        }
    }

    private static SQLItem numberItem(String text) {
        try {
            if (text.indexOf('.') >= 0
                    || text.indexOf('e') >= 0
                    || text.indexOf('E') >= 0) {
                return new SQLItem(new BigDecimal(text), DECIMAL);
            }

            return new SQLItem(new BigInteger(text), INTEGER);
        }
        catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid numeric literal: " + text, ex);
        }
    }

    private static String stringValue(String text) {
        return text.substring(1, text.length() - 1).replace("''", "'");
    }

    private String replacePreparedParameters(boolean explainPlan) {
        String sql = prepared().getSql();
        List<SqlScanner.Token> preparedTokens = scanner.scan(sql);
        StringBuilder result = new StringBuilder(sql.length() + 32);
        int copiedUntil = 0;
        int index = 1;

        for (SqlScanner.Token token : preparedTokens) {
            if (token.kind() != SqlScanner.Kind.POSITIONAL_PARAMETER)
                continue;

            result.append(sql, copiedUntil, token.start());
            if (explainPlan)
                result.append(":p").append(index++).append(' ');
            else
                result.append("null ");
            copiedUntil = token.end();
        }

        return result.append(sql, copiedUntil, sql.length()).toString();
    }

    private static int numberedIndex(String text) {
        try {
            return Integer.parseInt(text.substring(1)) - 1;
        }
        catch (NumberFormatException ex) {
            throw new IllegalArgumentException(
                    "Invalid numbered parameter: " + text, ex);
        }
    }
}
