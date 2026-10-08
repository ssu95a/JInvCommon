package ru.inversion.utils.js;

import java.util.EventObject;

public class ScriptManEvent extends EventObject {

    /** */
    public enum Type {
        COMPILE,
        EXECUTE,
        OUTDATED
    }

    final private boolean before;

    final private Object scriptId;

    final private Type type;

    /** */
    public ScriptManEvent( Object source, Object scriptId, Type type, boolean b ) {
        super(source);
        this.type = type;
        this.before = b;
        this.scriptId = scriptId;
    }
    /** */
    public boolean isAfter() {
        return !before;
    }
    /** */
    public boolean isBefore() {
        return before;
    }
    /** */
    public Object getScriptId() {
        return scriptId;
    }
    /** */
    public Type getType() {
        return type;
    }
}
