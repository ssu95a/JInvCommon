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
 * Загрузка экземпляра сущности по значению первичного ключа
 * @author ssu
 */
public class Work_ExistsById<P,V> extends WorkBaseExists<P,V> {
    /** */
    public Work_ExistsById( IEntityMetaData<P> entityMetaData, TaskContext tc ) {
        super( entityMetaData, tc );
    }
    /** */
    @Override
    public Boolean execute( V id ) {

        try {

            if( id == null )
                throw new IllegalArgumentException( Tags.PRODUCT_LABEL + "'id' is null");

            final List<IEntityProperty<P,?>> idList = requireIdList();
            final SqlDialect dialect = tc.dialect();

            if( idList.size() == 1 )
            {
                IEntityProperty<P,?> pk = idList.get(0);
                dsLoad.set( dialect.pkName(pk), Jt2St.convert(id) );
            }
            else
            {
                final Map<String, Object> mapId = (Map<String, Object>) id;

                for( IEntityProperty<P,?> pk : idList )
                     dsLoad.set( dialect.pkName(pk), Jt2St.convert( mapId.get(pk.getPropertyName()) ) );
            }
            return !dsLoad.execute().isEmpty();
        }
        catch( Throwable th ) {
            throwOnWorkFailure( STAGE_EXECUTE, th );
            return null;
        }
    }
}
