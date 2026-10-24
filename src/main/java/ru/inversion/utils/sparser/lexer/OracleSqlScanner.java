package ru.inversion.utils.sparser.lexer;

import ru.inversion.utils.sparser.lexer.recognizer.RecognizerSets;



/** Лексический сканнер для Oracle SQL. */
public final class OracleSqlScanner extends SqlScanner {

    public OracleSqlScanner() {
        super(RecognizerSets.oracle());
    }
}
