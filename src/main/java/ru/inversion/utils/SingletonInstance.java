package ru.inversion.utils;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Аннтоция над типами, кторые допускают одновременное существование только одного экземпляра.
 * <p>
 * @author ssu
 */
@Target(value = {ElementType.TYPE})
@Retention(value = RetentionPolicy.RUNTIME)
public @interface SingletonInstance {
}
