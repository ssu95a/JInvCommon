package ru.inversion.utils.parser.sql.dialect;

/**
 * Минимальный диалектный контракт SQL-препроцессора.
 *
 * <p>Интерфейс не зависит от JDBC, соединений,
 * метаданных сущностей и инфраструктуры приложения.</p>
 */
public interface SqlSyntaxDialect {

    /**
     * Проверяет поддержку синтаксической возможности.
     */
    boolean supports( SqlSyntaxFeature feature );
}