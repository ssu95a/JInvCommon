package ru.inversion.dataset.mark;

/** */
@MarkMode(mode = MarkModeEnum.MRK_U)
@MarkColumns(columns = {"ID"})
public class OARowUMarkable extends OARow implements IUMarkable {

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
    public String getMarkStringID() {
        return this.get(0);
    }
}
