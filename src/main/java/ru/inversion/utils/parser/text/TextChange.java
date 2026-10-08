package ru.inversion.utils.parser.text;

import java.util.Objects;

/**
 * Одно изменение исходного текста.
 *
 * <p>Диапазон задаётся в координатах исходного текста:</p>
 *
 * <ul>
 *     <li>пустой диапазон — вставка;</li>
 *     <li>непустой диапазон и пустой newText — удаление;</li>
 *     <li>непустой диапазон и непустой newText — замена.</li>
 * </ul>
 */
public final class TextChange {

    private final TextRange range;
    private final String newText;

    public TextChange(
            TextRange range,
            String newText
    ) {
        this.range = Objects.requireNonNull(
                range,
                "range"
        );

        this.newText = Objects.requireNonNull(
                newText,
                "newText"
        );
    }

    /**
     * Диапазон исходного текста, который нужно заменить.
     */
    public TextRange range() {
        return range;
    }

    /**
     * Новый текст, которым заменяется диапазон.
     */
    public String newText() {
        return newText;
    }

    /**
     * Возвращает true, если изменение вставляет текст
     * в определённую позицию.
     */
    public boolean isInsertion() {
        return range.isEmpty()
                && !newText.isEmpty();
    }

    /**
     * Возвращает true, если изменение удаляет диапазон.
     */
    public boolean isDeletion() {
        return !range.isEmpty()
                && newText.isEmpty();
    }

    /**
     * Возвращает true, если один непустой фрагмент
     * заменяется другим.
     */
    public boolean isReplacement() {
        return !range.isEmpty()
                && !newText.isEmpty();
    }

    /**
     * Возвращает true для пустого изменения:
     * пустой диапазон заменяется пустым текстом.
     */
    public boolean isEmpty() {
        return range.isEmpty()
                && newText.isEmpty();
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (!(object instanceof TextChange)) {
            return false;
        }

        TextChange other =
                (TextChange) object;

        return range.equals(other.range)
                && newText.equals(other.newText);
    }

    @Override
    public int hashCode() {
        int result = range.hashCode();
        result = 31 * result + newText.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "TextChange{"
                + "range=" + range
                + ", newText='" + newText + '\''
                + '}';
    }
}