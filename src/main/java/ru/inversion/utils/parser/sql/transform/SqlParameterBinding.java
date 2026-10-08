package ru.inversion.utils.parser.sql.transform;

import java.util.Objects;

/**
 * Описывает один JDBC placeholder в итоговом SQL.
 */
public final class SqlParameterBinding {

    private final int jdbcPosition;
    private final SqlBindingKind kind;

    private final String name;
    private final Integer sourcePosition;
    private final Object generatedValue;

    private SqlParameterBinding(
            int jdbcPosition,
            SqlBindingKind kind,
            String name,
            Integer sourcePosition,
            Object generatedValue
    ) {
        if (jdbcPosition < 1) {
            throw new IllegalArgumentException(
                    "jdbcPosition < 1: "
                            + jdbcPosition
            );
        }

        this.jdbcPosition =
                jdbcPosition;

        this.kind =
                Objects.requireNonNull(
                        kind,
                        "kind"
                );

        this.name =
                name;

        this.sourcePosition =
                sourcePosition;

        this.generatedValue =
                generatedValue;

        validate();
    }

    public static SqlParameterBinding jdbcPositional(
            int jdbcPosition,
            int sourcePosition
    ) {
        return new SqlParameterBinding(
                jdbcPosition,
                SqlBindingKind.JDBC_POSITIONAL,
                null,
                sourcePosition,
                null
        );
    }

    public static SqlParameterBinding named(
            int jdbcPosition,
            String name
    ) {
        return new SqlParameterBinding(
                jdbcPosition,
                SqlBindingKind.NAMED,
                Objects.requireNonNull(
                        name,
                        "name"
                ),
                null,
                null
        );
    }

    public static SqlParameterBinding numberedPositional(
            int jdbcPosition,
            int sourcePosition
    ) {
        return new SqlParameterBinding(
                jdbcPosition,
                SqlBindingKind.NUMBERED_POSITIONAL,
                null,
                sourcePosition,
                null
        );
    }

    public static SqlParameterBinding generatedLiteral(
            int jdbcPosition,
            Object value
    ) {
        return new SqlParameterBinding(
                jdbcPosition,
                SqlBindingKind.GENERATED_LITERAL,
                null,
                null,
                value
        );
    }

    public int jdbcPosition() {
        return jdbcPosition;
    }

    public SqlBindingKind kind() {
        return kind;
    }

    public boolean isNamed() {
        return kind == SqlBindingKind.NAMED;
    }

    public boolean isGenerated() {
        return kind
                == SqlBindingKind.GENERATED_LITERAL;
    }

    /**
     * Имя без двоеточия.
     */
    public String name() {
        return name;
    }

    /**
     * Для исходного ? — порядковый номер ?.
     * Для $n — значение n.
     */
    public Integer sourcePosition() {
        return sourcePosition;
    }

    /**
     * Значение литерала, заменённого на ?.
     */
    public Object generatedValue() {
        return generatedValue;
    }

    private void validate() {
        switch (kind) {
            case JDBC_POSITIONAL:
            case NUMBERED_POSITIONAL:
                if (sourcePosition == null
                        || sourcePosition < 1) {

                    throw new IllegalArgumentException(
                            "Positional binding requires "
                                    + "sourcePosition"
                    );
                }

                requireNull(
                        name,
                        "Positional binding cannot have name"
                );

                requireNull(
                        generatedValue,
                        "Positional binding cannot have value"
                );
                break;

            case NAMED:
                if (name == null
                        || name.isEmpty()) {

                    throw new IllegalArgumentException(
                            "Named binding requires name"
                    );
                }

                requireNull(
                        sourcePosition,
                        "Named binding cannot have "
                                + "sourcePosition"
                );

                requireNull(
                        generatedValue,
                        "Named binding cannot have value"
                );
                break;

            case GENERATED_LITERAL:
                requireNull(
                        name,
                        "Generated binding cannot have name"
                );

                requireNull(
                        sourcePosition,
                        "Generated binding cannot have "
                                + "sourcePosition"
                );
                break;

            default:
                throw new IllegalStateException(
                        "Unsupported binding kind: "
                                + kind
                );
        }
    }

    private static void requireNull(
            Object value,
            String message
    ) {
        if (value != null) {
            throw new IllegalArgumentException(
                    message
            );
        }
    }
}