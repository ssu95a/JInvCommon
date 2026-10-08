package ru.inversion.utils.bits;

public class Bits32 extends BitsBase {

    private int bits;

    public Bits32( ) {
        this.bits = 0;
    }

    public Bits32( int bits ) {
        this.bits = bits;
    }

    @Override
    public int getSize() {
        return 32;
    }

    @Override
    protected boolean doGet( int index ) {
        return ((bits & (1L << index)) != 0);
    }

    @Override
    protected void doSet( int index, boolean v ) {
        if( v )
            bits |= (1 << index);
        else
            bits &= ~(1 << index);
    }
    @Override
    public void clear() {bits = 0;}

    @Override
    public int intValue() { return bits; }
    @Override
    public long longValue() { return bits; }
    @Override
    public float floatValue() { return bits; }
    @Override
    public double doubleValue() { return bits; }
    @Override
    public byte byteValue() { return (byte)bits; }
    @Override
    public short shortValue() {return (short)bits;}
}
