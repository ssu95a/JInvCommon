package ru.inversion.dataset.parser.lexer;

import ru.inversion.dataset.parser.lexer.recognizer.RecognizerSets;



/** Лексический сканнер для PostgreSQL/Postgres Pro. */
public final class PostgresSqlScanner extends SqlScanner {

    public PostgresSqlScanner() {
        super(RecognizerSets.postgres());
    }
}
