package ru.inversion.utils.converter;

/**
 *
 * @author ssu
 */
public interface IConverter<S, T> {

    /**
     * S->T
     */
    T to(S value);

    /**
     * T->S
     */
    S from(T value);


}
