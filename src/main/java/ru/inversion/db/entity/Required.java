package ru.inversion.db.entity;

import java.lang.annotation.*;

/**
 * @author lebedev-m
 */
@Target(value = ElementType.METHOD)
@Retention(value = RetentionPolicy.RUNTIME)
public @interface Required {
}
