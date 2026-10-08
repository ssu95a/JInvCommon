package ru.inversion.meta;

import java.lang.annotation.Annotation;

import ru.inversion.db.dialect.SqlDialect;
import ru.inversion.db.entity.ContentTypeEnum;
import ru.inversion.db.entity.RegisterEnum;
import ru.inversion.utils.IDumpable;

/**
 *
 * @author ssu
 */
public interface IEntityProperty<P,R> extends IDumpable {

    /** */
    boolean isId( );
    
    /** */
    String getName( );

    /** */
    default String getPropertyName() { return getName(); } // по умолчанию как раньше

    /** */
    default String getColumnName() {
        return getColumnInfo() == null ? null : getColumnInfo().getName();
    }

    /** */
    default String getColumnName( SqlDialect dialect ) {
        return getColumnInfo() == null ? null : dialect.columnName(this);
    }

    /** */
    Class getType( );
    
    /** */
    ContentTypeEnum getContent( );
    
    /** */
    IEntityColumn<P,R> getColumnInfo( );
    
    /** */
    boolean isTransient( );
    
    /** */
    void invokeSetter( P pojo, Object param );
    
    /** */
    R invokeGetter( P pojo );
    
    /** */
    boolean isRequired( );
    
    /** */
    int getLength( );
    
    /** */
    IEntityProperty<P,?> getProxyFor( );
    
    /** */
    String getOrderBy( );

    /** */
    String makeOrderBy( SqlDialect dialect );

    /** 
     * Возвращает аннотацию класса {@code annotationClass} если она объявлена около getter'а.
     */
    <T extends Annotation> T getAnnotation( Class<T> annotationClass );

    /** */
    boolean isReadOnly( );
    
    /** */
    default RegisterEnum getCaseSensitiveSearchMode() { return RegisterEnum.UNKNOWN_CASE; }

    /** */
    default boolean isDecimalStripTrailingZeros() { return false; }

    /** */
    default boolean useInLOVPredicate() { return true; }
}
