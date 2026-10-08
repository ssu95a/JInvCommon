package ru.inversion.utils.parser.text;

import java.util.Objects;

/**
 * <h5>Неизменяемое представление всего разбираемого текста.</h5>
 */
public final class SourceText implements CharSequence {

    public static final int EOF = -1;

    private final String text;

    public SourceText(CharSequence source) {
        Objects.requireNonNull(source, "source");
        this.text = source instanceof String ? (String) source : source.toString();
    }

    @Override
    public int length() {
        return text.length();
    }

    @Override
    public char charAt(int index) {
        return text.charAt(index);
    }

    /** */
    public int get(int index)
    {
        return index >= 0 && index < text.length() ? text.charAt(index) : EOF;
    }

    /** */
    public int peek(int offset, int distance) {
        return get(offset + distance);
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        return text.subSequence(start, end);
    }

    /** */
    public String substring(int start, int end) {
        return text.substring(start,end);
    }

    /** */
    public void appendTo( StringBuilder target, int start, int end )
    {
        target.append(text, start, end);
    }

    /** */
    public String around(int start, int end, int radius) {
        int excerptStart = Math.max(0, start - radius);
        int excerptEnd = Math.min(text.length(), end + radius);

        return text.substring(excerptStart, excerptEnd);
    }

    @Override
    public String toString() {
        return text;
    }
}