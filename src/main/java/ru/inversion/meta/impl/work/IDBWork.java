package ru.inversion.meta.impl.work;

/** */
@FunctionalInterface
public interface IDBWork<V,R> extends AutoCloseable {
    /** */
    R execute( V value ) throws Exception;
    /** */
    default void close(){}

    public final static IDBWork EMPTY = new IDBWork() {
        @Override
        public Object execute( Object value ) {
            return null;
        }
    };
}
