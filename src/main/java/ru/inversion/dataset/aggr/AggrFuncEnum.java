package ru.inversion.dataset.aggr;

/**
 * Типы агрегирующих ф-ций используемых в аггрегаторах.
 * <p>
 * @author Sulimoff
 */
public enum AggrFuncEnum {
    
    /** 
     * Без ф-ции, возможно для применения с константами.
     * (пока не поддерживается)
     */
    NONE,
    
    /** 
     * Сумма - SUM([column]).
     */
    SUM, 
    
    /** 
     * Кол-во. 
     */
    COUNT
}
