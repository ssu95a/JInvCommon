package ru.inversion.property;

import ru.inversion.utils.U;
import ru.inversion.utils.lstn.IListenerManConsumer;
import ru.inversion.utils.lstn.ListenerManFactory;

/**
 *
 * @author ssu
 */
public class Property<T> implements IProperty<T> {

    private T value;
    
    final private IListenerManConsumer<ChangeListener<T>> listeners = ListenerManFactory.createListenerManConsumer();
    
    /** */
    public Property( ) {
        this.value = null;
    }
    
    /** */
    public Property( T value ) {
        this.value = value;
    }
    
    /** */
    @Override
    public T getValue() {
        return value;
    }
    
    /** */
    @Override
    public void setValue( T v ) {
        
        T oldValue = this.getValue( );
        
        if( !U.equals(v, oldValue) ) {
            this.value = v;
            fireValueChangedEvent( oldValue, this.value );
        }
    }
    
    /** */
    protected void fireValueChangedEvent( T oldValue, T newValue ) {
        listeners.fire((l) -> l.changed( Property.this, oldValue, newValue));
    }

    /** */
    @Override
    public void addListener(ChangeListener<T> listener) {
        listeners.addListener(listener);
    }
    
    /** */
    @Override
    public void removeListener(ChangeListener<T> listener) {
          listeners.removeListener(listener);
    }
    
    /** */
    @Override
    public Object getBean() {
        return null;
    }
    
    /** */
    @Override
    public String getName() {
        return null;
    }

}
