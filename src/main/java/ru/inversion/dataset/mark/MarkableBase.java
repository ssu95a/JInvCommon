package ru.inversion.dataset.mark;

/**
 * Базовый класс для поддержки пометки в записях DataSet.
 * <p>
 * @author sulimoff
 */
public abstract class MarkableBase implements IMarkable {

    /** 
     * Помечена запись или нет.
     */
    private boolean mark = false;
    
    @Override
    public boolean isMark( ) {
        return mark;
    }

    @Override
    public void setMark( boolean mark ) {
        this.mark = mark;
    }
}
