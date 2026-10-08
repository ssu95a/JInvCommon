package ru.inversion.parser.node;

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


    DISTINCT,

    ROLLUP,

    OR,

    AND,

    NOT,

    IN,

//    BY,

    ALL,

    AS,

// -------- //

//    SELECT,
//
//    FROM,

    WHERE,

//    ORDER_BY,

    GROUP_BY,

    CONNECT,

    UNION,

    HAVING,

    PARTITION,

    WITH,

    MINUS,

    INTERSECT

}
