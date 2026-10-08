package ru.inversion.property;


import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * based on Java FX Property
 *
 * @author ssu
 */
public interface IProperty<T> extends Supplier<T>, Consumer<T> {

    @FunctionalInterface
    public interface ChangeListener<T> {
        void changed( IProperty<? extends T> property, T oldValue, T newValue );
    }
    
    /** */
    T getValue();
    
    /** */
    void setValue( T value );
    
    /** */
    Object getBean( );

    /** */
    String getName( );
    
    /** */
    void addListener(ChangeListener<T> listener);

    /** */
    void removeListener(ChangeListener<T> listener);

    /** */
    default T get() { return getValue(); }

    /** */
    default void accept(T t) { setValue(t); }
}
