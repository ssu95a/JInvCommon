package ru.inversion.dataset.parser.lexer;

import ru.inversion.dataset.parser.lexer.recognizer.RecognizerSets;



/** Лексический сканнер для Oracle SQL. */
public final class OracleSqlScanner extends SqlScanner {

    public OracleSqlScanner() {
        super(RecognizerSets.oracle());
    }
}
