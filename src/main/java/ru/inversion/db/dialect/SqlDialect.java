package ru.inversion.db.dialect;

import ru.inversion.meta.IEntityMetaData;
import ru.inversion.meta.IEntityProperty;
import ru.inversion.meta.impl.ArrayMetaData;
import ru.inversion.parser.statement.LangEnum;
import ru.inversion.tc.TaskContext;

import java.sql.Array;
import java.sql.ResultSetMetaData;
import java.sql.Struct;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** */
public interface SqlDialect {

    enum DialectId { ORACLE, POSTGRES }

    enum ReturningMode { NONE, ORACLE_OUT_PARAMS, POSTGRES_RESULTSET }

    DialectId type( );
    /** */
    String sqlName( String rawName ) ;

    /** */
    default String tableName(String rawTableName) {
        return sqlName(rawTableName);
    }

    /** */
    default String columnName(String columnName ) {
        return sqlName(columnName);
    }

    /** Столбец для свойства (при необходимости заключаемый в кавычки). */
    <P,R> String columnName(IEntityProperty<P,R> property );

    /**
     * Имя входного параметра при установке значения. [A-Za-z_][A-Za-z0-9_]*
     */
    <P,R> String inName( IEntityProperty<P,R> property );
    String inName(String name);

    /**
     * Имя выходного параметра OUT. Must match [A-Za-z_][A-Za-z0-9_]*
     */
    <P,R> String outName(IEntityProperty<P,R> property);
    String outName(String name);

    /**
     * Имя для привязки PK (может быть таким же, как inParam).
     * Default: "PK__" + inParam(pk)
     */
    default <P,R> String pkName( IEntityProperty<P,R> pkProperty ) {
        return "PK__" + inName(pkProperty);
    }

    /**
     * Выражение блокировки, добавляется к SELECT ... WHERE ...
     * waitSeconds:
     *  - oracle  : WAIT n if n>0, NOWAIT if n==0
     *  - postgres: NOWAIT if n==0;
     */
    String lockClause( int waitSeconds );

    /** Вызов следующего значения из sequence */
    String sequenceNextVal( String sequenceName );

    /** Выражения для преобразования к дате строки */
    String toDateClause( Object dateObj );

    /** Имя типа для массива */
    String elementType( String elementType );

    /** */
    ReturningMode returningMode( );

    /** */
    LangEnum plLang();

    /** */
    <P,R> String forLovPredicate( IEntityProperty<P,R> property, String lovParameter );

    /** */
    default <P> String forLovPredicate(IEntityMetaData<P> md, String lovParameter )
    {
        return forLovPredicate( md, lovParameter, null );
    }

    /** */
    <P> String forLovPredicate( IEntityMetaData<P> md, String lovParameter, Map<String,Object> parameters );

    /** */
    String forLovPredicate( String lovParameter, List<String> columns );

    /** */
    ResultSetMetaData getStructMetaData( TaskContext tc, String sqlTypeName );

    /** */
    Struct createStruct( TaskContext tc, String sqlTypeName, Object[] attributes );

    /** */
    ArrayMetaData getArrayMetaData(TaskContext tc, String sqlTypeName);

    /** */
    Array createArray(TaskContext tc, ArrayMetaData arrayMetaData, Collection<?> values);

    /** */
    default Array createArray(TaskContext tc, String sqlTypeName, Collection<?> values)
    {
        final ArrayMetaData metaData = getArrayMetaData(tc,sqlTypeName);
        return createArray( tc, metaData, values );
    }


    /*
    Path downloadData( TaskContext tc, String remoteSrc, String format, Function<String,Path> localPathMaker, BiConsumer<Integer, String> listener );
    <T extends Comparable<T>> IMarkerDao<T> createMarkerDao( MarkModeEnum markMode );
    <T extends Comparable<T>> IMarkerDao<T> createMarkerDao( MarkModeEnum markMode, long markerId );
    */

    /**
     * Разобрать исключение БД и привести его к общему, независимому от JDBC-драйвера виду.
     * <p>
     * Должны анализироваться:
     * - Throwable.getCause()
     * - SQLException.getNextException()
     * - BatchUpdateException
     *
     * @return информация об SQL-ошибке либо null, если SQLException в цепочке отсутствует.
     */
    //SqlExceptionInfo decodeException(Throwable error);
}
