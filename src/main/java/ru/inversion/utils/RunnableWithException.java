package ru.inversion.utils;

/** */
@FunctionalInterface
public interface RunnableWithException {
    void run() throws Exception;
}
