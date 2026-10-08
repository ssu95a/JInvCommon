package ru.inversion.meta.impl;

import ru.inversion.meta.DAOException;
import ru.inversion.meta.IEntityDao;
import ru.inversion.meta.IEntityMetaData;
import ru.inversion.meta.IEntityProperty;
import ru.inversion.meta.impl.work.*;
import ru.inversion.tc.TaskContext;
import ru.inversion.utils.Pair;
import ru.inversion.utils.Tags;
import ru.inversion.utils.U;

import javax.persistence.NamedStoredProcedureQueries;
import javax.persistence.NamedStoredProcedureQuery;
import javax.persistence.QueryHint;
import java.io.Serializable;
import java.sql.SQLException;
import java.util.*;

import static ru.inversion.meta.EntityConstants.*;

/**
 * <h6>Базовая impl DAO для экземпляров Entity</h6>
 * @author Sulimoff
 */
public class EntityDao<P,ID extends Serializable> implements IEntityDao<P,ID> {

    final private IEntityMetaData<P> metaData;
    final private TaskContext        taskContext;

    private IDBWork insertWork,
                    updateWork,
                    deleteWork,
                    deleteByIdWork,
                    lockWork,
                    lockByIdWork,
                    loadByIDWork,
                    loadByIDWork_lock,
                    loadByPojoWork,
                    loadByPojoWork_lock,
                    existsWork,
                    existsByIdWork;
    /** */
    public EntityDao( IEntityMetaData<P> metaData, TaskContext tc ) {
        this.metaData    = Objects.requireNonNull(metaData, "'metaData' is null");
        this.taskContext = Objects.requireNonNull(tc, "'tc' is null");;
    }
    
    /** */
    @Override
    public P getById( ID id, boolean lock ) {

        if( id == null )
            return null;
        
        try {

            if( lock )
                lockById( id );

            if( loadByIDWork == null )
                loadByIDWork = new Work_LoadById<P,ID>( metaData, taskContext, true );

            return (P)loadByIDWork.execute(id);
        }
        catch( Exception ex ) {
            DAOException.throwOnLoadInstance( "getById", metaData.getEntityClass(), ex );
        }
        return null;
    }

    /** */
    @Override
    public P get( P entity, boolean lock) {

        if( entity == null )
            return null;
        
        try {

            if( lock )
                lock( entity );

            if( loadByPojoWork == null )
                loadByPojoWork = new Work_Load<P>( metaData, taskContext, false );

            return (P)loadByPojoWork.execute(entity);
        }
        catch( Exception ex ) {
            DAOException.throwOnLoadInstance( "get.entity", metaData.getEntityClass(), ex );
        }
        return null;
    }

    /** */
    @Override
    public Iterable<P> findAll( ) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    /** */
    @Override
    public Iterable<P> findByExample( P exampleInstance, String orderBy, Collection<String> propertyList ) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /** */
    @Override
    public Iterable<P> findByParameters( Map<String,?> parameters, String orderBy) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    /** */
    @Override
    public void insert( P entity ) {
        
        if( entity == null )
            return;
        
        try {

            if( insertWork == null ) {

                NamedStoredProcedureQueries nspqs = metaData.getEntityClass().getAnnotation(NamedStoredProcedureQueries.class);

                if( nspqs != null ) {

                    for( NamedStoredProcedureQuery n : nspqs.value() ) {
                         if( INSERT.equals(n.name()) ) {
                             insertWork = new Work_DMLByNamedStored<>( metaData, taskContext, n );
                             break;
                         }
                    }
                }

                if( insertWork == null )
                    insertWork = new Work_Insert<>( metaData, taskContext );
            }

            insertWork.execute(entity);
        }
        catch( Exception ex ) {
            DAOException.throwOnOperation( INSERT, ex );
        }
    }

    /** */
    @Override
    public void update( P entity) {

        if( entity == null )
            return;
        
        try {

            if( updateWork == null ) {

                NamedStoredProcedureQueries nspqs = metaData.getEntityClass().getAnnotation(NamedStoredProcedureQueries.class);

                if( nspqs != null ) {

                    for(NamedStoredProcedureQuery n : nspqs.value()) {
                        if( UPDATE.equals( n.name() ) ) {
                            updateWork = new Work_DMLByNamedStored<>( metaData, taskContext, n );
                            break;
                        }
                    }
                }

                if( updateWork == null )
                    updateWork = new Work_Update<>( metaData, taskContext );
            }
            updateWork.execute(entity);
        }
        catch( Exception ex ) {
            DAOException.throwOnOperation( UPDATE, ex );
        }
    }

    /** */
    @Override
    public void delete( P entity) {
        
        if( entity == null )
            return;
        
        try {

            if( deleteWork == null ) {

                NamedStoredProcedureQueries nspqs = metaData.getEntityClass().getAnnotation( NamedStoredProcedureQueries.class );

                if( nspqs != null )
                {
                    for( NamedStoredProcedureQuery n : nspqs.value() )
                    {
                        if( DELETE.equals( n.name() ) ) {
                            deleteWork = new Work_DMLByNamedStored<>(metaData, taskContext, n);
                            break;
                        }
                    }
                }

                if( deleteWork == null )
                    deleteWork = new Work_Delete<>( metaData, taskContext );
            }

            deleteWork.execute(entity);
        }
        catch( Exception ex ) {
            DAOException.throwOnOperation( DELETE, ex );
        }
    }

