package ru.inversion.meta.impl.work;

import ru.inversion.datacall.CallBuilder;
import ru.inversion.datacall.CallException;
import ru.inversion.db.dialect.SqlDialect;
import ru.inversion.meta.IEntityColumn;
import ru.inversion.meta.IEntityMetaData;
import ru.inversion.meta.IEntityProperty;
import ru.inversion.tc.TaskContext;
import ru.inversion.utils.Tags;

import java.util.Map;

import static javax.persistence.ParameterMode.IN;
import static javax.persistence.ParameterMode.OUT;
import static ru.inversion.parser.statement.LangEnum.PL;
import static ru.inversion.parser.statement.LangEnum.SQL;

/**
 * DML для обновления всей записи в БД
 * @author ssu
 */
public class Work_Update<P> extends WorkBaseDml<P,P,P> {

    /** */
    public Work_Update( IEntityMetaData<P> entityMetaData, TaskContext tc ) throws CallException {
        super( entityMetaData, tc );
    }
    
    /** */
    @Override
    protected void prepareCall( CallBuilder builder, Object ignored ) {

        final SqlDialect dialect = tc.dialect();
        final Map<String, IEntityProperty<P,?>> columnsMap = entityMetaData.getColumnsMap( );

        final StringBuilder sb  = new StringBuilder();
        sb.append( "UPDATE " )
          .append( dialect.tableName( entityMetaData.getTableName() ) )
          .append( "\n   SET\n" );

        int nCount = 0;
        
        for( IEntityProperty<P,?> p : columnsMap.values() ) {
            
            IEntityColumn<P,?> columnInfo = p.getColumnInfo();
            
            if( columnInfo.isUpdatable() ) {

                final String col = dialect.columnName( p );
                final String prm = dialect.inName(p);

                if( nCount > 0 )
                    sb.append(',');

                sb.append( col ).append(" = :").append( prm );
                
                builder.parameter( IN, prm, p.getColumnInfo().getSQLType(), null, p.getType(), null, true );

                nCount ++;
            }
        }//end for

        if( nCount == 0 )
            throw new IllegalStateException( Tags.PRODUCT_LABEL + "No updatable columns for entity " + entityMetaData.getName() );

        nCount = 0;

        for( IEntityProperty<P,?> pk : requireIdList() )
        {
            final String pkCol = dialect.columnName(pk);
            final String pkPrm = dialect.pkName(pk) ;

            sb.append( nCount++ == 0 ? "\nWHERE " : " AND ").append( pkCol ).append(" = :").append(pkPrm);
            builder.parameter( IN, pkPrm, pk.getColumnInfo().getSQLType(), null, pk.getType(), null, true );
        }

        final boolean useReturning = useReturningInto(dialect);

        builder.lang(SQL);

        if( dialect.returningMode() != SqlDialect.ReturningMode.NONE )
        {
            nCount = 0;
            StringBuilder into = new StringBuilder();

            for( IEntityProperty<P, ?> p : columnsMap.values() )
            {
                IEntityColumn<P, ?> c = p.getColumnInfo();

                if( !c.isReturningOnUpdate())
                    continue;

                /*
                    Игнорим запрет на апдейт, при установке из ретурн параметра
                    03.06.2024

                    String newName;

                    if( columnInfo.isUpdateable() )
                    {
                        newName = "OUT__" + p.getName();
                    }
                    else
                        newName = p.getName();
                */

                final String outCol = dialect.columnName(p);
                final String outPrm = dialect.outName(p);

                builder.parameter( OUT, outPrm, c.getSQLType(), null, p.getType(), null, true);

                if (nCount == 0) {
                    sb.append(" RETURNING ");
                    if (useReturning)
                        into.append(" INTO ");
                } else {
                    sb.append(',');
                    if (useReturning)
                        into.append(',');
                }

                sb.append(outCol);

                if (useReturning) {
                    into.append(':').append(outPrm);
                }

                nCount++;

            }//end for

            if( nCount > 0 && useReturning )
            {
                sb.append(into);
                sb.insert(0, "BEGIN \n");
                sb.append(';').append("\nEND;");

                builder.lang(PL);
            }
        }

        builder.sql( sb.toString() );
    }
}
