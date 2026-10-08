package ru.inversion.utils.js;

import ru.inversion.utils.Pair;

import java.io.Reader;
import java.time.LocalDateTime;
import java.util.function.Function;


/** */
public interface IScriptSupplier extends Function<Object, Pair<Reader,LocalDateTime> > {
    /** */
    String makeGlobalId( Object id );

    /** Устарел ли скрипт в кэше */
    boolean isOutdated ( Object id, LocalDateTime time );
}
