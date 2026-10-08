package ru.inversion.meta.impl.work;

import ru.inversion.datacall.CallBuilder;
import ru.inversion.db.dialect.SqlDialect;
import ru.inversion.db.dialect.SqlDialectFactory;
import ru.inversion.meta.IEntityColumn;
import ru.inversion.meta.IEntityMetaData;
import ru.inversion.meta.IEntityProperty;
import ru.inversion.tc.TaskContext;
import ru.inversion.utils.S;
import ru.inversion.utils.Tags;

import java.util.Map;

import static javax.persistence.ParameterMode.IN;
import static javax.persistence.ParameterMode.OUT;
import static ru.inversion.parser.statement.LangEnum.PL;
import static ru.inversion.parser.statement.LangEnum.SQL;

/**
 *
 * @author sulimoff
 */
public class Work_Insert<P> extends WorkBaseDml<P,P,P> {

    /** */
    public Work_Insert( IEntityMetaData<P> entityMetaData, TaskContext tc ) {
        super( entityMetaData, tc );
    }

    /** */
    @Override
    protected void prepareCall( CallBuilder builder, Object unused )
    {
        final SqlDialect dialect = tc.dialect();

        final Map<String, IEntityProperty<P,?>> columnsMap = entityMetaData.getColumnsMap( );

        final String tableSql = dialect.tableName( entityMetaData.getTableName() );

        final StringBuilder cols = new StringBuilder(100);
        final StringBuilder vals = new StringBuilder(100);

        cols.append("INSERT INTO ").append(tableSql).append("(");
        vals.append(" VALUES (");

        {
            int insCount = 0;

            for( IEntityProperty<P, ?> p : columnsMap.values() ) {

                final IEntityColumn<P, ?> c = p.getColumnInfo();

                if (!c.isInsertable())
                    continue;

                if (insCount > 0) {
                    cols.append(',');
                    vals.append(',');
                }

                // column
                final String col = dialect.columnName( p );
                cols.append( col );

                // parameter
                final String prm = dialect.inName(p);
                builder.parameter( IN, prm, c.getSQLType(), null, p.getType(), null, true );

                if( S.isNullOrEmpty(c.getSequenceName()) )
                    vals.append(':').append( prm );
                else
                    vals.append("COALESCE(:").append(prm).append(", ").append( dialect.sequenceNextVal(c.getSequenceName())).append(")" );

                insCount++;

            }//end for

            if( insCount == 0 )
                throw new IllegalStateException( Tags.PRODUCT_LABEL + "No insertable columns for entity " + entityMetaData.getName() );
        }

        cols.append(")");
        vals.append(")");

        final StringBuilder sql = new StringBuilder(250);
        sql.append(cols).append(vals);

        int retCount = 0;

        final StringBuilder returning = new StringBuilder();
        final StringBuilder into      = new StringBuilder();

        final boolean useReturning = useReturningInto(dialect);

        if( dialect.returningMode() != SqlDialect.ReturningMode.NONE )
        {
            for( IEntityProperty<P, ?> p : columnsMap.values() )
            {
                final IEntityColumn<P, ?> c = p.getColumnInfo();
                if( !c.isReturningOnInsert() )
                    continue;

                final String outPrm = dialect.outName( c.getName() ); // "OUT__" + inName

                builder.parameter( OUT, outPrm, c.getSQLType(), null, p.getType(), null, true );

                if( retCount == 0)
                {
                    returning.append(" RETURNING ");

                    if( useReturning )
                        into.append(" INTO ");
                }
                else
                {
                    returning.append(',');

                    if( useReturning )
                        into.append(',');
                }

                returning.append( dialect.columnName( p ) );

                if( useReturning )
                    into.append(':').append(outPrm);

                retCount++;
            }//end for
        }

        if (retCount > 0)
        {
            sql.append(returning);

            if( useReturning ) {
                sql.append(into);
                sql.insert(0, "BEGIN \n");
                sql.append(';').append("\nEND;");
                builder.lang(PL);
            }
            else
                builder.lang(SQL);
        }
        else
        {
            builder.lang(SQL);
        }

        builder.sql(sql.toString());
    }
}
