package ru.inversion.db.entity;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 *
 * @author sulimoff
 */
@Target(value = ElementType.METHOD)
@Retention(value = RetentionPolicy.RUNTIME)
public @interface DBReturningValue {
    boolean insert() default true;
    boolean update() default false;
    /**
     * при insert брать значение из Sequence, должен быть задан аннотацией @Sequence
     * */
    boolean sequenceValue() default false;
}
