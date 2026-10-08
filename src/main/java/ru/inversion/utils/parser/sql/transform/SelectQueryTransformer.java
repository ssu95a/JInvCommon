package ru.inversion.utils.parser.sql.transform;

import ru.inversion.utils.parser.text.TextChange;
import ru.inversion.utils.parser.text.TextRange;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Формирует точечные изменения внешнего SELECT-запроса
 * на основании {@link SelectQueryMap}.
 *
 * <p>Класс не применяет изменения самостоятельно.
 * Полученные {@link TextChange} должны передаваться
 * в TextChangeApplier.</p>
 */
public final class SelectQueryTransformer {

    private SelectQueryTransformer() { }

    /**
     * Усиливает существующий WHERE новым предикатом
     * либо добавляет WHERE, если он отсутствует.
     *
     * <p>Существующий предикат:</p>
     *
     * <pre>
     * where a = 1 or b = 2
     * </pre>
     *
     * <p>преобразуется в:</p>
     *
     * <pre>
     * where (a = 1 or b = 2) and (c = 3)
     * </pre>
     *
     * <p>Если WHERE отсутствует, создаётся вставка:</p>
     *
     * <pre>
     *  where c = 3
     * </pre>
     *
     * @param map       карта исходного SELECT-запроса
     * @param predicate добавляемый SQL-предикат
     *
     * @return изменения в координатах исходного SQL
     *
     * @throws NullPointerException
     *         если map или predicate равен null
     *
     * @throws IllegalArgumentException
     *         если predicate пуст либо существующий
     *         WHERE не содержит предикат
     */
    public static List<TextChange> strengthenWhere( SelectQueryMap map, String predicate )
    {
        Objects.requireNonNull(
                map,
                "map"
        );

        Objects.requireNonNull(
                predicate,
                "predicate"
        );

        if (predicate.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "predicate is empty"
            );
        }

        if (map.hasWhere()) {
            return strengthenExistingWhere(
                    map.wherePredicateRange(),
                    predicate
            );
        }

        return insertWhere(
                map.whereInsertion(),
                predicate
        );
    }

    /**
     * <h6>Обрамляет существующий предикат двумя вставками.</h6>
     * <p>
     * Исходный предикат не копируется и не собирается
     * заново, поэтому его форматирование и комментарии
     * сохраняются без изменений.
     */
    private static List<TextChange> strengthenExistingWhere( TextRange predicateRange, String predicate )
    {
        if( predicateRange.isEmpty() )
            throw new IllegalArgumentException( "Existing WHERE predicate is empty" );

        TextChange openParenthesis =
                new TextChange(
                        new TextRange(
                                predicateRange.start(),
                                predicateRange.start()
                        ),
                        "("
                );

        TextChange appendPredicate =
                new TextChange(
                        new TextRange(
                                predicateRange.end(),
                                predicateRange.end()
                        ),
                        ") and (" + predicate + ")"
                );

        return Arrays.asList(
                openParenthesis,
                appendPredicate
        );
    }

    /**
     * Создаёт новый WHERE в рассчитанной позиции.
     *
     * <p>Пробелы с обеих сторон гарантируют отделение
     * нового предложения от предыдущего и следующего
     * фрагментов SQL.</p>
     */
    private static List<TextChange> insertWhere(
            SqlAnchor insertion,
            String predicate
    ) {
        return Collections.singletonList(
                insertion.insert(
                        " where " + predicate + " "
                )
        );
    }

    public static TextChange prependSelectItem(
            SelectQueryMap map,
            String selectItem
    ) {
        Objects.requireNonNull(map, "map");
        Objects.requireNonNull(selectItem, "selectItem");

        if (selectItem.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "selectItem is empty"
            );
        }

        return map.selectItemInsertion()
                .insert(selectItem + ", ");
    }

    public static List<TextChange> replaceOrInsertOrderBy(
            SelectQueryMap map,
            String orderByItems
    ) {
        Objects.requireNonNull(map, "map");
        Objects.requireNonNull(orderByItems, "orderByItems");

        if (orderByItems.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "orderByItems is empty"
            );
        }

        String clause =
                "order by " + orderByItems;

        if (map.hasOrderBy()) {
            return Collections.singletonList(
                    new TextChange(
                            map.orderByClauseRange(),
                            clause
                    )
            );
        }

        return Collections.singletonList(
                map.orderByInsertion()
                        .insert(" " + clause + " ")
        );
    }
}