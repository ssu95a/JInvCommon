package ru.inversion.meta.impl.work;

import ru.inversion.datacall.CallException;
import ru.inversion.meta.IEntityMetaData;
import ru.inversion.tc.TaskContext;

/**
 *
 * @author ssu
 */
public class Work_Delete<P> extends WorkBaseDelete<P,P>  {
    /** */
    public Work_Delete( IEntityMetaData<P> entityMetaData, TaskContext tc ) throws CallException {
        super(entityMetaData,tc);
    }
}
