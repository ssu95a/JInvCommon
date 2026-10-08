package ru.inversion.utils.js;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import ru.inversion.dataset.IParameters;
import ru.inversion.utils.*;
import ru.inversion.utils.lstn.IListenerManEvent;
import ru.inversion.utils.lstn.ListenerManFactory;

import javax.script.*;
import java.io.Reader;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;

/** */
public class ScriptMan implements AutoCloseable {

    final static private String SCRIPT_ENGINE_NAME = System.getProperty("js.engine.name", "nashorn");

    static final private Cache<Object, Triplet< Lock, CompiledScript, LocalDateTime > > scriptCache;

    static {
        scriptCache = Caffeine.newBuilder().maximumSize(8).expireAfterAccess(10,TimeUnit.MINUTES).build();
    }

    /** */
    static final private ScriptEngineManager SCRIPT_ENGINE_MANAGER = new ScriptEngineManager();

    /** */
    private static ScriptEngine scriptEngine()
    {
        ScriptEngine engine =
                SCRIPT_ENGINE_MANAGER.getEngineByName(
                        SCRIPT_ENGINE_NAME
                );

        if( engine == null )
            throw new IllegalStateException(
                    "Script engine not found: "
                            + SCRIPT_ENGINE_NAME
            );

        if( !(engine instanceof Compilable) )
            throw new IllegalStateException(
                    "Script engine does not support compilation: "
                            + SCRIPT_ENGINE_NAME
            );

        return engine;
    }

    /** */
    private final IScriptSupplier scriptSupplier;

    /** */
    final private IListenerManEvent< Consumer<ScriptManEvent>, ScriptManEvent > listeners =
                   ListenerManFactory.createListenerManEvent( Consumer::accept );

    /** */
    private ScriptMan( IScriptSupplier scriptSupplier ) {
        this.scriptSupplier = scriptSupplier;
    }

    /** */
    public void addListener( Consumer<ScriptManEvent> l ) { listeners.addListener(l); }

    /** */
    public void removeListener( Consumer<ScriptManEvent> l ) { listeners.removeListener(l); }

    /** */
    private Bindings toBindings( IParameters parameters )
    {
        return new SimpleBindings( ) {

            @Override
            public Object get( Object key ) {

                Object v = parameters.getParameter( key.toString() );

//                if( v instanceof Supplier)
//                    return ((Supplier)v).get();

                if( v instanceof Function)
                    return ((Function)v).apply(key);

                return v;
            }
            @Override
            public Object put( String key, Object value ) {
                Object oldValue = parameters.getParameter( key );
                parameters.setParameter( key, value );
                return oldValue;
            }
            @Override
            public boolean containsKey( Object key ) {
                return parameters.hasParameter((String)key);
            }
            @Override
            public Set< String > keySet() {
                return parameters.getParametersNames();
            }
            @Override
            public boolean isEmpty() {
                return parameters.isEmpty();
            }
            @Override
            public boolean containsValue( Object value ) {
                throw new UnsupportedOperationException("containsValue");
            }
            @Override
            public int size() {
                throw new UnsupportedOperationException("size");
            }
        };
    }

    /** */
    public <T> T execute( Object scriptId, IParameters parameters )  {

        try {

            while( true )
            {
                final String scriptKey = scriptSupplier.makeGlobalId(scriptId);

                final Holder<Boolean> newInstance = new Holder<>(false);

                final Triplet<Lock,CompiledScript,LocalDateTime> compiledTriplet = scriptCache.get( scriptKey, (s) -> {

                    final Pair<Reader,LocalDateTime> r = scriptSupplier.apply( scriptId );
                    if( r == null )
                        throw new IllegalStateException( Tags.PRODUCT_LABEL + "No script found with id " +  scriptId );

                    if(!listeners.isEmpty() )
                        listeners.fire( new ScriptManEvent( ScriptMan.this, scriptId, ScriptManEvent.Type.COMPILE, true ) );

                    final CompiledScript compiled;
                    try {
                        compiled = ((Compilable)scriptEngine()).compile(r.first);
                    } catch (ScriptException e) {
                        throw new RuntimeException(Tags.PRODUCT_LABEL + "Error on compile script", e );
                    }

                    if(!listeners.isEmpty() )
                        listeners.fire( new ScriptManEvent( ScriptMan.this, scriptId, ScriptManEvent.Type.COMPILE, false ) );

                    newInstance.set(true);

                    return Triplet.makeTriplet( new ReentrantLock(), compiled, r.second );
                });

                compiledTriplet.first.lock();

                try {

                    if( !newInstance.get() )
                    {
                        if( scriptSupplier.isOutdated( scriptId, compiledTriplet.third ) )
                        {
                            if( !listeners.isEmpty() )
                                listeners.fire( new ScriptManEvent( ScriptMan.this, scriptId, ScriptManEvent.Type.OUTDATED, true ) );

                            /*
                             * Удаляем только тот экземпляр, свежесть которого
                             * только что проверили. Другой поток уже мог
                             * перекомпилировать скрипт и положить новый entry.
                             */
                            scriptCache.asMap().remove( scriptKey, compiledTriplet );

                            if(!listeners.isEmpty() )
                                listeners.fire( new ScriptManEvent( ScriptMan.this, scriptId, ScriptManEvent.Type.OUTDATED, false) );

                            continue;
                        }
                    }

                    if( !listeners.isEmpty() )
                         listeners.fire( new ScriptManEvent( ScriptMan.this, scriptId, ScriptManEvent.Type.EXECUTE, true ) );

                    SimpleScriptContext sc = new SimpleScriptContext();
                    sc.setBindings( compiledTriplet.second.getEngine().createBindings(), ScriptContext.ENGINE_SCOPE );
                    sc.setBindings( toBindings( U.nvl( parameters, IParameters.EMPTY ) ), ScriptContext.GLOBAL_SCOPE );

                    //  sc.setWriter     ( ctxt.getWriter() );
                    //  sc.setReader     ( ctxt.getReader() );
                    //  sc.setErrorWriter( ctxt.getErrorWriter() );

                    final Object value = compiledTriplet.second.eval( sc );

                    if( !listeners.isEmpty() )
                         listeners.fire( new ScriptManEvent( ScriptMan.this, scriptId, ScriptManEvent.Type.EXECUTE, false) );

                    return (T)value;
                }
                finally {
                    compiledTriplet.first.unlock();
                }

            }
        }
        catch( Exception exception ) {
            throw new RuntimeException( Tags.PRODUCT_LABEL + "Error on execute script with ID " + scriptId, exception );
        }
    }

    @Override
    public void close() { }

    /** */
    public static ScriptMan create( IScriptSupplier scriptSupplier )
    {
        return new ScriptMan( Objects.requireNonNull( scriptSupplier, "'scriptSupplier' is null" ) );
    }

}
