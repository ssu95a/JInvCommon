package ru.inversion.utils.parser.statement.sql.state;

import ru.inversion.utils.parser.Token;

import java.util.Iterator;
import java.util.function.Function;

/** */
public class SelectState implements Function<Iterator<Token<?>>,Boolean> {

    @Override
    public Boolean apply( Iterator<Token<?>> iter ) {

        // в iter current state - is SELECT and level = 0
        return null;
    }
}
