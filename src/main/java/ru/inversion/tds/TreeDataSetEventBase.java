package ru.inversion.tds;

import java.util.EventObject;

public abstract class TreeDataSetEventBase<P> extends EventObject {

    /**
     * Признак событие сформировано до действия или после.
     */
    final private boolean before;

    public TreeDataSetEventBase(Object source, boolean before) {
        super(source);
        this.before = before;
    }

    /** */
    public <T extends ITreeDataSet<P>> T getTreeDataSet()
    {
        return (T)getSource();
    }

    /**
     * @return
     *      Признак что событие сформировано до действия
     */
    public boolean isBefore() {
        return before;
    }

    /**
     * @return
     *      Признак что событие сформировано после действия
     */
    public boolean isAfter() {
        return !before;
    }

}
