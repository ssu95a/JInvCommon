package ru.inversion.meta.impl.work;

import ru.inversion.datacall.*;
import ru.inversion.db.dialect.SqlDialect;
import ru.inversion.meta.DMLWorkParameterException;
import ru.inversion.meta.IEntityColumn;
import ru.inversion.meta.IEntityMetaData;
import ru.inversion.meta.IEntityProperty;
import ru.inversion.tc.TaskContext;
import ru.inversion.utils.Pair;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static javax.persistence.ParameterMode.IN;
import static javax.persistence.ParameterMode.OUT;
import static ru.inversion.parser.statement.LangEnum.PL;
import static ru.inversion.parser.statement.LangEnum.SQL;

/**
 * DML для обновления всей записи в БД
 *
 * @author sulimoff
 */
public class Work_UpdateDiff<P> extends WorkBase <P,P,P> {

    final private P pojo;
    final protected IDataCall call;

    private final List<Pair<IEntityProperty<P,?>,String>> propertyRetList = new ArrayList<>();

    /** */
    public Work_UpdateDiff( IEntityMetaData<P> entityMetaData, P pojo, Map<String, Pair<Object,Object> > diff, TaskContext tc )
           throws CallException
    {
        super( entityMetaData, tc );

        if( diff == null || diff.isEmpty() )
        {
            System.out.println("diffMap is empty");

            this.pojo = null;
            this.call = null;

            return;
        }

        this.pojo = pojo;

        final SqlDialect  dialect     = tc.dialect( );
        final CallBuilder callBuilder = CallBuilder.NEW( );

        // Значения устанавливаемых параметров
        final Map<String,Object> propertyValues = new LinkedHashMap<>();

        StringBuilder sb = new StringBuilder();
        sb.append( "UPDATE " )
          .append( dialect.tableName( entityMetaData.getTableName() ) )
          .append( "\n   SET\n" );

        int nCount = 0;

        for( Map.Entry<String,Pair<Object,Object>> e : diff.entrySet() )
        {
            IEntityProperty<P,?> property = entityMetaData.getProperty( e.getKey() );

            if( property == null )
                continue;

            IEntityColumn<P,?> columnInfo = property.getColumnInfo();

            Object  value    = e.getValue().second;
            boolean useProxy = false;

            if( columnInfo == null ) {

                property = property.getProxyFor();

                if( property != null ) {
                    columnInfo = property.getColumnInfo();
                    useProxy = true;
                }
            }

            if( columnInfo != null )
            {
                if( columnInfo.isUpdatable() )
                {
                    final String col = dialect.columnName(property);
                    final String prm = dialect.inName    (property);

                    sb.append( col).append(" = :").append( prm ).append(",\n");

                    callBuilder.parameter( IN, prm, columnInfo.getSQLType(), null, property.getType(), null, true );

                    if( useProxy )
                        value = property.invokeGetter(pojo);

                    propertyValues.put( prm, value );

                    nCount ++;
                }//end if
            }//end if
        }//end for

        if( nCount == 0 )
        {
            // System.out.println("diffMap is empty");
            this.call = null;
            return;
        }

        if( sb.length() >= 2 )
            sb.setCharAt( sb.length() - 2, ' ' );

        sb.append( "WHERE " );

        nCount = 0;

        // установка значений PK
        for( IEntityProperty<P,?> e : requireIdList() )
        {
            final boolean pkChanged = diff.containsKey(e.getPropertyName());

            final String pkCol = dialect.columnName(e);
            final String pkPrm = dialect.pkName(e);

            if( nCount > 0 )
                sb.append(" AND ");

            sb.append( pkCol ).append(" = :").append( pkPrm );

            // если поменяли значение PK — во WHERE ставим СТАРОЕ значение
            final Object pkValue = pkChanged ? diff.get(e.getPropertyName()).first : e.invokeGetter(pojo);

            callBuilder.parameter( IN, pkPrm, e.getColumnInfo().getSQLType(), null, e.getType(), null, true);

            propertyValues.put( pkPrm, pkValue );

            nCount++;
        }

        nCount = 0;

        StringBuilder sbInto = new StringBuilder();
        final Map<String, IEntityProperty<P,?>>columnsMap = entityMetaData.getColumnsMap( );

        // Возвращаемые значения
        for( IEntityProperty<P,?> p : columnsMap.values()  )
        {
            IEntityColumn<P,?> columnInfo = p.getColumnInfo();

            if( columnInfo.isReturningOnUpdate()  ) {

                final String outPrm = dialect.outName(p);

                callBuilder.parameter( OUT, outPrm, columnInfo.getSQLType(), null, p.getType(), null, true );

                if( nCount == 0 )
                {
                    sb.append( " RETURNING " );
                    sbInto.append(" INTO ");
                }
                else
                {
                    sb.append(','); sbInto.append(',');
                }

                sb.append( dialect.columnName(p) );
                sbInto.append(':').append( outPrm );

                propertyRetList.add( Pair.makePair( p, outPrm ) );

                nCount ++;
            }
        }//end for

        if( nCount > 0 ) {

            if( useReturningInto(dialect) ) {
                sb.append(sbInto);
                sb.insert( 0, "BEGIN \n");
                sb.append(';').append("\nEND;");
                callBuilder.lang( PL );
            }
            else
                callBuilder.lang( SQL );
        }
        else
            callBuilder.lang( SQL );

        callBuilder.sql( sb.toString() );

        call = SQLCallBuilder.NEW(tc).callStatement( (p)-> callBuilder.build() ).build( );

        propertyValues.forEach( call::set );
    }

    /** */
    @Override
    public P execute( P unused ) {

        if( call != null ) {

            try {

                call.execute();

                int i = 0;

                for( Pair<IEntityProperty<P,?>, String > pr : propertyRetList )
                {
                    IEntityProperty<P,?> p = pr.getFirst();

                    try {
                        p.invokeSetter( pojo, call.get( pr.getSecond() ) );
                    } catch(Throwable th) {
                        DMLWorkParameterException.throwOnGetOutParameterValue( i, p.getPropertyName(), pr.getSecond(), th );
                    }
                }
            }
            catch( Throwable th ) {
                throwOnWorkFailure( STAGE_EXECUTE, th );
                return null;
            }
        }
        return pojo;
    }

    /** */
    @Override
    public void close( ) {
        if( call != null)
            call.close();
    }

    /** */
    @Override
    protected String workName() {
        return call == null
            ? "Work_UpdateDiff:" + entityMetaData.getEntityClass().getName()
            : call.getName();
    }
}
