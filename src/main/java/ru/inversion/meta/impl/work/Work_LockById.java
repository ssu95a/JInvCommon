package ru.inversion.meta.impl.work;

import ru.inversion.db.dialect.SqlDialect;
import ru.inversion.meta.DMLWorkParameterException;
import ru.inversion.meta.IEntityMetaData;
import ru.inversion.meta.IEntityProperty;
import ru.inversion.tc.TaskContext;
import ru.inversion.utils.Jt2St;
import ru.inversion.utils.Tags;

import java.util.List;
import java.util.Map;

/**
 *
 * @author ssu
 */
public class Work_LockById<P,V> extends WorkBaseLock<P,V> {
    /** */
    public Work_LockById( IEntityMetaData<P> entityMetaData, TaskContext tc, String lt ) {
        super(entityMetaData, tc, lt );
    }
    /** */
    @Override
    public Boolean execute( V id ) {

        if( id == null )
            throw new IllegalArgumentException( Tags.PRODUCT_LABEL + "'id' is null");

        try {

            final SqlDialect dialect = tc.dialect();

            final List<IEntityProperty<P,?>> idList = requireIdList();

            if( idList.size() == 1 )
            {
                IEntityProperty<P,?> pk = idList.get(0);
                dsLoad.set( dialect.pkName(pk), Jt2St.convert(id) );
            }
            else
            {
                final Map<String,Object> mapId = (Map<String,Object>)id;
                for( IEntityProperty<P,?> e : idList )
                     dsLoad.set( dialect.pkName(e), mapId.get(e.getPropertyName() ));
            }

            return !dsLoad.execute().isEmpty();
        }
        catch( Throwable th ) {
            throwOnWorkFailure( STAGE_EXECUTE, th );
            return null;
        }
    }
}
