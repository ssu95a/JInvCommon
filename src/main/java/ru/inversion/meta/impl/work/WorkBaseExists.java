package ru.inversion.meta.impl.work;

import ru.inversion.db.dialect.SqlDialect;
import ru.inversion.meta.IEntityMetaData;
import ru.inversion.meta.IEntityProperty;
import ru.inversion.tc.TaskContext;

import java.util.List;

public abstract class WorkBaseExists<P,V> extends WorkBaseLoad<P,V,Boolean> {

    /** */
    protected WorkBaseExists( IEntityMetaData< P > entityMetaData, TaskContext tc ) {
        super(entityMetaData, tc, null );
    }

    @Override
    protected String prepareSql( Object param )
    {
        final SqlDialect dialect = tc.dialect();
        final StringBuilder   sb = new StringBuilder( );

        sb.append( "SELECT NULL FROM ").append( dialect.tableName( entityMetaData.getTableName() ) );

        final List< IEntityProperty< P, ? > > idList = requireIdList();

        int i = 0;

        for( IEntityProperty<P,?> pk : idList )
        {
            final String pkCol = dialect.columnName(pk);
            final String pkPrm = dialect.pkName(pk);

            sb.append( i++ ==0 ? " WHERE " : " AND ").append( pkCol ).append( " = :" ).append( pkPrm );
        }

        return sb.toString();
    }
}
