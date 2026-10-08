package ru.inversion.dataset;

import java.util.function.Supplier;

/** Связывает объекты Data с другими объектами */
public interface IDataLink<S,D> {

    /**
     * Возвращает ссылку на source объект
     *
     * @return S
     */
    S getSource( );

    /**
     * Возвращает ссылку на destination объект
     *
     * @return D
     * */
    D getDestination();

    /**
     *
     */
    void setAutoRequery( Supplier<Boolean> requeryIf );
}
