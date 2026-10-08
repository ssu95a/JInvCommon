package ru.inversion.meta;

/**
 * Константы для нужд работы с экземплярами сущностей
 */
public interface EntityConstants {

    /** */
    public static String PLSQL  = "$PL/SQL$";

    /** */
    public static String INSERT = "insert";

    /** */
    public static String UPDATE = "update";

    /** */
    public static String DELETE = "delete";

    /** */
    public static String LOCK   = "lock";

    /** */
    public static String LOCK_MODE_NONE   = "$NONE$";

    /** */
    public static String LOCK_MODE_TABLE  = "$TABLE$";

    /** */
    public static String LOCK_MODE_PLSQL  = PLSQL;

    /** */
    //public static String LOCK_MODE_STATEMENT  = "$STATEMENT$";

    /** */
    public static String LOCK_TABLE  = "lock_table";
}
