package ru.inversion.dataset.mark;


/** */
public class OARow {

    private Object data[];

    /** */
    public OARow( ) {
    }

    /** */
    public OARow( Object[] data ) {
        this.data = data;
    }

    /** */
    public OARow( int size ) {
        this.data = new Object[size];
    }

    /** */
    public <T> T get( int index ) {
        return this.data == null ? null : (T)this.data[index];
    }

    /** */
    public void set( int index, Object o ) {
        this.data[index] = o;
    }

    /** */
    public int length() {
        return this.data == null ? 0 : data.length;
    }

    /** */
    public void setData( Object data[] ) {
        this.data = data;
    }

    /** */
    public Object[] getData( ) {
        return this.data;
    }
}
