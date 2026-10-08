package ru.inversion.db.entity;

import java.lang.annotation.*;

/**
 * @author lebedev-m, foma
 *
 * Пример использования:
 * @CheckPattern( pattern = "(.{0}|.{10})", descr_bundle = "ru/inversion/fxproj/res/MyBundle", descr_key = "EMPTY_OR_TEN")
 * или
 * @CheckPattern( id="RUSSIAN", descr_bundle = "ru/inversion/fxproj/res/MyBundle", descr_key = "IMECHKO_U_VAS_NE_RUSSKOE")
 * над геттером в пожо
 */
@Target(value = ElementType.METHOD)
@Retention(value = RetentionPolicy.RUNTIME)
public @interface CheckPattern
{
    /** Regex-паттерн. При заполненном id игнорируется   */
    String pattern() default "";
    /** Regex-группа. По умолчанию 0 (полное соответствие)*/
    int group() default 0;
    /** Название хранимого паттерна.
     При использовании перекрывает значение pattern()*/
    String id() default "";
    /** Ключ текста описания в бандле.
     Текст отображается в тултипе и при неверном заполнении поля */
    String descr_key() default "";
    /** Бандл, содержащий текст описания */
    String descr_bundle() default "";
}
