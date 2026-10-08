package ru.inversion.query.node;

/**
 * Виды частей SQL выражения
 * <p>
 * @author Sulimoff
 */
public enum NodeTypeEnum {
    //
    EXPRESSION,
    //
    SELECT,
    //
    HINT,
    //
    MARK,
    //
    FROM,
    //
    PREDICATE,
    //
    ORDER_BY,
    //могут быть named, indexed, constant
    PARAMETER,
    // Подвыражение, то что в круглых скобках
    COMPOSITE,
    // Узел содержаший текст
    TEXT
}
