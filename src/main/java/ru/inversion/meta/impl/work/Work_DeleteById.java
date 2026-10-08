package ru.inversion.meta.impl.work;

import ru.inversion.datacall.CallException;
import ru.inversion.db.dialect.SqlDialect;
import ru.inversion.meta.DMLWorkException;
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
public class Work_DeleteById<P,V> extends WorkBaseDelete<P,V>  {

    /** */
    public Work_DeleteById( IEntityMetaData<P> entityMetaData, TaskContext tc ) throws CallException {
        super(entityMetaData,tc);
    }


    /** */
    @Override
    public Boolean execute( V id )
    {
        try {

            if( id == null )
                throw new IllegalArgumentException( "'id' is null" );

            final SqlDialect dialect = tc.dialect();
            final List<IEntityProperty<P,?>> idList = requireIdList();

            if( idList.size() == 1 )
            {
                IEntityProperty<P,?> pk = idList.get(0);
                call.set( dialect.pkName(pk), Jt2St.convert(id) );
            }
            else
            {
                final Map<String,Object> mapId = (Map<String,Object>)id;
                for( IEntityProperty<P,?> pk : idList )
                     call.set( dialect.pkName(pk), Jt2St.convert(mapId.get(pk.getPropertyName()) ) );
            }

            call.execute();

            return Boolean.TRUE;
        }
        catch( Throwable th ) {
            throwOnWorkFailure( STAGE_EXECUTE, th );
            return null;
        }
    }
}
