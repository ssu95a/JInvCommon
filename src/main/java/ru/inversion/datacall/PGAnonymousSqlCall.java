package ru.inversion.datacall;

import org.postgresql.PGConnection;
import org.postgresql.jdbc.PreferQueryMode;
import ru.inversion.dataset.IParameters;
import ru.inversion.dataset.parser.lexer.SqlScanner.Token;
import ru.inversion.tc.TaskContext;
import ru.inversion.utils.Jt2St;
import ru.inversion.utils.ParametersValues;
import ru.inversion.utils.Tags;
import ru.inversion.utils.converter.TypeConverter;
import ru.inversion.utils.lstn.IListenerManEvent;
import ru.inversion.utils.lstn.ListenerManFactory;

import javax.json.JsonArray;
import javax.json.JsonNumber;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import javax.json.JsonString;
import javax.json.JsonValue;
import javax.json.spi.JsonProvider;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.JDBCType;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import static javax.persistence.ParameterMode.IN;
import static ru.inversion.dataset.parser.lexer.SqlScanner.Kind.NAMED_PARAMETER;
import static ru.inversion.dataset.parser.lexer.SqlScanner.Kind.QUOTED_IDENTIFIER;
import static ru.inversion.dataset.parser.lexer.SqlScanner.Kind.WORD;

/** Выполняет анонимный блок PL/pgSQL через DO с параметрами в локальной настройке транзакции. */
public class PGAnonymousSqlCall implements IDataCall {

    private static final JsonProvider JSON = JsonProvider.provider();
    private static final String SETTING = "'jinv.anonymous_call'";
    private static final class Parameter {
        private final ParameterMetadata<?> metadata;
        private String variable;
        private int bindPosition = -1;
        private Object value = IParameters.NO_PARAMETER_VALUE;
        private Supplier<?> inputCallback;
        private BiConsumer<Object, Object> outputListener;

        private Parameter(ParameterMetadata<?> metadata) {
            this.metadata = metadata;
        }
    }

    private final TaskContext tc;
    private final boolean requiresAutoCommit;
    private final IParameters callBackParameters;
    private final Map<Comparable, Parameter> parameters = new TreeMap<>(ParametersValues.ParamIdComparator);
    private final String outerLabel;
    private final boolean hideValues;
    private final IListenerManEvent<IDataCallListener, DataCallEvent> listeners =
            ListenerManFactory.createListenerManEvent(IDataCallListener::dataCallChanged);
    private BiConsumer<Object, Object> outputListener;
    private Map<String, Object> properties;
    // Границы деклараций внутри preparedSql. До первого execute() этот участок пуст.
    private final int declarationsOffset;
    private int declarationsLength;
    private String preparedSql;
    private int bindCount;
    private int outputCount;
    private String name;
    private boolean closed;

    public PGAnonymousSqlCall(
            TaskContext tc,
            CallStatement callStatement,
            IParameters callBackParameters,
            String name
    ) {
        this.tc = Objects.requireNonNull(tc, "tc");
        this.requiresAutoCommit = Objects.requireNonNull(callStatement, "callStatement").isAutoCommit();
        this.name = name;
        this.callBackParameters = callBackParameters == null ? IParameters.EMPTY : callBackParameters;

        String source = callStatement.sourceSql();
        String sourceLower = source.toLowerCase(Locale.ROOT);
        String label = "jinv_outer";
        for (int i = 1; sourceLower.contains(label); i++)
            label = "jinv_outer_" + i;
        outerLabel = label;

        String tag = "$jinv_do$";
        for (int i = 1; source.contains(tag); i++)
            tag = "$jinv_do_" + i + "$";

        for (ParameterMetadata<?> parameter : callStatement.getParameterIter()) {
            if (!(parameter instanceof RawParameterMetadata) || ((RawParameterMetadata)parameter).isDeclared())
                parameters.put(parameter.getIdentifier(), new Parameter(parameter));
        }
        List<Token> tokens = callStatement.sqlTokens();
        Set<String> identifiers = new HashSet<>();
        identifiers.add("found");
        for (Token token : tokens) {
            if (token.kind() == WORD)
                identifiers.add(token.text(source).toLowerCase(Locale.ROOT));
            else if (token.kind() == QUOTED_IDENTIFIER) {
                String quoted = token.text(source);
                identifiers.add(quoted.substring(1, quoted.length() - 1).replace("\"\"", "\"").toLowerCase(Locale.ROOT));
            }
        }
        String block = replaceParameters(source, tokens, identifiers);
        for (Parameter parameter : parameters.values()) {
            if (parameter.metadata.isOut())
                bind(parameter, identifiers);
        }
        hideValues = parameters.values().stream().anyMatch(parameter ->
                parameter.bindPosition >= 0 && parameter.metadata.isIn() && !parameter.metadata.isLogValue());

        Parameter[] bound = boundParameters();
        StringBuilder sql = jdbcHeader(tag, bound);
        declarationsOffset = sql.length();
        appendJdbcBody(sql, block, tag, bound);
        preparedSql = sql.toString();
    }

