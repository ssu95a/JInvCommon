package ru.inversion.tds;


import java.util.function.Function;

/**
 *
 * by Swing TreePath impl.
 */
public interface ITreeDataSetPath<P, T extends ITreeDataSetItem<P>> {

    /** */
    T getFirstItem();

    /** */
    T getLastItem();

    /** */
    T getItem(int nItem);

    /** */
    Iterable<T> items(boolean reverse);

    /** */
    ITreeDataSetPath<P,T> getParentPath();

    /** */
    int getPathCount();

    /** */
    String toString( Function<P,String> titleExtractor, String dlmtr );
}
