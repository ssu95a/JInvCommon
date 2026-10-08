package ru.inversion.dataset.mark;

/** */
@MarkMode(mode = MarkModeEnum.MRK_ID2)
public class OARowID2Markable extends OARow implements IID2Markable {

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
    public Long getMarkLongID1( ) {
        return this.<Long>get(0);
    }
    @Override
    public Long getMarkLongID2( ) {
        return this.<Long>get(1);
    }
}
