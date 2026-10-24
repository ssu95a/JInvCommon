package ru.inversion.utils.sparser.lexer;

/**
 * Локальное правило распознавания одной SQL-лексемы.
 *
 * Возвращает null, если в offset лексема данного вида не начинается.
 * Если префикс лексемы распознан, но она не завершена, выбрасывает
 * IllegalArgumentException с позицией ошибки.
 * Реализации не хранят изменяемого состояния.
 */
public interface TokenRecognizer {
    SqlScanner.Token recognize(String sql, int offset);
}
