package ru.inversion.meta.impl.work;

import ru.inversion.datacall.CallBuilder;
import ru.inversion.db.dialect.SqlDialect;
import ru.inversion.meta.IEntityMetaData;
import ru.inversion.meta.IEntityProperty;
import ru.inversion.parser.statement.LangEnum;
import ru.inversion.tc.TaskContext;

import java.util.List;

import static javax.persistence.ParameterMode.IN;
import static ru.inversion.parser.statement.LangEnum.SQL;

/**
 *
 * @author ssu
 */
public class WorkBaseDelete<P,V> extends WorkBaseDml<P,V,Boolean>  {
    /** */
    public WorkBaseDelete( IEntityMetaData<P> entityMetaData, TaskContext tc ) {
        super(entityMetaData,tc);
    }


    /** */
    protected void prepareCall( CallBuilder builder, Object unused )
    {
        final SqlDialect dialect = tc.dialect();
        final StringBuilder   sb = new StringBuilder( );

        sb.append( "DELETE\n  FROM ").append( dialect.tableName( entityMetaData.getTableName() ) );

        int i = 0;

        for( IEntityProperty<P,?> pk : requireIdList() )
        {
            final String pkCol = dialect.columnName(pk);
            final String pkPrm = dialect.pkName(pk);

            sb.append( i++ == 0 ? "\n WHERE " : " AND ").append(pkCol).append(" = :").append(pkPrm);
            builder.parameter(IN, pkPrm, pk.getColumnInfo().getSQLType(), null, pk.getType(), null, true);
        }

        builder.sql(sb.toString()).lang(LangEnum.SQL);
    }
}
