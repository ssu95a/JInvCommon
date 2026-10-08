package ru.inversion.meta.impl.work;

import ru.inversion.datacall.CallException;
import ru.inversion.datacall.IDataCall;
import ru.inversion.datacall.SQLCallBuilder;
import ru.inversion.db.dialect.SqlDialect;
import ru.inversion.meta.*;
import ru.inversion.tc.TaskContext;
import ru.inversion.utils.*;

import javax.persistence.NamedStoredProcedureQuery;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** */
public class Work_DMLByNamedStored<P> extends WorkBase<P,P,P> {

    final public static String PLSQL_PREFIX = EntityConstants.PLSQL;

    final private IDataCall call;

    final protected List<Pair<IEntityProperty<P,?>,String>> propertySetList = new ArrayList<>();
    final protected List<Pair<IEntityProperty<P,?>,String>> propertyRetList = new ArrayList<>();

    private final String workNameHint;

    public Work_DMLByNamedStored( IEntityMetaData<P> entityMetaData, TaskContext tc, NamedStoredProcedureQuery nspq )
    {
        super( entityMetaData, tc, nspq );

        final SqlDialect dialect = tc.dialect();

        String q = nspq.procedureName();

        if( S.isNullOrEmpty(q) )
            q = PLSQL_PREFIX;

        String dmlName = nspq.name();

        if( q.startsWith( PLSQL_PREFIX ) ) {

            q = q.substring( PLSQL_PREFIX.length() );

            URL url = null;

            if( q.isEmpty() )
                url = entityMetaData.getEntityClass().getResource("plsql/def.xml");
            else
                url = entityMetaData.getEntityClass().getResource(q);

            workNameHint = dmlName + " (" + url + ")";

            try {
                call = SQLCallBuilder.NEW( tc ).url(url).name( entityMetaData.getEntityClass().getSimpleName() + "." + dmlName ).build();
            } catch( CallException e ) {
                throwOnWorkFailure(STAGE_PREPARE, e);
                throw new AssertionError(); //stub
            }
        }
        else
        {
            workNameHint = dmlName + " (@NamedNativeQuery)";

            try {
                call = SQLCallBuilder.NEW( tc ).runClass( entityMetaData.getEntityClass() ).name( dmlName ).build();
            } catch(CallException e) {
                throwOnWorkFailure(STAGE_PREPARE, e);
                throw new AssertionError();//stub
            }
        }

        int dml = U.decode( nspq.name(), EntityConstants.INSERT, 1, EntityConstants.UPDATE, 2, -1 );

        for( Map.Entry<String, IEntityProperty<P,?> > e : entityMetaData.getColumnsMap().entrySet() ) {

            final IEntityColumn<P, ?> columnInfo = e.getValue().getColumnInfo();

            if( ( dml == 1 && columnInfo.isReturningOnInsert() )
                ||
                ( dml == 2 && columnInfo.isReturningOnUpdate() )
            )
                resolveOutPrm( dialect, e.getValue()).ifPresent( (s)->propertyRetList.add( Pair.makePair( e.getValue(),s ) ));

            resolveInPrm( dialect, e.getValue() ).ifPresent( (s)->propertySetList.add( Pair.makePair( e.getValue(), s ) ));

        }//end for
    }

    private Optional<String> resolveInPrm(SqlDialect d, IEntityProperty<P,?> p) {

        {
            String prm = d.inName(p);
            if (call.hasParameter(prm))
                return Optional.of(prm);
        }

        if( p.isId() )
        {
            String pkPrm = d.pkName(p);
            if (call.hasParameter(pkPrm))
                return Optional.of(pkPrm);
        }

        {
            String colName = p.getColumnInfo().getName();
            if( call.hasParameter(colName) )
                return Optional.of(colName);
        }
        return Optional.empty();
    }

    private Optional<String> resolveOutPrm(SqlDialect d, IEntityProperty<P,?> p) {

        {
            String outPrm = d.outName(p);
            if (call.hasParameter(outPrm))
                return Optional.of(outPrm);
        }

        if( p.isId() )
        {
            String pkPrm = d.pkName(p);
            if (call.hasParameter(pkPrm))
                return Optional.of(pkPrm);
        }

        String colName = p.getColumnInfo().getName();
        if (call.hasParameter(colName))
            return Optional.of(colName);

        return Optional.empty();
    }

    @Override
    public P execute( P pojo ) {

        try {

            if( pojo == null )
                throw new IllegalStateException( Tags.PRODUCT_LABEL + "'pojo' is null" );

            int i = 0;

            for( Pair<IEntityProperty<P, ?>, String> p : propertySetList )
            {
                i++;

                try {
                    call.set( p.second, Jt2St.convert( p.first.invokeGetter(pojo) ) );
                }
                catch( Throwable th ) {
                    DMLWorkParameterException.throwOnSetParameterValue( i, p.first.getPropertyName(), p.second, th );
                }
            }

            call.execute( );

            i = 0;

            for( Pair<IEntityProperty<P, ?>, String> p : propertyRetList ) {

                i++;

                try {
                    p.first.invokeSetter( pojo, call.getParameter(p.second) );
                }
                catch( Throwable th ) {
                    DMLWorkParameterException.throwOnGetOutParameterValue( i, p.first.getPropertyName(), p.second, th );
                }
            }

            return pojo;
        }
        catch( Throwable th ) {
            throwOnWorkFailure( STAGE_EXECUTE, th );
            return null;
        }
    }

    @Override
    protected String workName() { return call != null ? call.getName() : workNameHint; }
}
