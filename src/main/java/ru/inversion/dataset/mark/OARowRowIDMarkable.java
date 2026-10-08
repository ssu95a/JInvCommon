package ru.inversion.dataset.mark;



import java.sql.RowId;

@MarkMode(mode = MarkModeEnum.MRK)
@MarkColumns(columns = {"ROWID"})
public class OARowRowIDMarkable extends OARow implements IRowIDMarkable {

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
    public String getMarkRowID() {
        final int indexByType = findIndexByType();
        return ((RowId)super.get(indexByType)).toString(); //OLD: return this.get(0);
    }

    private int findIndexByType() {
        final Object[] data = super.getData();
        for (int i = 0; i <= data.length; i++) {
            if (data[i] instanceof RowId) {
                return i;
            }
        }
        return 0;
    }
}
