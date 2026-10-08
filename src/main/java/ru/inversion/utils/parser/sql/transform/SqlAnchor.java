package ru.inversion.utils.parser.sql.transform;

import ru.inversion.utils.parser.text.TextChange;
import ru.inversion.utils.parser.text.TextRange;

import java.util.Objects;

/**
 * Позиция вставки в исходном SQL-тексте.
 *
 * <p>Смещение задаётся относительно исходной версии текста.
 * Якорь не содержит вставляемый текст и не определяет
 * форматирование вставки.</p>
 */
public final class SqlAnchor {

    private final int offset;

    public SqlAnchor(int offset) {
        if( offset < 0)
            throw new IllegalArgumentException( "offset < 0: " + offset );

        this.offset = offset;
    }

    /**
     * Позиция вставки в исходном тексте.
     */
    public int offset() {
        return offset;
    }

    /**
     * Возвращает пустой диапазон в позиции якоря.
     */
    public TextRange range() {
        return new TextRange( offset, offset );
    }

    /**
     * Создаёт изменение, вставляющее текст
     * в позицию якоря.
     */
    public TextChange insert(String text) {
        Objects.requireNonNull( text, "text" );
        return new TextChange( range(), text );
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (!(object instanceof SqlAnchor)) {
            return false;
        }

        SqlAnchor other =
                (SqlAnchor) object;

        return offset == other.offset;
    }

    @Override
    public int hashCode() {
        return offset;
    }

    @Override
    public String toString() {
        return "SqlAnchor{"
                + "offset=" + offset
                + '}';
    }
}