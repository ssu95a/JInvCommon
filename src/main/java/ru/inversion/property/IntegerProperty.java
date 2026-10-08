package ru.inversion.property;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public class IntegerProperty extends Property<Integer> implements IntSupplier {

    public IntegerProperty() {
    }

    public IntegerProperty( Integer value ) {
        super(value);
    }

    public IntegerProperty( int value ) {
        super(value);
    }

    @Override
    public int getAsInt() { return getValue(); }
}
