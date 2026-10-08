package ru.inversion.utils.parser.text;

public final class TextRange {

    private final int start;
    private final int end;

    public TextRange(int start, int end)
    {
        if( start < 0 )
            throw new IllegalArgumentException("start < 0: " + start);

        if( end < start )
            throw new IllegalArgumentException( "end < start: [" + start + ", " + end + ")" );

        this.start = start;
        this.end   = end;
    }

    public int start( ) { return start; }

    public int end() { return end; }

    public int length() { return end - start; }

    public boolean isEmpty() { return start == end; }

    /** */
    public boolean contains(int offset) {
        return start <= offset && offset < end;
    }

    /** */
    public boolean contains(TextRange other) {

        if( other == null )
            throw new IllegalArgumentException("other is null");

        return start <= other.start && other.end <= end;
    }

    /**
     * Проверяет наличие непустого общего участка.
     */
    public boolean intersects(TextRange other) {

        if (other == null)
            throw new IllegalArgumentException("other is null");

        return start < other.end && other.start < end;
    }

    /**
     * Возвращает общий диапазон или null, если пересечения нет.
     */
    public TextRange intersection(TextRange other) {

        if( !intersects(other) )
            return null;

        return new TextRange( Math.max(start, other.start), Math.min(end, other.end) );
    }

    /**
     * Диапазоны соприкасаются границами, но не пересекаются.
     */
    public boolean isAdjacentTo(TextRange other) {
        if( other == null )
            throw new IllegalArgumentException("other is null");

        return end == other.start || other.end == start;
    }


    @Override
    public String toString() {
        return "[" + start + ", " + end + ")";
    }

    @Override
    public boolean equals(Object object) {

        if( this == object )
            return true;

        if (!(object instanceof TextRange))
            return false;

        TextRange other = (TextRange) object;

        return start == other.start && end == other.end;
    }

    @Override
    public int hashCode() {
        int result = start;
        result = 31 * result + end;
        return result;
    }
}