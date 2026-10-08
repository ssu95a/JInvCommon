package ru.inversion.utils.parser.sql.jdbc;

import ru.inversion.utils.parser.sql.transform.PreparedSql;
import ru.inversion.utils.parser.sql.transform.SqlBindingKind;
import ru.inversion.utils.parser.sql.transform.SqlParameterBinding;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Устанавливает значения параметров {@link PreparedSql}
 * в JDBC {@link PreparedStatement}.
 *
 * <p>Поддерживаются:</p>
 *
 * <ul>
 *     <li>исходные JDBC-параметры {@code ?};</li>
 *     <li>именованные параметры {@code :name};</li>
 *     <li>нумерованные параметры {@code $1}, {@code $2};</li>
 *     <li>значения SQL-литералов, заменённых компилятором.</li>
 * </ul>
 *
 * <p>Сначала разрешаются все значения. JDBC statement
 * изменяется только после успешной проверки полного набора
 * входных параметров.</p>
 */
public final class JdbcParameterBinder {

    private JdbcParameterBinder() {
    }

    /**
     * Устанавливает значения всех параметров.
     *
     * <p>Позиционные значения имеют нумерацию с единицы:
     * {@code sourcePosition == 1} соответствует элементу
     * {@code positionalValues.get(0)}.</p>
     *
     * <p>Один именованный параметр может присутствовать
     * в SQL несколько раз. Значение из {@code namedValues}
     * будет установлено во все соответствующие JDBC-позиции.</p>
     *
     * @param statement        JDBC statement, созданный для
     *                         {@link PreparedSql#sql()}
     * @param preparedSql      SQL и полный план привязки
     * @param positionalValues значения исходных {@code ?}
     *                         и {@code $n}
     * @param namedValues      значения исходных {@code :name}
     *
     * @throws NullPointerException если один из аргументов null
     * @throws IllegalArgumentException если отсутствует требуемое
     *                                  входное значение
     * @throws SQLException если JDBC-драйвер не смог установить
     *                      значение
     */
    public static void bind(
            PreparedStatement statement,
            PreparedSql preparedSql,
            List<?> positionalValues,
            Map<String, ?> namedValues
    ) throws SQLException {
        Objects.requireNonNull(
                statement,
                "statement"
        );

        Objects.requireNonNull(
                preparedSql,
                "preparedSql"
        );

        Objects.requireNonNull(
                positionalValues,
                "positionalValues"
        );

        Objects.requireNonNull(
                namedValues,
                "namedValues"
        );

        List<SqlParameterBinding> bindings =
                preparedSql.bindings();

        List<Object> resolvedValues =
                resolveValues(
                        bindings,
                        positionalValues,
                        namedValues
                );

        for (int index = 0;
             index < bindings.size();
             index++) {

            SqlParameterBinding binding =
                    bindings.get(index);

            statement.setObject(
                    binding.jdbcPosition(),
                    resolvedValues.get(index)
            );
        }
    }

    private static List<Object> resolveValues(
            List<SqlParameterBinding> bindings,
            List<?> positionalValues,
            Map<String, ?> namedValues
    ) {
        List<Object> result =
                new ArrayList<Object>(
                        bindings.size()
                );

        for (SqlParameterBinding binding
                : bindings) {

            result.add(
                    resolveValue(
                            binding,
                            positionalValues,
                            namedValues
                    )
            );
        }

        return result;
    }

    private static Object resolveValue(
            SqlParameterBinding binding,
            List<?> positionalValues,
            Map<String, ?> namedValues
    ) {
        SqlBindingKind kind =
                binding.kind();

        switch (kind) {
            case JDBC_POSITIONAL:
            case NUMBERED_POSITIONAL:
                return positionalValue(
                        binding,
                        positionalValues
                );

            case NAMED:
                return namedValue(
                        binding,
                        namedValues
                );

            case GENERATED_LITERAL:
                return binding.generatedValue();

            default:
                throw new IllegalStateException(
                        "Unsupported SQL binding kind: "
                                + kind
                );
        }
    }

    private static Object positionalValue(
            SqlParameterBinding binding,
            List<?> positionalValues
    ) {
        int sourcePosition =
                binding.sourcePosition();

        if (sourcePosition
                > positionalValues.size()) {

            throw new IllegalArgumentException(
                    "Missing positional parameter "
                            + sourcePosition
                            + " required by JDBC position "
                            + binding.jdbcPosition()
            );
        }

        return positionalValues.get(
                sourcePosition - 1
        );
    }

    private static Object namedValue(
            SqlParameterBinding binding,
            Map<String, ?> namedValues
    ) {
        String name =
                binding.name();

        if (!namedValues.containsKey(name)) {
            throw new IllegalArgumentException(
                    "Missing named parameter '"
                            + name
                            + "' required by JDBC position "
                            + binding.jdbcPosition()
            );
        }

        return namedValues.get(name);
    }
}
