package ru.inversion.dataset.parser.lexer;

import ru.inversion.dataset.parser.lexer.recognizer.RecognizerSets;



/** Лексический сканнер для Oracle SQL. */
public final class OracleSqlScanner extends SqlScanner {

    public OracleSqlScanner() {
        super(RecognizerSets.oracle());
    }

    private OracleSqlScanner(boolean call) {
        super(RecognizerSets.oracle(call));
    }

    /** Разбирает общий синтаксис вызовов, включая JDBC escape и метки PL-блоков. */
    public static OracleSqlScanner forCall() {
        return new OracleSqlScanner(true);
    }
}
