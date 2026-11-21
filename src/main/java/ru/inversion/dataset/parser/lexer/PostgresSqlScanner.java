package ru.inversion.dataset.parser.lexer;

import ru.inversion.dataset.parser.lexer.recognizer.RecognizerSets;



/** Лексический сканнер для PostgreSQL/Postgres Pro. */
public final class PostgresSqlScanner extends SqlScanner {

    public PostgresSqlScanner() {
        super(RecognizerSets.postgres());
    }

    private PostgresSqlScanner(boolean procedural) {
        super(RecognizerSets.postgres(procedural));
    }

    public static PostgresSqlScanner forPlPgSql() {
        return new PostgresSqlScanner(true);
    }
}
