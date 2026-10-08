package ru.inversion.dataset.mark;


/** */
@MarkMode(mode = MarkModeEnum.MRK_ID)
@MarkColumns(columns = {"ID"})
public class OARowIDMarkable extends OARow implements IIDMarkable {

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

    @Override
    public Long getMarkLongID( ) {
        return this.<Long>get(0);
    }
}