package ru.inversion.db.entity;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import static ru.inversion.db.entity.RegisterEnum.UNKNOWN_CASE;

/**
 *
 * @author ssu
 */
@Target(value = ElementType.METHOD)
@Retention(value = RetentionPolicy.RUNTIME)
public @interface EntityAttribute {
    /** */
    RegisterEnum caseSensitiveSearchMode() default UNKNOWN_CASE;
    /** */
    boolean decimalStripTrailingZeros() default false;
    /** */
    boolean useInLOVPredicate() default true;
}

