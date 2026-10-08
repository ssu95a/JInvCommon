package ru.inversion.dataset.mark;

/**
 * Интерфейс аннотаций для таблицы пометки.
 * <p>
 * @author sulimoff
 */
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface MarkTable {
    String name();
}