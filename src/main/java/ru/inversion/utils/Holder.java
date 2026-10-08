
package ru.inversion.utils;

/**
 * Класс обертка над значением.
 * <p>
 * Для передачи в качестве параметров
 * @author ssu
 */
public class Holder<T> {
    
    private T value;

    /** */
    public Holder( ) {
    }

    /** */
    public Holder( T value ) {
        this.value = value;
    }
    
    /** */
    public void set( T value ) {
        this.value = value;
    }

    /** */
    public T get( ) {
        return this.value;
    }
    
    /** */
    public boolean isPresent( ) {
        return value != null;
    }

    /** */
    public static <T> Holder<T> of( T value ) { return new Holder<>(value); }
}
