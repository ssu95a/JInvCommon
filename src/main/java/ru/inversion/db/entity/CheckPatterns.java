package ru.inversion.db.entity;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author  Shchapov
 * @date    created 18.12.2024
 *
 * Позволяет во время работы формы переключаться между различными шаблонами проверки(см класс CheckPattern).
 *
 * Пример использования:
 * Над геттером в pojo
 * @CheckPatterns(value = {
 *      @CheckPattern( pattern = "\\d{6}", descr_key = "num_check_id_21", descr_bundle = "ru.inversion.fxsmev.cmn.sm007.res.Sm007" ),
 *      @CheckPattern( pattern = "\\d{7}", descr_key = "num_check_id_22", descr_bundle = "ru.inversion.fxsmev.cmn.sm007.res.Sm007" )
 * })
 *
 * Установить текущий шаблон проверки(CheckPattern)
 * CDOC_NUM.setCheckPattern(String descr_key)
 *
 * Если не указан метод setCheckPattern явно, то по умолчанию используется первый шаблон проверки из списка.
 * Если у геттера присутствуют одновременно аннотации @CheckPattern и @CheckPatterns,
 * и не указан явно метод setCheckPattern, то используется шаблон проверки из аннотации @CheckPattern
 */
@Target(value = ElementType.METHOD)
@Retention(value = RetentionPolicy.RUNTIME)
public @interface CheckPatterns {
    CheckPattern[] value();
}