    /** */
    @Override
    public void deleteById( ID id) {
        
        if( id == null )
            return;
        
        try {

            if( deleteByIdWork == null )
                deleteByIdWork = new Work_DeleteById<>( metaData, taskContext );

            deleteByIdWork.execute(id);
        }
        catch( Exception ex ) {
            DAOException.throwOnOperation( "deleteById", ex );
        }
    }

    /** */
    @Override
    public long count( Map<String, ?> parameters) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    /** */
    @Override
    public boolean existsById( ID id ) {

        if( id == null )
            return false;

        try {

            if( existsByIdWork == null )
                existsByIdWork = new Work_ExistsById<>(metaData, taskContext);

            return (Boolean)existsByIdWork.execute(id);
        }
        catch( Exception ex ) {
            DAOException.throwOnOperation( "existsById", ex );
        }
        return false;
    }

    /** */
    @Override
    public boolean exists( P entity ) {

        if( entity == null )
            return false;

        try {

            if( existsWork == null )
                existsWork = new Work_Exists<>(metaData, taskContext);

            return (Boolean)existsWork.execute(entity);
        }
        catch( Exception ex ) {
            DAOException.throwOnOperation( "exists", ex );
        }
        return false;
    }
    
    /** */
    @Override
    public ID getID( P entity ) {

        if( entity == null )
            return null;

        ID id;

        final List< IEntityProperty< P, ? > > idList = this.metaData.getIDList();

        if( idList.size() == 1 )
            id = (ID)idList.get(0).invokeGetter(entity);
        else
        {
            Map<String,Object> v = new LinkedHashMap<>();

            for( IEntityProperty<P,?> p : idList )
                 v.put( p.getPropertyName(), p.invokeGetter(entity) );

            if( v.isEmpty() )
                throw new IllegalStateException( Tags.PRODUCT_LABEL + "Entity " + metaData.getEntityClass().getName() + " has no ID properties.");

            id = (ID)v;
        }

        return id;
    }

    @Override
    public void refresh( P entity ) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }


    /** */
    static boolean is_ORA_30006_InitialException(Throwable ex) {

        while( ex != null )
        {
            if( ex instanceof SQLException )
            {
                SQLException sqlEx = (SQLException) ex;

                if( sqlEx.getErrorCode() == 30006)
                    return true;
            }

            ex = ex.getCause();
        }

        return false;
    }

    private String lockTable;

    @Override
    public void lock( P entity ) {

        if( entity == null )
            return;

        try {

            if( lockWork == null )
            {

                NamedStoredProcedureQueries nspqs = metaData.getEntityClass().getAnnotation( NamedStoredProcedureQueries.class );

                if( nspqs != null )
                {
                    for( NamedStoredProcedureQuery n : nspqs.value() )
                    {
                        if( LOCK.equals(n.name()) )
                        {
                            if( LOCK_MODE_NONE.equals( n.procedureName() ) )
                                lockWork = IDBWork.EMPTY;
                            else if( LOCK_MODE_TABLE.equals( n.procedureName() ) )
                            {
                                final QueryHint[] hints = n.hints();

                                if( hints != null && hints.length > 0 )
                                {
                                    for( QueryHint hint : hints )
                                    {
                                        if( LOCK_TABLE.equals( hint.name() ) ) {
                                            lockTable = hint.value();
                                            break;
                                        }
                                    }
                                }
                            }
                            else
                                lockWork = new Work_DMLByNamedStored<>( metaData, taskContext, n );
                        }
                    }//end for
                }

                if( lockWork == null )
                    lockWork = new Work_Lock<P>( metaData, taskContext, this.lockTable );
            }

            lockWork.execute( entity );
        }
        catch( Exception ex )
        {
            if( is_ORA_30006_InitialException(ex) )
                DAOException.throwLockTimeoutException( ex, entity, lockTable );
            else
                DAOException.throwOnOperation( LOCK, ex );
        }
    }

    /** */
    @Override
    public void lockById( ID id ) {

        if( id == null )
            return;

        try {

            if( lockByIdWork == null )
                lockByIdWork = new Work_LockById<>( metaData, taskContext, null );

            lockByIdWork.execute( id );
        }
        catch( Exception ex ) {
            if( is_ORA_30006_InitialException(ex) )
                DAOException.throwLockTimeoutException( ex, id, null );
            else
                DAOException.throwOnOperation( "lockByIDWork", ex );
        }
    }

    private Boolean hasUpdateNamedStored = null;

    /** */
    @Override
    public void update( P entity, Map<String, Pair<Object,Object> > diffMap ) {

        if( U.containsNull( entity, diffMap ) || diffMap.isEmpty() )
            return;

        try {

            if( hasUpdateNamedStored == null ) {

                NamedStoredProcedureQueries nspqs = metaData.getEntityClass().getAnnotation( NamedStoredProcedureQueries.class );

                if( nspqs != null )
                {
                    for( NamedStoredProcedureQuery n : nspqs.value() )
                    {
                        if( UPDATE.equals( n.name()) ) {
                            hasUpdateNamedStored = Boolean.TRUE;
                            break;
                        }
                    }
                }

                if( hasUpdateNamedStored == null )
                    hasUpdateNamedStored = Boolean.FALSE;
            }

            if( !hasUpdateNamedStored )
            {
                try( Work_UpdateDiff work = new Work_UpdateDiff( metaData, entity, diffMap, taskContext ) ) {
                     work.execute(null);
                }
            }
            else
                update( entity );
        }
        catch( Exception ex ) {
            DAOException.throwOnOperation( "EntityDao.updateDiff", ex );
        } 
    }
}
