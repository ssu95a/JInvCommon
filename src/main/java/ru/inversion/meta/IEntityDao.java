package ru.inversion.meta;

import ru.inversion.utils.Pair;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;

public interface IEntityDao<T, ID extends Serializable> {

    /** */
    T getById( ID id, boolean lock );

    /** */
    default T getById( ID id ) { return getById( id, false );}

    /** */
    default T get( T entity ) { return get( entity, false );}

    /** */
    T get( T entity, boolean lock );

    /** */
    Iterable<T> findAll( );

    /** */
    Iterable<T> findByExample( T exampleInstance, String orderBy,  Collection<String> propertyList );

    /** */
    Iterable<T> findByParameters( Map<String,?> parameters, String orderBy );

    /** */
    void insert( T entity );
    
    /** */
    void update( T entity );

    /** */
    void update( T entity, Map<String, Pair<Object,Object> > diffMap );

    /** */
    void delete( T entity );

    /** */
    void deleteById( ID id );

    /** */
    long count( Map<String, ?> parameters );

    /** */
    boolean existsById( ID id );
 
    /** */
    boolean exists( T entity );

    /** */
    ID getID( T entity );

    /** */
    void refresh( T entity );

    /** */
    void lock( T entity );

    /** */
    void lockById( ID id );

    /** Без исключения таймаута; возвращает false */
    default boolean tryLockById( ID id) {
        throw new UnsupportedOperationException("tryLockById");
    }

    /** */
    default void insertAll(Collection<T> items) {
        throw new UnsupportedOperationException("insertAll");
    }

    /** */
    default void updateAll(Collection<T> items){
        throw new UnsupportedOperationException("updateAll");
    }

    /** */
    default void deleteAll(Collection<T> items){
        throw new UnsupportedOperationException("deleteAll");
    }

    /** */
    default void deleteAllById(Collection<ID> ids){
        throw new UnsupportedOperationException("deleteAllById");
    }

    /** upsert */
    default T save(T entity){
        throw new UnsupportedOperationException("save");
    }
}