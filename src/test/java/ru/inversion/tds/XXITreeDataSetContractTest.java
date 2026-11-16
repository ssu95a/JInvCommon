package ru.inversion.tds;

import org.junit.jupiter.api.Test;
import ru.inversion.dataset.IDataSet;
import ru.inversion.dataset.mark.IUMarkable;
import ru.inversion.dataset.mark.MarkColumns;
import ru.inversion.db.entity.ParentId;

import javax.persistence.Column;
import javax.persistence.Id;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XXITreeDataSetContractTest {

    private static final ITreeDataSetItemFactory ITEM_FACTORY =
            new ITreeDataSetItemFactory() {
                @Override
                public <P> ITreeDataSetItem<P> createItem(
                        ITreeDataSetItem<P> parent,
                        P value
                ) {
                    return new TreeDataSetItem<>(parent, value, true);
                }

                @Override
                public <P> ITreeDataSetItem<P> createRootItem(
                        ITreeDataSet<P> dataSet,
                        P value
                ) {
                    return new TreeDataSetItem<>(dataSet, value);
                }
            };

    private static final class TestDataSet
            extends XXITreeDataSet<MarkedTreeRow> {

        TestDataSet() {
            super(MarkedTreeRow.class);
        }

        @Override
        protected ITreeDataSetItemFactory getItemFactory() {
            return ITEM_FACTORY;
        }
    }

    @Test
    void bulkMarkUsesMarkTableKeyInsteadOfTreePrimaryKey() {
        TestDataSet dataSet = new TestDataSet();
        MarkedTreeRow root = row(1L, null, "MARK-ROOT", false);
        MarkedTreeRow child = row(2L, 1L, "MARK-CHILD", false);
        MarkedTreeRow alreadyMarked =
                row(3L, 1L, "MARK-OLD", true);

        dataSet.createRoot(root);
        dataSet.getRoot().insert(
                Arrays.asList(child, alreadyMarked),
                IDataSet.InsertRowModeEnum.LAST,
                false
        );

        assertEquals(
                Arrays.<Object>asList("MARK-ROOT", "MARK-CHILD"),
                dataSet.collectUnmarkedMarkIds(false)
        );
        assertEquals(
                Collections.<Object>singletonList("MARK-CHILD"),
                dataSet.collectUnmarkedMarkIds(true)
        );
    }

    @Test
    void bulkMarkRejectsNullMarkTableKey() {
        TestDataSet dataSet = new TestDataSet();
        dataSet.createRoot(row(1L, null, null, false));

        TreeDataSetException failure = assertThrows(
                TreeDataSetException.class,
                () -> dataSet.collectUnmarkedMarkIds(false)
        );

        assertTrue(failure.getMessage().contains("null mark ID"));
    }

    @Test
    void markedCountTracksManualTreeMutations() {
        TestDataSet dataSet = new TestDataSet();
        MarkedTreeRow root = row(1L, null, "ROOT", true);
        MarkedTreeRow child = row(2L, 1L, "CHILD", true);

        dataSet.createRoot(root);
        assertEquals(1, dataSet.getMarkedCount());

        dataSet.getRoot().insert(child);
        assertEquals(2, dataSet.getMarkedCount());

        dataSet.getRoot().remove(dataSet.getRoot().getChildAt(0));
        assertEquals(1, dataSet.getMarkedCount());

        dataSet.getRoot().setValue(
                row(1L, null, "ROOT", false)
        );
        assertEquals(0, dataSet.getMarkedCount());
        assertFalse(dataSet.hasMarkedItems());

        ITreeDataSetItem<MarkedTreeRow> removedRoot =
                dataSet.getRoot();
        removedRoot.setValue(row(1L, null, "ROOT", true));
        assertEquals(1, dataSet.getMarkedCount());

        dataSet.removeRootItem(removedRoot);
        assertEquals(0, dataSet.getMarkedCount());
        assertFalse(dataSet.isMarkItem(removedRoot));
    }

    @Test
    void failedClearStillSynchronizesMarkedCountWithActualTree() {
        TestDataSet dataSet = new TestDataSet();
        dataSet.createRoot(row(1L, null, "ROOT", true));
        dataSet.getRoot().setItemCurrent();
        dataSet.addNavigationListener(event -> {
            throw new IllegalStateException("navigation failed");
        });

        assertThrows(IllegalStateException.class, dataSet::clear);

        assertTrue(dataSet.isEmpty());
        assertEquals(0, dataSet.getMarkedCount());
    }

    private static MarkedTreeRow row(
            Long id,
            Long parentId,
            String markId,
            boolean marked
    ) {
        MarkedTreeRow row = new MarkedTreeRow();
        row.setId(id);
        row.setParentId(parentId);
        row.setMarkId(markId);
        row.setMark(marked);
        return row;
    }

    @MarkColumns(columns = {"MARK_ID"})
    public static final class MarkedTreeRow implements IUMarkable {
        private Long id;
        private Long parentId;
        private String markId;
        private boolean marked;

        @Id
        @Column(name = "TREE_ID")
        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        @ParentId
        @Column(name = "PARENT_ID")
        public Long getParentId() {
            return parentId;
        }

        public void setParentId(Long parentId) {
            this.parentId = parentId;
        }

        @Column(name = "MARK_ID")
        @Override
        public String getMarkStringID() {
            return markId;
        }

        public void setMarkId(String markId) {
            this.markId = markId;
        }

        @Override
        public boolean isMark() {
            return marked;
        }

        @Override
        public void setMark(boolean marked) {
            this.marked = marked;
        }
    }
}
