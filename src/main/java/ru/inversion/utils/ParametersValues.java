package ru.inversion.utils;

import ru.inversion.dataset.IParameters;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/** */
public class ParametersValues implements IParameters, IDumpable {

    /**
     * Все параметры - именованные, индексированные - храним в одной мапе
     * поэтому Comparator учитывает тип при сравнении
     * именованные - регистронезависимые
     */
    static final public Comparator<Comparable> ParamIdComparator = (o1, o2 ) -> {

        if( o1.getClass() == o2.getClass() ) {

            if( o1.getClass() == String.class )
                return ( (String)o1).compareToIgnoreCase( (String)o2 );

            return o1.compareTo(o2);
        }

        if( o1.getClass() == String.class )
            return 1;

        return -1;
    };

    /** Общий слушатель для OUT параметров */
    private BiConsumer<Object,Object> valueListener;
    //
    final private Map<Comparable,Object> values = new TreeMap<>(ParamIdComparator);
    //
    private IParameters parent = EMPTY;
    /** */
    public ParametersValues()
    { }
    /** */
    public ParametersValues( IParameters parent )
    {
        this.parent = U.nvl( parent, EMPTY );
    }
    /** */
    public <T> T get(String name) {

        T v = (T) values.get(name);

        if( v == null )
        {
            if( !values.containsKey(name) )
                 v = (T)parent.getParameter(name);
        }

        return v;
    }
    /** */
    public <T> T get(int index) {

        T v = (T) values.get(index);

        if( v == null )
        {
            if( !values.containsKey(index) )
                v = (T)parent.getParameter(index);
        }

        return v;
    }
    /** */
    @Override
    public boolean hasParameter(String name) {
        return values.containsKey(name ) || parent.hasParameter(name );
    }
    /** */
    @Override
    public boolean hasParameter(int index) {
        return values.containsKey(index) || parent.hasParameter(index);
    }
    /** */
    @Override
    public Object getParameter( String parameterName ) {
        return get(parameterName);
    }
    /** */
    @Override
    public Object getParameter( int parameterIndex ) {
        return get(parameterIndex);
    }
    /** */
    public void setParameter( String name, Object value ) {
        values.put( name, value );
        if( valueListener != null )
            valueListener.accept( name, value );
    }
    /** */
    public void setParameter( int index, Object value ) {
        values.put( index, value );
        if( valueListener != null )
            valueListener.accept( index, value );
    }
    @Override
    public void dump( Map< String, Object > properties ) {
        values.forEach(new BiConsumer< Comparable, Object >() {
            @Override
            public void accept( Comparable c, Object o ) {
                properties.put(c.toString(), o );
            }
        });
    }
    /** */
    public Object removeParameter( String name ) {
        return values.remove(name);
    }
    /** */
    public Object removeParameter( int index ) {
        return values.remove(index);
    }
    /** */
    public void forEach( BiConsumer<Comparable,Object> cnsm )
    {
        values.forEach( cnsm );
    }
    /** */
    public void setValueListener( BiConsumer< Object, Object > listener ) {
        this.valueListener = listener;
    }
    /** */
    public boolean isEmpty() { return values.isEmpty(); }
    /** */
    @Override
    public Set<String> getParametersNames() {

        if( parent == null || parent.isEmpty() ) {
            ;
        }
            if( values.keySet().stream().anyMatch(k->!S.isString(k)) )
                return Collections.unmodifiableSet( values.keySet().stream().filter(S::isString).map(Object::toString).collect(Collectors.toSet()));
            else
                return Collections.unmodifiableSet((Set<? extends String>) (Object)values.keySet());
    }
}
