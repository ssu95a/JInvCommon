package ru.inversion.meta.impl.work;

import ru.inversion.db.dialect.SqlDialect;
import ru.inversion.meta.IEntityMetaData;
import ru.inversion.meta.IEntityProperty;
import ru.inversion.tc.TaskContext;
import ru.inversion.utils.S;
import ru.inversion.utils.Tags;

import java.util.List;

/** */
public abstract class WorkBaseLock<P,V> extends WorkBaseLoad<P,V,Boolean> {

    public WorkBaseLock( IEntityMetaData<P> entityMetaData, TaskContext tc, Object param ) {
        super( entityMetaData, tc, param );
    }

    @Override
    protected String prepareSql( Object param )
    {
        final SqlDialect dialect = tc.dialect();

        final String lockTable = S.isNullOrEmpty( (String)param ) ? entityMetaData.getTableName() : (String)param;

        StringBuilder sb = new StringBuilder( );
        sb.append( "SELECT NULL\nFROM ").append( dialect.tableName(lockTable) );

        final List< IEntityProperty< P, ? > > idList = requireIdList();

        int i = 0;

        for( IEntityProperty<P,?> pk : idList )
        {
            final String pkCol = dialect.columnName(pk);
            final String pkPrm = dialect.pkName(pk);

            sb.append(i++ == 0 ? "\n WHERE " : " AND ").append(pkCol).append(" = :").append(pkPrm);
        }

        sb.append( dialect.lockClause(1) );

        return sb.toString();
    }
}
