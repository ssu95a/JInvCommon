package ru.inversion.datacall;

import java.util.Iterator;

/** */
public interface IBatchLoader<T> extends AutoCloseable {
    /** */
    <B extends IBatchLoader<T>> B iterator ( Iterator<T> iter ) throws CallException;
    /** */
    <B extends IBatchLoader<T>> B execute ( ) throws CallException;
    /** */
    <B extends IBatchLoader<T>> B addBatchListener( IBatchListener l ) throws CallException;
    /** */
    <B extends IBatchLoader<T>> B removeBatchListener( IBatchListener l ) throws CallException;
}
