package ru.inversion.dataset.mark;


/**
 *
 * @author ssu
 */
public interface IIDMarkable extends IMarkable {

    /** {@inheritDoc} */
    @Override
    default Object getMarkID1( ) {
        return getMarkLongID();
    }

    /** */
    Long getMarkLongID( );
}
