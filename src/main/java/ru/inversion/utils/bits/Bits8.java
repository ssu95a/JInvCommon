package ru.inversion.utils.bits;

public class Bits8 extends BitsBase {

    private byte bits;

    public Bits8( ) {
        this.bits = 0;
    }

    public Bits8( byte bits ) {
        this.bits = bits;
    }
    public Bits8( String of ) {
        super(of);
    }
    public Bits8( char[] of ) {
        super(of);
    }

    @Override
    public int getSize() {
        return 8;
    }

    @Override
    public void clear() {bits = 0;}

    @Override
    protected boolean doGet( int index ) {
        return ((bits & (1 << index)) != 0);
    }

    @Override
    protected void doSet( int index, boolean v ) {

        if( v )
            bits |= (byte)(1 << index);
        else
            bits &= (byte)~(1 << index);
    }

    @Override
    public int intValue() { return bits; }
    @Override
    public long longValue() { return bits; }
    @Override
    public float floatValue() { return bits; }
    @Override
    public double doubleValue() { return bits; }
    @Override
    public byte byteValue() { return bits; }
}
