package ru.inversion.utils.sparser.lexer;

import ru.inversion.utils.sparser.lexer.recognizer.RecognizerSets;



/** Лексический сканнер для PostgreSQL/Postgres Pro. */
public final class PostgresSqlScanner extends SqlScanner {

    public PostgresSqlScanner() {
        super(RecognizerSets.postgres());
    }
}
