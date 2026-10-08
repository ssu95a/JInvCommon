package ru.inversion.db.entity;

import ru.inversion.parser.statement.LangEnum;

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
public @interface ColumnSQLDefinition {
    // используется PL/SQL, PLpg/SQL, проч
    LangEnum lang();
    String columnDefinition();
}
