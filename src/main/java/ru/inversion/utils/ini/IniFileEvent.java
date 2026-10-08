package ru.inversion.utils.ini;

import java.util.EventObject;

/** */
public class IniFileEvent extends EventObject {

    final public static String DEFAULT_SECTION = "#";

    /** */
    public enum Type {
        StartFile,
        Section,
        Parameter,
        Comment,
        Eof
    };

    final private Type type;
    final private String key;
    final private String value;

    private final int lineNo;
    private final String rawLine;

    /** */
    public IniFileEvent(Object source, Type type, String value, int lineNo, String rawLine) {
        super(source);
        this.type   = type;
        this.value  = value;
        this.key    = type.name();
        this.lineNo = lineNo;
        this.rawLine= rawLine;
    }

    /** */
    public IniFileEvent(Object source, String key, String value, int lineNo, String rawLine) {
        super(source);
        this.lineNo = lineNo;
        this.rawLine = rawLine;
        this.type = Type.Parameter;
        this.value = value;
        this.key = key;
    }

    /** */
    public Type type() {
        return type;
    }

    /** */
    public String value() {
        return value;
    }

    /** */
    public String key() {
        return key;
    }

    /** */
    public int lineNo() {
        return lineNo;
    }

    /** */
    public String rawLine() {
        return rawLine;
    }

    @Override
    public String toString() {
        return "IniFileEvent{" + "type=" + type + ", key='" + key + '\'' + ", value='" + value + '\'' + ", lineNo=" + lineNo + ", rawLine='" + rawLine + '\'' + '}';
    }
}
