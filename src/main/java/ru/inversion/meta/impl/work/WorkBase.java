package ru.inversion.meta.impl.work;

import ru.inversion.db.dialect.SqlDialect;
import ru.inversion.meta.DMLWorkException;
import ru.inversion.meta.IEntityMetaData;
import ru.inversion.meta.IEntityProperty;
import ru.inversion.tc.TaskContext;
import ru.inversion.utils.Tags;

import java.util.List;
import java.util.Objects;

public abstract class WorkBase<P,V,R> implements IDBWork<V,R>{

    protected static final String STAGE_PREPARE = "prepare";
    protected static final String STAGE_EXECUTE = "execute";
    protected static final String STAGE_SET_PARAM = "set-param";
    protected static final String STAGE_GET_OUT = "get-out";
    protected static final String STAGE_MAP_PARAMS = "map-params";

    /** */
    protected final IEntityMetaData<P> entityMetaData;

    /** */
    protected final TaskContext tc;

    /** */
    protected WorkBase( IEntityMetaData<P> entityMetaData, TaskContext tc, Object param ) {
        this.entityMetaData = Objects.requireNonNull( entityMetaData, "entityMetaData" );
        this.tc             = Objects.requireNonNull( tc, "tc" );
    }

    /** */
    protected WorkBase( IEntityMetaData<P> entityMetaData, TaskContext tc ) {
        this(entityMetaData,tc,null);
    }

    /** */
    abstract protected String workName();

    /** */
    protected final void throwOnWorkFailure( String stage, Throwable cause )
    {
        DMLWorkException.throwOnWorkFailure (
            getClass().getSimpleName(),
            workName(),
            entityMetaData.getEntityClass().getName(),
            tc.dialect().type().name(),
            stage,
            cause
        );
    }

    /** */
    protected final List<IEntityProperty<P, ?>> requireIdList() {

        List<IEntityProperty<P, ?>> idList = entityMetaData.getIDList();

        if( idList == null || idList.isEmpty() )
            throw new IllegalStateException( Tags.PRODUCT_LABEL + "PK list is empty for entity " + entityMetaData.getEntityClass().getName() );

        return idList;
    }

    /** */
    protected boolean useReturningInto(SqlDialect d) {
        return d.type() == SqlDialect.DialectId.ORACLE && d.returningMode() != SqlDialect.ReturningMode.NONE;
    }

}
