package ru.inversion.utils.parser.text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Применяет набор изменений к исходному тексту.
 *
 * <p>Все диапазоны должны быть заданы относительно одной
 * исходной версии текста.</p>
 *
 * <p>После проверки изменения применяются справа налево,
 * поэтому изменение текста справа не сдвигает диапазоны,
 * расположенные слева.</p>
 */
public final class TextChangeApplier {

    private TextChangeApplier() {
    }

    /**
     * Применяет изменения к исходному тексту.
     *
     * @param source  исходный текст
     * @param changes изменения в координатах исходного текста
     *
     * @return изменённый текст
     *
     * @throws NullPointerException
     *         если source, changes или один из элементов
     *         changes равен null
     *
     * @throws IllegalArgumentException
     *         если диапазон выходит за границы текста
     *         или изменения конфликтуют
     */
    public static String apply(
            CharSequence source,
            List<TextChange> changes
    ) {
        Objects.requireNonNull(
                source,
                "source"
        );

        Objects.requireNonNull(
                changes,
                "changes"
        );

        if (changes.isEmpty()) {
            return source.toString();
        }

        List<IndexedChange> orderedChanges =
                copyAndValidate(
                        source.length(),
                        changes
                );

        Collections.sort(
                orderedChanges,
                RIGHT_TO_LEFT
        );

        StringBuilder result =
                new StringBuilder(source);

        for (IndexedChange indexedChange
                : orderedChanges) {

            TextChange change =
                    indexedChange.change;

            TextRange range =
                    change.range();

            result.replace(
                    range.start(),
                    range.end(),
                    change.newText()
            );
        }

        return result.toString();
    }

    /**
     * Создаёт защищённую внутреннюю копию списка
     * и проверяет каждый диапазон.
     */
    private static List<IndexedChange> copyAndValidate(
            int sourceLength,
            List<TextChange> changes
    ) {
        List<IndexedChange> result =
                new ArrayList<IndexedChange>(
                        changes.size()
                );

        for (int index = 0;
             index < changes.size();
             index++) {

            TextChange change =
                    Objects.requireNonNull(
                            changes.get(index),
                            "changes[" + index + "]"
                    );

            TextRange range =
                    change.range();

            if (range.end() > sourceLength) {
                throw new IllegalArgumentException(
                        "Text change range "
                                + range
                                + " exceeds source length "
                                + sourceLength
                );
            }

            result.add(
                    new IndexedChange(
                            change,
                            index
                    )
            );
        }

        validateConflicts(result);

        return result;
    }

    /**
     * Проверяет изменения попарно.
     *
     * <p>Количество изменений одного SQL-запроса невелико,
     * поэтому простой O(n²) алгоритм здесь предпочтительнее
     * более сложной структуры интервалов.</p>
     */
    private static void validateConflicts(
            List<IndexedChange> changes
    ) {
        for (int leftIndex = 0;
             leftIndex < changes.size();
             leftIndex++) {

            TextChange left =
                    changes.get(leftIndex)
                            .change;

            for (int rightIndex = leftIndex + 1;
                 rightIndex < changes.size();
                 rightIndex++) {

                TextChange right =
                        changes.get(rightIndex)
                                .change;

                if (conflicts(
                        left.range(),
                        right.range()
                )) {
                    throw new IllegalArgumentException(
                            "Conflicting text changes: "
                                    + left.range()
                                    + " and "
                                    + right.range()
                    );
                }
            }
        }
    }

    /**
     * Определяет, конфликтуют ли два диапазона.
     *
     * <p>Разрешены:</p>
     *
     * <ul>
     *     <li>соседние непустые диапазоны;</li>
     *     <li>несколько вставок в одной позиции;</li>
     *     <li>вставка на границе заменяемого диапазона.</li>
     * </ul>
     *
     * <p>Запрещены:</p>
     *
     * <ul>
     *     <li>пересекающиеся непустые диапазоны;</li>
     *     <li>вставка строго внутри заменяемого диапазона.</li>
     * </ul>
     */
    private static boolean conflicts(
            TextRange left,
            TextRange right
    ) {
        if (left.intersects(right)) {
            return true;
        }

        if (left.isEmpty()) {
            return isStrictlyInside(
                    left.start(),
                    right
            );
        }

        if (right.isEmpty()) {
            return isStrictlyInside(
                    right.start(),
                    left
            );
        }

        return false;
    }

    private static boolean isStrictlyInside(
            int offset,
            TextRange range
    ) {
        return !range.isEmpty()
                && range.start() < offset
                && offset < range.end();
    }

    /**
     * Изменения применяются справа налево.
     *
     * <p>Для одинаковой позиции:</p>
     *
     * <ul>
     *     <li>сначала применяется замена, затем вставка;</li>
     *     <li>несколько вставок применяются в обратном порядке,
     *     чтобы итоговый текст сохранял порядок списка changes.</li>
     * </ul>
     */
    private static final Comparator<IndexedChange>
            RIGHT_TO_LEFT =
            new Comparator<IndexedChange>() {

                @Override
                public int compare(
                        IndexedChange left,
                        IndexedChange right
                ) {
                    TextRange leftRange =
                            left.change.range();

                    TextRange rightRange =
                            right.change.range();

                    int byStart =
                            Integer.compare(
                                    rightRange.start(),
                                    leftRange.start()
                            );

                    if (byStart != 0) {
                        return byStart;
                    }

                    boolean leftEmpty =
                            leftRange.isEmpty();

                    boolean rightEmpty =
                            rightRange.isEmpty();

                    /*
                     * При одинаковом начале непустая замена
                     * должна примениться раньше вставки.
                     */
                    if (leftEmpty != rightEmpty) {
                        return leftEmpty ? 1 : -1;
                    }

                    /*
                     * Чтобы вставки [A, B] в одной позиции
                     * дали итоговый текст AB, применять их
                     * нужно в порядке B, затем A.
                     */
                    return Integer.compare(
                            right.originalIndex,
                            left.originalIndex
                    );
                }
            };

    private static final class IndexedChange {

        private final TextChange change;
        private final int originalIndex;

        private IndexedChange(
                TextChange change,
                int originalIndex
        ) {
            this.change = change;
            this.originalIndex = originalIndex;
        }
    }
}