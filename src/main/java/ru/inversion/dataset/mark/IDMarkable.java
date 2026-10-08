package ru.inversion.dataset.mark;

/**
 * Базовый класс для pojo, с механизмом пометки MRK_ID.
 * <p>
 * Одно INTEGER поле как первичный ключ.
 * 
 * @author sulimoff
 */
public abstract
    class IDMarkable
        extends MarkableBase
            implements IIDMarkable { }
