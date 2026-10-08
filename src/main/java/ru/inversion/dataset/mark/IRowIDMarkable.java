package ru.inversion.dataset.mark;

/**
 *
 * @author ssu
 */
public interface IRowIDMarkable extends IMarkable {

    /** {@inheritDoc} */
    @Override
    default Object getMarkID1( ) {
        return getMarkRowID();
    }
    
    /** 
     * Возвращает {@code ROWID} в строковом виде.
     * <p>
     * Используется как ключ при пометке
     * @return 
     *          ROWID как строка
     */
    String getMarkRowID( );
}
