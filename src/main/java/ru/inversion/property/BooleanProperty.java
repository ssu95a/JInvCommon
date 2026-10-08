package ru.inversion.property;

/**
 *
 * @author ssu
 */
public class BooleanProperty extends Property<Boolean> {

    /** */
    public BooleanProperty( ) {
        this(false);
    }

    /** */
    public BooleanProperty( Boolean value ) {
        super(value);
    }

    /** */
    public BooleanProperty( boolean value ) {
        super(value);
    }
}
