package ru.inversion.dataset.mark;


/**
 *
 * @author ssu
 */
public interface IUMarkable extends IMarkable {

    @Override
    default Object getMarkID1( ) {
        return getMarkStringID();
    }
    
    /** 
     * Возвращает ID типа String.
     * <p>
     * Используется как ключ при пометке
     * @return 
     *          Строковый ID записи
     */
    String getMarkStringID( );
    
}
