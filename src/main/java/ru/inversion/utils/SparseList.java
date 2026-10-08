package ru.inversion.utils;

import java.util.*;

public class SparseList <E> extends AbstractList<E> implements List<E>, RandomAccess {

    private static final Object DELETED = new Object();

    /** */
    private static class Range<E> {

        private int start;
        private List<E> data;
    }

    @Override
    public E get( int index ) {
        return null;
    }

    @Override
    public int size() {
        return 0;
    }
}