    private void bind(Parameter parameter, Set<String> identifiers) {
        if (parameter.bindPosition >= 0)
            return;
        String variable = parameter.metadata.getName();
        // Позиционному параметру или имени, уже встречающемуся в блоке, даём отдельное имя.
        if (variable == null || !variable.matches("[\\p{L}_][\\p{L}\\p{N}_.]*")
                || variable.getBytes(StandardCharsets.UTF_8).length > 63
                || variable.toLowerCase(Locale.ROOT).startsWith(outerLabel)
                || !identifiers.add(variable.toLowerCase(Locale.ROOT))) {
            variable = outerLabel + "_p" + bindCount;
        }
        parameter.variable = variable;
        parameter.bindPosition = bindCount++;
        if (parameter.metadata.isOut())
            outputCount++;
    }

    private String replaceParameters(String source, List<Token> tokens, Set<String> identifiers) {
        StringBuilder result = new StringBuilder(source.length());
        Token last = null;
        int position = 0;
        for (Token token : tokens) {
            switch (token.kind()) {
                case NAMED_PARAMETER:
                case POSITIONAL_PARAMETER:
                    Comparable<?> id = token.kind() == NAMED_PARAMETER
                            ? source.substring(token.start() + 1, token.end()) : position++;
                    Parameter parameter = parameters.computeIfAbsent(id, key -> new Parameter(inferredInput(key)));
                    bind(parameter, identifiers);
                    result.append(outerLabel).append(".\"").append(parameter.variable).append('"');
                    break;
                case NUMBERED_PARAMETER:
                    throw new CallException(Tags.PRODUCT_LABEL + "Use :name or ? for anonymous block parameters");
                case OPERATOR:
                    // Внутри DO оператор доходит до сервера без JDBC-экранирования.
                    result.append(token.text(source).replace("??", "?"));
                    break;
                case WHITESPACE:
                case COMMENT:
                case HINT:
                case PARAMETERIZE_OFF:
                case PARAMETERIZE_ON:
                    result.append(source, token.start(), token.end());
                    continue;
                default:
                    result.append(source, token.start(), token.end());
            }
            last = token;
        }
        if (last == null)
            throw new CallException(Tags.PRODUCT_LABEL + "Empty PostgreSQL anonymous block");
        if (last.end() - last.start() != 1 || source.charAt(last.start()) != ';')
            result.append("\n;");
        return result.toString();
    }

    private static ParameterMetadata<?> inferredInput(Comparable<?> id) {
        return id instanceof String
                ? new RawParameterMetadata((String)id, null, null, IN, null, false, null, true, false)
                : new RawParameterMetadata((Integer)id, null, null, IN, null, false, null, true, false);
    }

    private Parameter requireParameter(Comparable<?> id) {
        Parameter parameter = parameters.get(id);
        if (parameter == null || parameter.metadata == null)
            throw new CallException(Tags.PRODUCT_LABEL + "No parameter with Id " + id);
        return parameter;
    }

