package ru.inversion.datacall;

import javax.persistence.Parameter;
import javax.persistence.ParameterMode;

import static javax.persistence.ParameterMode.*;
import static javax.persistence.ParameterMode.INOUT;

/** */
public interface ParameterMetadata<T> extends Parameter<T> {

    /** */
    Integer getSQLType();

    /** */
    ParameterMode getMode();

    /** */
    String getElementType();

    /** */
    T getDefaultValue();

    /** */
    boolean isLogValue();

    /** */
    boolean isReturnValue( );

    /** */
    default boolean isIn( ) {
        return getMode() == IN || getMode() == INOUT;
    }

    /** */
    default boolean isOut( ) {
        return getMode() == OUT || getMode() == INOUT;
    }

    /** */
    default Comparable<?> getIdentifier( ) {
        return getName() == null ? getPosition() : getName();
    }

    /** */
    default boolean isIndexed() {
        return getName() == null;
    }
}
