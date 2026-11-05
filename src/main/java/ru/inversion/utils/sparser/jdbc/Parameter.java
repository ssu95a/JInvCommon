package ru.inversion.utils.sparser.jdbc;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Objects;

/**
 * Источник значения для одного JDBC-плейсхолдера в подготовленном SQL.
 * Позиция плейсхолдера определяется местом в списке параметров.
 *
 * Current execution uses one global parameter namespace. Fragment/source
 * ownership is no longer part of parameter resolution.
 */
public final class Parameter {

    /**
     * Compatibility value from the former fragment-ownership model.
     *
     * @deprecated current execution has one global parameter namespace.
     */
    @Deprecated
    public static final int GLOBAL_SOURCE = -1;

    public enum Kind {
        /** Исходный анонимный параметр {@code ?}. */
        POSITIONAL,

        /** Именованный параметр {@code :name}. */
        NAMED,

        /** Нумерованный параметр {@code $n}. */
        NUMBERED,

        /** Литерал, заменённый параметром при автопараметризации. */
        LITERAL
    }

    private final Kind kind;
    private final String name;
    private final int number;
    private final Object value;
    private final int sourceId;

    private Parameter(Kind kind, String name, int number, Object value, int sourceId) {
        if (sourceId < GLOBAL_SOURCE)
            throw new IllegalArgumentException("Invalid parameter sourceId: " + sourceId);

        this.kind = kind;
        this.name = name;
        this.number = number;
        this.value = value;
        this.sourceId = sourceId;
    }

    /**
     * Исходный {@code ?}. Индекс источника здесь не хранится:
     * потребитель нумерует только POSITIONAL с нуля в порядке итогового списка.
     */
    public static Parameter positional() {
        return positional(GLOBAL_SOURCE);
    }

    /**
     * @deprecated source ownership is ignored by the current execution model.
     */
    @Deprecated
    public static Parameter positional(int sourceId) {
        return new Parameter(Kind.POSITIONAL, null, 0, null, sourceId);
    }

    /** Имя без префикса {@code :}; сохраняется без нормализации. */
    public static Parameter named(String name) {
        return named(name, GLOBAL_SOURCE);
    }

    /**
     * @deprecated source ownership is ignored by the current execution model.
     */
    @Deprecated
    public static Parameter named(String name, int sourceId) {
        Objects.requireNonNull(name, "name");
        if (name.isEmpty() || name.charAt(0) == ':') {
            throw new IllegalArgumentException(
                    "Parameter name must be non-empty and without ':' prefix");
        }
        return new Parameter(Kind.NAMED, name, 0, null, sourceId);
    }

    /** Исходный номер {@code n} из {@code $n}, начиная с единицы. */
    public static Parameter numbered(int number) {
        return numbered(number, GLOBAL_SOURCE);
    }

    /**
     * @deprecated source ownership is ignored by the current execution model.
     */
    @Deprecated
    public static Parameter numbered(int number, int sourceId) {
        if (number < 1) {
            throw new IllegalArgumentException("Parameter number must be positive");
        }
        return new Parameter(Kind.NUMBERED, null, number, null, sourceId);
    }

    /**
     * Уже разобранное значение литерала: String, BigInteger или BigDecimal.
     * Принимаются только сами эти классы. Значение не преобразуется.
     * Значение null не допускается.
     */
    public static Parameter literal(Object value) {
        return literal(value, GLOBAL_SOURCE);
    }

    /**
     * @deprecated source ownership is ignored by the current execution model.
     */
    @Deprecated
    public static Parameter literal(Object value, int sourceId) {
        Objects.requireNonNull(value, "value");
        Class<?> type = value.getClass();
        if (type != String.class && type != BigInteger.class && type != BigDecimal.class) {
            throw new IllegalArgumentException("Unsupported literal type: " + type.getName());
        }
        return new Parameter(Kind.LITERAL, null, 0, value, sourceId);
    }

    public Kind getKind() {
        return kind;
    }

    /** Имя без {@code :}. Доступно только для NAMED. */
    public String getName() {
        requireKind(Kind.NAMED);
        return name;
    }

    /** Номер из {@code $n}, начиная с единицы. Доступен только для NUMBERED. */
    public int getNumber() {
        requireKind(Kind.NUMBERED);
        return number;
    }

    /** Разобранная константа. Доступна только для LITERAL. */
    public Object getValue() {
        requireKind(Kind.LITERAL);
        return value;
    }

    /**
     * Compatibility origin from the former fragment-ownership model.
     *
     * @deprecated current parameter resolution does not inspect sourceId.
     */
    @Deprecated
    public int getSourceId() {
        return sourceId;
    }

    private void requireKind(Kind expected) {
        if (kind != expected) {
            throw new IllegalStateException("Expected " + expected + ", actual " + kind);
        }
    }
}