    private Object value(Comparable<?> id, Parameter parameter) {
        if (parameter != null && parameter.value != IParameters.NO_PARAMETER_VALUE)
            return parameter.value;
        return id instanceof String ? callBackParameters.getParameter((String)id)
                : callBackParameters.getParameter((Integer)id);
    }

    private Object inputValue(Parameter parameter) {
        if (parameter.inputCallback != null)
            return parameter.inputCallback.get();
        ParameterMetadata<?> metadata = parameter.metadata;
        Object value = value(metadata.getIdentifier(), parameter);
        if (value != null || parameter.value != IParameters.NO_PARAMETER_VALUE || metadata.getDefaultValue() == null)
            return value;
        boolean supplied = metadata.isIndexed() ? callBackParameters.hasParameter(metadata.getPosition())
                : callBackParameters.hasParameter(metadata.getName());
        return supplied ? null : metadata.getDefaultValue();
    }

    private static String pgType(ParameterMetadata<?> parameter, Object value) {
        Integer declared = parameter.getSQLType();
        Class<?> javaType = value == null ? parameter.getParameterType() : value.getClass();
        int inferred = Types.NULL;
        if (javaType != null && (value != null || declared == null)) {
            try {
                inferred = Jt2St.get(javaType, Types.BOOLEAN);
            } catch (IllegalArgumentException ex) {
                throw new CallException("Parameter " + parameter.getIdentifier() + " requires a scalar value", ex);
            }
            pgScalarType(inferred, parameter);
        }

        if (declared == null && parameter.isOut())
            throw new CallException(Tags.PRODUCT_LABEL + "No SQL type for OUT parameter " + parameter.getIdentifier());
        int type = declared == null ? inferred : declared;
        if (type == Types.OTHER) {
            String element = parameter.getElementType();
            boolean uuid = element == null ? javaType == UUID.class || parameter.getParameterType() == UUID.class
                    : "uuid".equalsIgnoreCase(element) || "pg_catalog.uuid".equalsIgnoreCase(element);
            if (!uuid)
                throw unsupportedType(parameter, element == null ? "OTHER" : element);
        }
        return pgScalarType(type, parameter);
    }

    private static String pgScalarType(int sqlType, ParameterMetadata<?> parameter) {
        switch (sqlType) {
            case Types.BIT:
            case Types.BOOLEAN:
                return "pg_catalog.bool";
            case Types.TINYINT:
            case Types.SMALLINT:
                return "pg_catalog.int2";
            case Types.INTEGER:
                return "pg_catalog.int4";
            case Types.BIGINT:
                return "pg_catalog.int8";
            case Types.NUMERIC:
            case Types.DECIMAL:
                return "pg_catalog.numeric";
            case Types.REAL:
                return "pg_catalog.float4";
            case Types.FLOAT:
            case Types.DOUBLE:
                return "pg_catalog.float8";
            case Types.DATE:
                return "pg_catalog.date";
            case Types.TIME:
                return "pg_catalog.time";
            case Types.TIME_WITH_TIMEZONE:
                return "pg_catalog.timetz";
            case Types.TIMESTAMP:
                return "pg_catalog.timestamp";
            case Types.TIMESTAMP_WITH_TIMEZONE:
                return "pg_catalog.timestamptz";
            case Types.CHAR:
            case Types.NCHAR:
                return "pg_catalog.bpchar";
            case Types.VARCHAR:
            case Types.NVARCHAR:
            case Types.NULL:
                return "pg_catalog.varchar";
            case Types.LONGVARCHAR:
            case Types.LONGNVARCHAR:
                return "pg_catalog.text";
            case Types.OTHER:
                return "pg_catalog.uuid";
            case -1024:
                throw unsupportedType(parameter, "JSON_STRUCT");
            case -1025:
                throw unsupportedType(parameter, "JSON_ARRAY");
            case -1026:
                throw unsupportedType(parameter, "IN_LIST");
            default:
                throw unsupportedType(parameter, JDBCType.valueOf(sqlType).getName());
        }
    }

