package ru.inversion.meta.impl.work;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.inversion.datacall.*;
import ru.inversion.db.dialect.SqlDialect;
import ru.inversion.meta.DMLWorkException;
import ru.inversion.meta.DMLWorkParameterException;
import ru.inversion.meta.IEntityMetaData;
import ru.inversion.meta.IEntityProperty;
import ru.inversion.tc.TaskContext;
import ru.inversion.utils.Pair;
import ru.inversion.utils.S;
import ru.inversion.utils.Tags;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public abstract class WorkBaseDml<P,V,R> extends WorkBase<P,V,R> {

    private static final Logger logger = LoggerFactory.getLogger(WorkBaseDml.class);

    final protected List< Pair<IEntityProperty<P,?>,String> > propertySetList = new ArrayList<>();
    final protected List< Pair<IEntityProperty<P,?>,String> > propertyRetList = new ArrayList<>();

    final protected IDataCall call;

    /** */
    public WorkBaseDml( IEntityMetaData< P > entityMetaData, TaskContext tc, Object param )
    {
        super( entityMetaData, tc, param );

        call = SQLCallBuilder.NEW(tc).name( makeName() ).callStatement( make(param) ).build();

        final SqlDialect dialect = tc.dialect();

        for( ParameterMetadata pm : call.getParameterIter()) {

            final String prm = pm.getName();

            if( S.isNullOrEmpty(prm) ) {
                logger.debug("Skip indexed parameter in DML: pos={}, mode={}", pm.getPosition(), pm.getMode());
                continue;
            }

            switch( pm.getMode() )
            {
                case IN:
                    propertyByParameter( dialect, prm )
                        .map( prp->{ propertySetList.add( Pair.makePair( prp, prm));return prp;})
                            .orElseThrow( ()->new IllegalStateException ( Tags.PRODUCT_LABEL + "Параметр '" + prm + "' (IN) не найден в метаданных " + entityMetaData.getName() ));
                break;
                case OUT:
                {
                    final Optional<IEntityProperty<P, ?>> opt = propertyByParameter(dialect, prm);

                    if( !opt.isPresent() )
                    {
                        if ("return#".equalsIgnoreCase(prm) || "_return".equalsIgnoreCase(prm)) {
                            break; // системный
                        }

                        throw new IllegalStateException(
                            Tags.PRODUCT_LABEL + "OUT параметр '" + prm + "' не найден в метаданных " + entityMetaData.getName()
                            //logger.warn("{} OUT-parameter can't be mapped to entity property in {}", prm, entityMetaData.getName() );
                        );
                    }
                    propertyRetList.add(Pair.makePair( opt.get(), prm ));
                }
                break;
                case INOUT:
                {
                    propertyByParameter( dialect, prm )
                        .map( prp->{
                            propertySetList.add(Pair.makePair( prp, prm));
                            propertyRetList.add(Pair.makePair( prp, prm));
                            return prp;
                            }
                        )
                        .orElseThrow( ()->new IllegalStateException ( Tags.PRODUCT_LABEL + "Параметр '" + prm + "' (INOUT) не найден в метаданных " + entityMetaData.getName() ));
                }
                break;
            }
        }
    }

    /** */
    private Optional<IEntityProperty<P, ?>> propertyByParameter(SqlDialect dialect, String prm )
    {
        for( IEntityProperty<P, ?> p : entityMetaData.getColumnsMap().values() )
        {
            if( prm.equalsIgnoreCase(dialect.inName (p)))  return Optional.of(p);
            if( prm.equalsIgnoreCase(dialect.outName(p))) return Optional.of(p);
        }

        for( IEntityProperty<P, ?> pk : entityMetaData.getIDList() )
        {
            if( prm.equalsIgnoreCase(dialect.pkName(pk)) )
                return Optional.of(pk);
        }

        // 2) Fallback на legacy: OUT__X -> X (если вдруг в метаданных/старом коде так осталось)
        if( prm.regionMatches(true, 0, "OUT__", 0, "OUT__".length()) )
        {
            final String raw = prm.substring("OUT__".length());
            final IEntityProperty<P, ?> p = entityMetaData.getProperty(raw);
            if( p != null)
                return Optional.of(p);
        }

        // 3) Последний шанс: прямое имя свойства (на случай кастомных параметров)
        return Optional.ofNullable( entityMetaData.getProperty(prm) );
    }

    public WorkBaseDml( IEntityMetaData<P> entityMetaData, TaskContext tc ) {
        this( entityMetaData, tc, null );
    }

    /** */
    protected String makeName() {
        return String.join( ":", entityMetaData.getEntityClass().getName(), getClass().getSimpleName() );
    }

    /** */
    protected abstract void prepareCall( CallBuilder builder, Object param );

    /** */
    private Function<String, CallStatement > make( Object param ) {
        return new Function< String, CallStatement >() {
            @Override
            public CallStatement apply( String unused )
            {
                final CallBuilder builder = CallBuilder.NEW();

                prepareCall( builder, param );

                return builder.build();
            }
        };
    }

    /** helpers */
    protected final void putIn(IEntityProperty<P,?> p, String prm) {
        if (p == null || S.isNullOrEmpty(prm))
            throw new IllegalArgumentException("addIn: p/prm is null");
        propertySetList.add( Pair.makePair(p, prm) );
    }

    protected final void putPk(IEntityProperty<P,?> p, String prm) {
        // тоже IN
        putIn(p, prm);
    }

    protected final void putOut(IEntityProperty<P,?> p, String prm) {
        if( p == null || S.isNullOrEmpty(prm) )
            throw new IllegalArgumentException("addOut: p/prm is null");

        propertyRetList.add( Pair.makePair( p, prm) );
    }

    /** INOUT: и сетим, и читаем. */
    protected final void putInOut(IEntityProperty<P, ?> property, String prm) {
         putIn( property, prm );
        putOut( property, prm );
    }


    /** */
    @Override
    public R execute( V p1 ) {

        try {

            if( p1 == null )
                throw new IllegalStateException(Tags.PRODUCT_LABEL + "'pojo' is null");

            P pojo = (P)p1;

            int i = 0;

            for( Pair<IEntityProperty< P, ? >, String> p : propertySetList )
            {
                try {
                    call.set( p.second,  p.first.invokeGetter(pojo) );
                    i++;
                } catch(Throwable th) {
                    DMLWorkParameterException.throwOnSetParameterValue( i, p.first.getPropertyName(), p.second, th );
                }
            }

            call.execute();

            i = 0;

            for( Pair<IEntityProperty< P, ? >, String> p : propertyRetList)
            {
                try {
                    p.first.invokeSetter( pojo, call.get( p.second ) );
                    i++;
                } catch(Throwable th) {
                    DMLWorkParameterException.throwOnGetOutParameterValue( i, p.first.getPropertyName(), p.second, th );
                }
            }

            return (R)pojo;
        }
        catch( Throwable th ) {
            throwOnWorkFailure( STAGE_EXECUTE, th );
            return null;
        }
    }

    /** */
    @Override
    protected String workName( ) { return call.getName(); }
}
