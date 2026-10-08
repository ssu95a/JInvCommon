package ru.inversion.dataset.mark;

/**
 * Базовый класс для pojo, с механизмом пометки MRK.
 * <p>
 * ROWID как первичный ключ.
 * 
 * @author sulimoff
 */
public abstract
    class RowIDMarkable
        extends MarkableBase
            implements IRowIDMarkable { }