    private static CallException unsupportedType(ParameterMetadata<?> parameter, String type) {
        return new CallException(Tags.PRODUCT_LABEL + "Unsupported type " + type + " for parameter "
                + parameter.getIdentifier() + ". Use a stored procedure for complex parameters.");
    }

    private StringBuilder jdbcHeader(String delimiter, Parameter[] bound) {
        StringBuilder sql = new StringBuilder("SELECT 1 FROM pg_catalog.set_config(")
                .append(SETTING).append(", ?, true);\nDO ").append(delimiter)
                .append("\n<<").append(outerLabel).append(">>\n");
        if (bound.length > 0) {
            sql.append("DECLARE\n");
            if (Arrays.stream(bound).anyMatch(parameter -> parameter.metadata.isIn())) {
                sql.append(outerLabel).append("_input pg_catalog.jsonb := pg_catalog.current_setting(")
                        .append(SETTING).append(")::pg_catalog.jsonb;\n");
            }
        }
        return sql;
    }

    private void appendJdbcBody(StringBuilder sql, String block, String delimiter, Parameter[] bound) {
        sql.append("BEGIN\n").append(block).append('\n');

        if (outputCount > 0) {
            StringJoiner result = new StringJoiner(", ", "ARRAY[", "]");
            for (Parameter parameter : bound) {
                if (parameter.metadata.isOut())
                    result.add("pg_catalog.to_jsonb(\"" + parameter.variable + "\")");
            }
            sql.append("PERFORM pg_catalog.set_config(").append(SETTING).append(", pg_catalog.to_jsonb(")
                    .append(result).append(")::pg_catalog.text, true);\n");
        }
        sql.append("END ").append(outerLabel).append(";\n").append(delimiter).append(";\n");

        if (outputCount > 0) {
            // OUT записывается JSON-массивом. Если остался входной объект, ошибка SELECT откатит и DO.
            sql.append("SELECT data FROM (SELECT pg_catalog.current_setting(").append(SETTING)
                    .append(") AS data) AS result WHERE pg_catalog.jsonb_array_length(data::pg_catalog.jsonb) = ")
                    .append(outputCount).append(";\n");
        }
        if (hideValues)
            sql.append("--lhv:1\n");
    }

    private static Object fromJson(JsonValue value, Class<?> javaType) {
        switch (value.getValueType()) {
            case NULL:
                return null;
            case STRING:
                String text = ((JsonString)value).getString();
                if (javaType == UUID.class)
                    return UUID.fromString(text);
                if (javaType == OffsetDateTime.class)
                    return OffsetDateTime.parse(text);
                if (javaType == OffsetTime.class)
                    return OffsetTime.parse(text);
                return javaType == null ? text : TypeConverter.convert(text, javaType);
            case NUMBER:
                JsonNumber number = (JsonNumber)value;
                if (javaType != null)
                    return TypeConverter.convert(number.bigDecimalValue(), javaType);
                if (number.isIntegral() && number.bigIntegerValue().bitLength() <= 63)
                    return number.longValueExact();
                return number.bigDecimalValue();
            case TRUE:
            case FALSE:
                boolean flag = value == JsonValue.TRUE;
                return javaType == null ? flag : TypeConverter.convert(flag, javaType);
            default:
                throw new CallException(Tags.PRODUCT_LABEL + "Expected a scalar PostgreSQL result: " + value.getValueType());
        }
    }

