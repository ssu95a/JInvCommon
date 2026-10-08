package ru.inversion.utils;

import ru.inversion.utils.lstn.IListenerManEvent;
import ru.inversion.utils.lstn.ListenerManFactory;

import java.util.*;

import static ru.inversion.utils.IObservableMap.MapActionEnum.*;

/**
 * by Java FX ObservableMapWrapper
 * */
public class ObservableMap<K,V> implements IObservableMap<K,V> {

    private class ObservableEntrySet implements Set<Entry<K,V>>{

        @Override
        public int size() {
            return map.size();
        }

        @Override
        public boolean isEmpty() {
            return map.isEmpty();
        }

        @Override
        public boolean contains(Object o) {
            return map.entrySet().contains(o);
        }

        @Override
        public Iterator<Entry<K, V>> iterator() {
            return new Iterator<Entry<K, V>>() {
                private Iterator<Entry<K,V>> iter = map.entrySet().iterator();
                private K lastKey; private V lastValue;

                @Override
                public boolean hasNext() {
                    return iter.hasNext();
                }

                @Override
                public Entry<K, V> next() {
                    final Entry< K, V > v = iter.next();
                    lastKey = v.getKey();
                    lastValue = v.getValue();
                    return v;
                }

                @Override
                public void remove() {
                    iter.remove();
                    listeners.fire( new MapChangeEvent( this, DELETE, lastKey, null, lastValue ) );
                }
            };
        }

        @Override
        public Object[] toArray() {
            throw new UnsupportedOperationException("Not supported.");
        }

        @Override
        public <T> T[] toArray(T[] a) {
            throw new UnsupportedOperationException("Not supported.");
        }

        @Override
        public boolean add(Entry<K, V> e) {
            throw new UnsupportedOperationException("Not supported.");
        }

        @Override
        @SuppressWarnings("unchecked")
        public boolean remove(Object o) {
            boolean ret = map.entrySet().remove(o);
            if (ret) {
                Entry<K,V> entry = (Entry<K, V>) o;
                listeners.fire( new MapChangeEvent( this, DELETE, entry.getKey(), null, entry.getValue() ) );
            }
            return ret;
        }

        @Override
        public boolean containsAll(Collection<?> c) {
            return map.entrySet().containsAll(c);
        }

        @Override
        public boolean addAll(Collection<? extends Entry<K, V>> c) {
            throw new UnsupportedOperationException("Not supported.");
        }

        @Override
        public boolean retainAll(Collection<?> c) {
            return removeRetain(c, false);
        }

        private boolean removeRetain(Collection<?> c, boolean remove) {
            throw new UnsupportedOperationException("Not supported.");
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            return removeRetain(c, true);
        }

        @Override
        public void clear() {
            ObservableMap.this.clear();
        }

        @Override
        public String toString() {
            return map.entrySet().toString();
        }

        @Override
        public boolean equals(Object obj) {
            return map.entrySet().equals(obj);
        }

        @Override
        public int hashCode() {
            return map.entrySet().hashCode();
        }
    }
    
    final private Map<K,V> map;
    final private IListenerManEvent<IChangeMapListener,MapChangeEvent> listeners
            = ListenerManFactory.createListenerManEvent(IChangeMapListener::onChanged);

    public ObservableMap( Map<K,V> map ) {
        this.map = Objects.requireNonNull(map, "'map' is null");
    }
    public ObservableMap( Map<K,V> map, IChangeMapListener listener ) {
        this.map = Objects.requireNonNull(map, "'map' is null");
        addListener(listener);
    }

    @Override
    public void addListener( IChangeMapListener listener ) {
        listeners.addListener(listener);
    }

    @Override
    public void removeListener( IChangeMapListener listener ) {
        listeners.removeListener(listener);
    }

    @Override
    public int size() {
        return map.size();
    }

    @Override
    public boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    public boolean containsKey( Object key ) {
        return map.containsKey(key);
    }

    @Override
    public boolean containsValue( Object value ) {
        return map.containsValue(value);
    }

    @Override
    public V get( Object key ) {
        return map.get(key);
    }

    @Override
    public V put( K key, V value ) {

        if( listeners.isEmpty() )
            return map.put( key, value );

        V oldValue;

        if( map.containsKey(key) )
        {
            oldValue = map.put( key, value );

            if( !U.equals( value, oldValue ) )
                listeners.fire( new MapChangeEvent( this, UPDATE, key, value, oldValue ) );

        } else
        {
            oldValue = map.put( key, value );
            listeners.fire( new MapChangeEvent( this, CREATE, key, value, oldValue ) );
        }

        return oldValue;
    }

    @Override
    public V remove( Object key ) {

        if( listeners.isEmpty() )
            return map.remove( key );

        if( !map.containsKey(key) )
            return null;

        V oldValue = map.remove(key);
        listeners.fire( new MapChangeEvent( this, DELETE, key, null, oldValue ) );
        return oldValue;
    }

    @Override
    public void putAll( Map< ? extends K, ? extends V > m ) {

        if( listeners.isEmpty() )
            map.putAll(m);
        else
        {
            for( Map.Entry<? extends K, ? extends V> e : m.entrySet() ) {
                 put( e.getKey(), e.getValue() );
            }
        }
    }

    @Override
    public void clear() {

        if( listeners.isEmpty() )
             map.clear( );
        else
        {
            for( Iterator<Entry<K, V>> i = map.entrySet().iterator(); i.hasNext(); )
            {
                Entry<K, V> e = i.next();
                K key = e.getKey();
                V val = e.getValue();
                i.remove();
                listeners.fire( new MapChangeEvent( this, DELETE, key, null, val ) );
            }
        }
    }

    @Override
    public Set< K > keySet() {
        return Collections.unmodifiableSet( map.keySet() );
    }

    @Override
    public Collection< V > values() {
        return Collections.unmodifiableCollection(map.values());
    }
    private ObservableEntrySet entrySet;

    @Override
    public Set< Entry< K, V > > entrySet() {
        if (entrySet == null) {
            entrySet = new ObservableEntrySet();
        }
        return entrySet;
    }
}
