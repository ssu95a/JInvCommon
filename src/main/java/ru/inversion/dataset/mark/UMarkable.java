package ru.inversion.dataset.mark;

/**
 * Базовый класс для pojo, с механизмом пометки MRK_U.
 * <p>
 * Одно VARCHAR поле как первичный ключ.
 * 
 * @author sulimoff
 */
public abstract
    class UMarkable
        extends MarkableBase
            implements IUMarkable { }