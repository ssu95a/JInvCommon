package ru.inversion.dataset.mark;

import static ru.inversion.dataset.mark.MarkModeEnum.MRK_ID2;

/**
 *
 * @author sulimoff
 */
public interface IID2Markable extends IMarkable {

    /** {@inheritDoc } */
    @Override
    default Object getMarkID1( ) {
        return getMarkLongID1();
    }
    
    /** {@inheritDoc } */
    @Override
    default Object getMarkID2( ) {
        return getMarkLongID2();
    }
    
    /** 
     * Возвращает ID из первого поля ключа типа Long.
     * <p>
     * Используется как ключ при пометке
     * @return 
     *          ID первого поля
     */
    public Long getMarkLongID1( );
    
    /** 
     * Возвращает ID из второго поля ключа типа Long.
     * <p>
     * Используется как ключ при пометке
     * @return 
     *          ID второго поля
     */
    public Long getMarkLongID2( );
}
