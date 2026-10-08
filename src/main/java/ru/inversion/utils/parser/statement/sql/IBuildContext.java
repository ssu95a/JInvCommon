package ru.inversion.utils.parser.statement.sql;

import ru.inversion.utils.parser.Token;
import ru.inversion.utils.parser.statement.IStatementPart;
import ru.inversion.utils.parser.statement.SqlStatement;

public interface IBuildContext {

    /* Token/text accumulation */
    void addToken(Token<?> token);

    /* Structural parts (auto-flush inside) */
    void addPart(IStatementPart part);

    /** */
//    default void addMarker(MarkerType type) {
//        //addPart(new MarkerPart(type));
//    }

    /** Итог */
    SqlStatement toStatement();
}