    private String executeSql(String sql, String input) throws SQLException {
        Connection connection = tc.getConnection();
        boolean autoCommit = connection.getAutoCommit();
        if (requiresAutoCommit && !autoCommit)
            throw new CallException(Tags.PRODUCT_LABEL + "This anonymous block requires an auto-commit connection");
        if (autoCommit && connection.unwrap(PGConnection.class).getPreferQueryMode() == PreferQueryMode.SIMPLE)
            throw new CallException(Tags.PRODUCT_LABEL + "PostgreSQL anonymous calls in simple query mode require a transaction");

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, input);
            String result = null;
            int resultSets = 0;
            boolean hasResult = statement.execute();
            while (hasResult || statement.getUpdateCount() != -1) {
                if (hasResult) {
                    try (ResultSet rows = statement.getResultSet()) {
                        if (++resultSets == 2 && rows.next())
                            result = rows.getString(1);
                    }
                }
                hasResult = statement.getMoreResults();
            }
            return result;
        }
    }

    private void applyOutputs(String result, Parameter[] bound) {
        if (outputCount == 0)
            return;
        if (result == null)
            throw new CallException(Tags.PRODUCT_LABEL + "PostgreSQL anonymous block returned no OUT values");

        JsonArray json;
        try (JsonReader reader = JSON.createReader(new StringReader(result))) {
            json = reader.readArray();
        }
        if (json.size() != outputCount)
            throw new CallException(Tags.PRODUCT_LABEL + "Unexpected number of OUT values");

        // Сначала конвертируем все OUT, чтобы ошибка не оставила частично обновлённые значения.
        Object[] converted = new Object[outputCount];
        int index = 0;
        for (Parameter parameter : bound) {
            if (parameter.metadata.isOut()) {
                converted[index] = fromJson(json.get(index), parameter.metadata.getParameterType());
                index++;
            }
        }
        index = 0;
        for (Parameter parameter : bound) {
            if (parameter.metadata.isOut())
                parameter.value = converted[index++];
        }
        index = 0;
        for (Parameter parameter : bound) {
            if (!parameter.metadata.isOut())
                continue;
            Comparable<?> id = parameter.metadata.getIdentifier();
            Object value = converted[index++];
            if (outputListener != null)
                outputListener.accept(id, value);
            if (parameter.outputListener != null)
                parameter.outputListener.accept(id, value);
        }
    }

    private Parameter[] boundParameters() {
        Parameter[] bound = new Parameter[bindCount];
        for (Parameter parameter : parameters.values()) {
            if (parameter.bindPosition >= 0)
                bound[parameter.bindPosition] = parameter;
        }
        return bound;
    }

    @Override
    public PGAnonymousSqlCall execute() {
        if (closed)
            throw new CallException(Tags.PRODUCT_LABEL + "DataCall is closed");
        listeners.fire(new DataCallEvent(this, DataCallEvent.Type.EXECUTE, true));

        Throwable failure = null;
        String sql = null;
        try {
            Parameter[] bound = boundParameters();
            JsonObjectBuilder input = JSON.createObjectBuilder();
            StringBuilder declarations = new StringBuilder();
            for (Parameter parameter : bound) {
                boolean isInput = parameter.metadata.isIn();
                Object value = isInput ? inputValue(parameter) : null;
                String type = pgType(parameter.metadata, value);
                declarations.append('"').append(parameter.variable).append("\" ").append(type);
                if (isInput) {
                    declarations.append(" := (").append(outerLabel).append("_input ->> '")
                            .append(parameter.variable).append("')::").append(type);
                    if (value == null) {
                        input.addNull(parameter.variable);
                    } else {
                        if (value.getClass() == java.util.Date.class)
                            value = new java.sql.Date(((java.util.Date)value).getTime());
                        input.add(parameter.variable, value.toString());
                    }
                }
                declarations.append(";\n");
            }
            String declarationSql = declarations.toString();
            if (declarationsLength != declarationSql.length()
                    || !preparedSql.regionMatches(declarationsOffset, declarationSql, 0, declarationsLength)) {
                int declarationsEnd = declarationsOffset + declarationsLength;
                preparedSql = new StringBuilder(preparedSql.length() - declarationsLength + declarationSql.length())
                        .append(preparedSql, 0, declarationsOffset)
                        .append(declarationSql)
                        .append(preparedSql, declarationsEnd, preparedSql.length())
                        .toString();
                declarationsLength = declarationSql.length();
            }
            sql = preparedSql;
            applyOutputs(executeSql(sql, input.build().toString()), bound);
            return this;
        } catch (SQLException | RuntimeException ex) {
            CallException error = new CallException(Tags.PRODUCT_LABEL + "Error on execute PostgreSQL anonymous block",
                    name, sql, ex);
            failure = error;
            throw error;
        } catch (Error error) {
            failure = error;
            throw error;
        } finally {
            try {
                listeners.fire(new DataCallEvent(this, DataCallEvent.Type.EXECUTE, false, failure == null));
            } catch (RuntimeException | Error listenerFailure) {
                if (failure == null)
                    throw listenerFailure;
                if (listenerFailure != failure)
                    failure.addSuppressed(listenerFailure);
            }
        }
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public void setProperty(String propertyName, Object value) {
        if (properties == null)
            properties = new HashMap<>();
        properties.put(propertyName, value);
    }

    @Override
    public Object getProperty(String propertyName) {
        return properties == null ? null : properties.get(propertyName);
    }

    private static Comparable<?> parameterId(Object id) {
        Objects.requireNonNull(id, "id");
        return id instanceof Number ? ((Number)id).intValue() : id.toString();
    }

    @Override
    public <V> void setValueCallback(Object id, Supplier<V> supplier) {
        // Явный null перекрывает локальное значение, источник параметров и default.
        requireParameter(parameterId(id)).inputCallback = supplier == null ? () -> null : supplier;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <V> void setValueListener(Object id, BiConsumer<Object, V> listener) {
        Parameter parameter = requireParameter(parameterId(id));
        if (!parameter.metadata.isOut())
            throw new CallException(Tags.PRODUCT_LABEL + "Parameter '" + id + "' is not OUT or INOUT");
        parameter.outputListener = (BiConsumer<Object, Object>)listener;
    }

    @Override
    public void setValueListener(BiConsumer<Object, Object> listener) {
        outputListener = listener;
    }

    @Override
    public void addDataCallListener(IDataCallListener listener) {
        listeners.addListener(listener);
    }

    @Override
    public void removeDataCallListener(IDataCallListener listener) {
        listeners.removeListener(listener);
    }

    @Override
    public ParameterMetadata<?> getParameterMetadata(Object id) {
        Parameter parameter = id == null ? null : parameters.get(parameterId(id));
        return parameter == null ? null : parameter.metadata;
    }

    @Override
    public Iterable<ParameterMetadata<?>> getParameterIter() {
        return () -> parameters.values().stream().filter(parameter -> parameter.metadata != null)
                .<ParameterMetadata<?>>map(parameter -> parameter.metadata).iterator();
    }

    @Override
    public Object getParameter(String parameterName) {
        return value(parameterName, parameters.get(parameterName));
    }

    @Override
    public Object getParameter(int parameterIndex) {
        return value(parameterIndex, parameters.get(parameterIndex));
    }

    @Override
    public void setParameter(String parameterName, Object value) {
        parameters.computeIfAbsent(parameterName, ignored -> new Parameter(null)).value = value;
    }

    @Override
    public void setParameter(int parameterIndex, Object value) {
        parameters.computeIfAbsent(parameterIndex, ignored -> new Parameter(null)).value = value;
    }

    @Override
    public boolean hasParameter(String parameterName) {
        return getParameterMetadata(parameterName) != null;
    }

    @Override
    public boolean hasParameter(int parameterIndex) {
        return getParameterMetadata(parameterIndex) != null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getReturnValue() {
        for (Parameter parameter : parameters.values()) {
            if (parameter.metadata != null && parameter.metadata.isReturnValue())
                return (T)value(parameter.metadata.getIdentifier(), parameter);
        }
        return null;
    }

    @Override
    public void close() {
        if (closed)
            return;
        listeners.fire(new DataCallEvent(this, DataCallEvent.Type.CLOSE, true));
        closed = true;
        listeners.fire(new DataCallEvent(this, DataCallEvent.Type.CLOSE, false));
    }
}
