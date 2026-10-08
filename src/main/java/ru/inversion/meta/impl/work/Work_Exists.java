package ru.inversion.meta.impl.work;

import ru.inversion.meta.DMLWorkParameterException;
import ru.inversion.meta.IEntityMetaData;
import ru.inversion.meta.IEntityProperty;
import ru.inversion.tc.TaskContext;
import ru.inversion.utils.Jt2St;
import ru.inversion.utils.Tags;

import java.util.List;

/**
 *
 * @author ssu
 */
public class Work_Exists<P> extends WorkBaseExists<P,P> {
    /** */
    public Work_Exists( IEntityMetaData<P> entityMetaData, TaskContext tc ) {
        super( entityMetaData, tc );
    }

    @Override
    public Boolean execute( P pojo ) {

        if( pojo == null )
            throw new IllegalArgumentException( Tags.PRODUCT_LABEL + "'pojo' is null");

        try {

            final List<IEntityProperty<P,?>> idList = requireIdList();

            for( IEntityProperty<P, ?> pk : idList )
            {
                Object o = pk.invokeGetter(pojo);
                dsLoad.set( tc.dialect().pkName(pk), Jt2St.convert(o) );
            }

            return !dsLoad.execute().isEmpty();
        }
        catch( Throwable th ) {
            throwOnWorkFailure( STAGE_EXECUTE, th );
            return null;
        }
    }
}
