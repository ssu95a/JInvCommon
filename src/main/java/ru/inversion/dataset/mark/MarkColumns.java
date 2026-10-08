package ru.inversion.dataset.mark;

/**
 * Интерфейс аннотаций для определения какие поля из DataSet используются при пометке.
 * <p>
 * Нужно для механизма "пометить все".
 * @author sulimoff
 */
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface MarkColumns {
    String[] columns( );
}