package ru.inversion.tds;

import java.util.function.Predicate;

/** */
public class TreeDataSetSearchParam<P> {

    /**
     * Искать только по листовым узлам
     */
    final private boolean inLeafOnly;

    /**
     * Искать с начала/с текущей позиции
     */
    final private boolean fromBeginning;

    /**
     * Если поиск идёт не с начала, а от текущего узла:
     * при достижении конца дерева продолжить поиск с начала.
     */
    private final boolean wrapAround;

    /**
     * Условие поиска
     */
    final private Predicate<P> predicate; // условие

    /** */
    public TreeDataSetSearchParam(Predicate< P > predicate, boolean inLeafOnly, boolean fromBeginning, boolean wrapAround) {
        this.inLeafOnly    = inLeafOnly;
        this.fromBeginning = fromBeginning;
        this.predicate     = predicate;
        this.wrapAround    = wrapAround;
    }

    public boolean inLeafOnly() {
        return inLeafOnly;
    }
    /** */

    public boolean fromBeginning() {
        return fromBeginning;
    }

    /** */
    public boolean fromCurrent() {
        return !fromBeginning;
    }

    /** */
    public Predicate<P> predicate() {
        return predicate;
    }

    /** */
    public boolean wrapAround() {
        return wrapAround;
    }
}
