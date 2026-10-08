package ru.inversion.utils.parser.lexer;

/**
 * Ошибка внутреннего контракта лексического движка
 * или ошибка выполнения recognizer-а.
 */
public class LexerException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public LexerException(String message) {
        super(message);
    }

    public LexerException(String message, Throwable cause) {
        super(message, cause);
    }

    public LexerException(Throwable cause) {
        super(cause);
    }
}