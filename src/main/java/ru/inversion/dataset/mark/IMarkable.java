package ru.inversion.dataset.mark;

/**
 * Интерфейс для поддержки пометки в одной записи DataSet.
 * <p>
 * Определяет поведение для записей DataSet которые поддерживают пометку.
 * Предполагается, что запись имеет PK и он состоит из не более 2-х полей.
 * 
 * @author sulimoff
 */
public interface IMarkable {

    /** 
     * Название для столбца в SQL выражении, который содержит признак помечена запись или нет.
     */
    final public static String MARK_COLUMN = "MARK";
    
    /** 
     * Возвращает признак помечена текущая запись или нет.
     * <p>
     * @return {@code true } - помечена
     *         {@code false} - нет
     */
    boolean isMark( );
    
    /** 
     * Устанавливает признак пометки для текущей записи в DataSet 
     * <p>
     * @param mark
     *          {@code true } - помечена
     *          {@code false} - нет
     */
    void setMark( boolean mark );
    
    /** 
     * Возвращает значение 1-го поля PK для записи.
     * <p>
     * @return 
     *      значение первого поля PK
     */
    Object getMarkID1( );

    /** 
     * Возвращает значение 2-го поля PK для записи.
     * <p>
     * @return 
     *      значение второго поля PK
     */
    default Object getMarkID2(  ) { return null; }
}
