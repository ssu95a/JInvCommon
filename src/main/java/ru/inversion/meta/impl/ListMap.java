package ru.inversion.meta.impl;


import ru.inversion.utils.Tags;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

public class ListMap<K,V> extends AbstractMap<K,V> {

    private static final String IMMUTABLE_MSG = "'ListMap' is immutable map";
    
    final private List<Entry<K,V>> entries;
    final private Comparator<K> comparator;

    /** */
    public ListMap( List<V> items, Function<V,K> keyExtractor, Comparator<K> comparator, boolean ignoreNulls )
    {
        this.comparator = Objects.requireNonNull( comparator, "comparator" );
        Objects.requireNonNull( keyExtractor, "keyExtractor" );

        List<V> src = (items == null) ? Collections.emptyList() : items;
        List<Entry<K, V>> copy = new ArrayList<>(src.size());

        for( V v : src )
        {
            if( v == null )
                if( ignoreNulls )
                    continue;
                else
                    throw new IllegalArgumentException( Tags.PRODUCT_LABEL + "entries contains null element");

            final K key = keyExtractor.apply(v);

            if( key == null)
                if( ignoreNulls )
                    continue;
                else
                    throw new IllegalArgumentException( Tags.PRODUCT_LABEL + "null keys are not supported");

            for( Entry<K,V> e1 : copy )
            {
                if( comparator.compare(e1.getKey(), key ) == 0 )
                    throw new IllegalArgumentException( Tags.PRODUCT_LABEL + "Duplicate keys by comparator: '" + e1.getKey() + "' and '" + key + "'");
            }

            copy.add( new AbstractMap.SimpleImmutableEntry<>( key, v ) );
        }

        this.entries = Collections.unmodifiableList(copy);
    }

    /** */
    private int findIndex( K key )
    {
        for( int i = 0; i < entries.size(); i++ )
        {
            if( comparator.compare( entries.get(i).getKey(), key) == 0 )
                return i;
        }
        return -1;
    }

    /** */
    @SuppressWarnings("unchecked")
    private int findIndexObject(Object key) {
        if (key == null) return -1;
        try {
            return findIndex((K) key);
        } catch (ClassCastException ex) {
            return -1;
        }
    }

    @Override
    public V get(Object key) {
        int index = findIndexObject(key);
        return index >= 0 ? entries.get(index).getValue() : null;
    }

    @Override
    public boolean containsKey(Object key) {
        return findIndexObject(key) >= 0;
    }

    @Override
    public Set<Entry<K,V>> entrySet() {
        return new AbstractSet<Entry<K, V>>() {
            @Override
            public Iterator<Entry<K, V>> iterator() {
                return entries.iterator();
            }
            @Override
            public int size() {
                return entries.size();
            }
            @Override
            public boolean isEmpty() {
                return entries.isEmpty();
            }

            @Override
            public boolean contains( Object o)
            {
                if (!(o instanceof Map.Entry<?, ?>))
                    return false;
                Map.Entry<?, ?> e = (Map.Entry<?, ?>) o;
                int idx = findIndexObject(e.getKey());
                return idx >= 0 && Objects.equals(entries.get(idx).getValue(), e.getValue());
            }

            @Override
            public Object[] toArray() {
                return entries.toArray();
            }
            @Override
            public <T> T[] toArray(T[] a) {
                return entries.toArray(a);
            }
            @Override
            public boolean add(Entry<K, V> kvEntry) {
                throw new UnsupportedOperationException(IMMUTABLE_MSG);
            }
            @Override
            public boolean remove(Object o) {
                throw new UnsupportedOperationException(IMMUTABLE_MSG);
            }
            @Override
            public boolean addAll(Collection<? extends Entry<K, V>> c) {
                throw new UnsupportedOperationException(IMMUTABLE_MSG);
            }
            @Override
            public boolean retainAll(Collection<?> c) {
                throw new UnsupportedOperationException(IMMUTABLE_MSG);
            }
            @Override
            public void clear() {
                throw new UnsupportedOperationException(IMMUTABLE_MSG);
            }
        };
    }

    @Override
    public Set<K> keySet() {
        // Return an unmodifiable set of keys in the stored order
        return new AbstractSet<K>() {
            @Override
            public Iterator<K> iterator() {
                Iterator<Entry<K, V>> it = entries.iterator();
                return new Iterator<K>() {
                    @Override
                    public boolean hasNext() {
                        return it.hasNext();
                    }

                    @Override
                    public K next() {
                        return it.next().getKey();
                    }
                };
            }

            @Override
            public int size() {
                return entries.size();
            }

            @Override
            public boolean contains(Object o) {
                return containsKey(o);
            }
        };
    }

    @Override
    public Collection<V> values() {
        return new AbstractCollection<V>() {
            @Override
            public Iterator<V> iterator() {
                Iterator<Entry<K, V>> it = entries.iterator();
                return new Iterator<V>() {
                    @Override
                    public boolean hasNext() {
                        return it.hasNext();
                    }

                    @Override
                    public V next() {
                        return it.next().getValue();
                    }
                };
            }
       @Override
            public int size() {
                return entries.size();
            }
        };
    }

    @Override
    public int size() {
        return entries.size();
    }

    @Override
    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /** */
    @Override
    public void forEach(BiConsumer<? super K, ? super V> action)
    {
        Objects.requireNonNull(action);
        entries.forEach( r->action.accept(r.getKey(),r.getValue()) );
    }

    @Override
    public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
        throw new UnsupportedOperationException(IMMUTABLE_MSG);
    }
    @Override
    public V putIfAbsent(K key, V value) {
        throw new UnsupportedOperationException(IMMUTABLE_MSG);
    }

    @Override
    public boolean remove(Object key, Object value) {
        throw new UnsupportedOperationException(IMMUTABLE_MSG);
    }

    @Override
    public boolean replace(K key, V oldValue, V newValue) {
        throw new UnsupportedOperationException(IMMUTABLE_MSG);
    }

    @Override
    public V replace(K key, V value) {
        throw new UnsupportedOperationException(IMMUTABLE_MSG);
    }

    @Override
    public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
        throw new UnsupportedOperationException(IMMUTABLE_MSG);
    }

    @Override
    public V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        throw new UnsupportedOperationException(IMMUTABLE_MSG);
    }

    @Override
    public V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        throw new UnsupportedOperationException(IMMUTABLE_MSG);
    }

    @Override
    public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
        throw new UnsupportedOperationException(IMMUTABLE_MSG);
    }
}
