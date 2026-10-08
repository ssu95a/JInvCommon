package ru.inversion.utils;


import java.util.EventObject;
import java.util.Map;

/**
 * by Java FX ObservableMapWrapper
 * */
public interface IObservableMap<K,V> extends Map<K,V> {

    /** */
    enum MapActionEnum {
        CREATE, UPDATE, DELETE
    }

    /** */
    class MapChangeEvent<K,V> extends EventObject {

        final private MapActionEnum action;
        final private K key;
        final private V oldValue, newValue;

        /** */
        public MapChangeEvent( Object source, MapActionEnum action, K key, V newValue, V oldValue ) {
            super( source );
            this.action = action;
            this.key    = key;
            this.newValue = newValue;
            this.oldValue = oldValue;
        }
        /** */
        public IObservableMap getObservableMap() { return (IObservableMap)getSource(); }
        /** */
        public MapActionEnum getAction() { return action;}
        /** */
        public K getKey() { return key; }
        /** */
        public V getOldValue() {
            return oldValue;
        }
        /** */
        public V getNewValue() {
            return newValue;
        }

        @Override
        public String toString() {
            return "MapChangeEvent{" + "action=" + action + ", key=" + key + ", oldValue=" + oldValue + ", newValue=" + newValue + '}';
        }
    }

    /** */
    interface IChangeMapListener<K,V> {
        public void onChanged( MapChangeEvent<? super K, ? super V> change );
    }

    /** */
    void addListener( IChangeMapListener<? super K, ? super V> listener );

    /** */
    void removeListener( IChangeMapListener<? super K, ? super V> listener );
}
