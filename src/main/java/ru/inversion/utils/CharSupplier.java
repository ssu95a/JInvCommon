package ru.inversion.utils;

@FunctionalInterface
public interface CharSupplier {

    /**
     * Gets a result.
     *
     * @return a result
     */
    char getAsChar();
}
