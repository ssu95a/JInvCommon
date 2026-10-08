package ru.inversion.utils.lstn;

@FunctionalInterface
public interface IListenerCondition<E> {
    /** Признак есть ли необходимость обработать это событие */
    boolean shouldHandle(E event);
}
