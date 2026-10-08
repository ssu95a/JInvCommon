package ru.inversion.dataset.impl;

import java.util.function.Function;

/** */
public class SQLQueryBuilder  {

    public enum SQLPartEnum {
        QUERY,
        MARK,
        PREDICATES,
        ORDER_BY,
        AFTER_ORDER_BY,
        HINT,
        ALIAS
    }

    public interface ISQLBuilderListener extends Function<SQLPartEnum,Object> {}

    final private ISQLBuilderListener listener;

    /** */
    public SQLQueryBuilder( ISQLBuilderListener l ) {
        listener = l;
    }

   /**
     *
     */
}
