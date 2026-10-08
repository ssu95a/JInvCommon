package ru.inversion.meta;

import ru.inversion.db.dialect.SqlDialect;
import ru.inversion.utils.IDumpable;

/**
 *
 * @author ssu
 */
public interface IEntityColumn<P,R> extends IDumpable {

    /** */
    String getName( );

    /** */
    boolean isInsertable( );

    /** */
    boolean isUpdatable( );

    /** */
    boolean isReturningOnInsert( );

    /** */
    boolean isSequenceValue();

    /** */
    boolean isReturningOnUpdate( );

    /** */
    IEntityProperty<P,R> getProperty();

    /** */
    int getSQLType();

    /** */
    boolean isNullable();

    /** */
    String getSequenceName();

    /**
     * Имя таблицы в которой находится колонка.
     * <p>
     * Может отличаться от той, на которой построена Entity
     */
    String getTableName();

    /**
     * Выражение на котором строится вычисляемая колонка.
     * <p>
     * Будет подставлен в запрос в виде <code>expr</code> <code>column_name</code>
     */
    Object getQueryExpr();

    /** Получение БД зависимого выражения для использования в запросе */
    String makeQueryExpr( SqlDialect dialect );

    /** */
    default boolean isIndexed() { return false; };

    /** in the name of perfectionism */
    @Deprecated
    default boolean isUpdateable( ) { return isUpdatable(); }

}
