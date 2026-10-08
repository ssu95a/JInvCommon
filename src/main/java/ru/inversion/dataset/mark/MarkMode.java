package ru.inversion.dataset.mark;

/**
 * Интерфейс аннотации для настройки механизма пометки на pojo.
 * <p>
 * @author slimoff
 */
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Deprecated
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface MarkMode {
    /** @see MarkModeEnum */
    MarkModeEnum mode();
}