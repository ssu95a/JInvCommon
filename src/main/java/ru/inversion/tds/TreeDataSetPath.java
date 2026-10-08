package ru.inversion.tds;

import ru.inversion.utils.S;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Immutable tree path.
 *
 * Path order:
 *   items(false): root -> leaf
 *   items(true):  leaf -> root
 */
public final class TreeDataSetPath<P, T extends ITreeDataSetItem<P>>
        implements ITreeDataSetPath<P, T> {

    private final List<T> items;

    /** */
    private TreeDataSetPath(List<T> items) {

        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Tree path is empty");
        }

        this.items = Collections.unmodifiableList(new ArrayList<>(items));
    }

    /**
     * Creates path from firstItem to lastItem.
     *
     * firstItem must be equal to lastItem or be an ancestor of lastItem.
     */
    public TreeDataSetPath(T firstItem, T lastItem) {
        this(buildPath(firstItem, lastItem));
    }

    /**
     * Creates path from root item to lastItem.
     */
    public TreeDataSetPath(T lastItem) {
        this(buildPathToRoot(lastItem));
    }

    /** */
    @Override
    public T getFirstItem() {
        return items.get(0);
    }

    /** */
    @Override
    public T getLastItem() {
        return items.get(items.size() - 1);
    }

    /** */
    @Override
    public T getItem(int nItem) {

        if (nItem < 0 || nItem >= items.size()) {
            return null;
        }

        return items.get(nItem);
    }

    /** */
    @Override
    public Iterable<T> items(boolean reverse) {

        if (!reverse) {
            return items;
        }

        List<T> reversed = new ArrayList<>(items);
        Collections.reverse(reversed);

        return reversed;
    }

    /** */
    @Override
    public ITreeDataSetPath<P, T> getParentPath() {

        if (items.size() <= 1) {
            return null;
        }

        return new TreeDataSetPath<>(
                items.subList(0, items.size() - 1)
        );
    }

    /** */
    @Override
    public int getPathCount() {
        return items.size();
    }

    /** */
    @Override
    public String toString(
            Function<P, String> titleExtractor,
            String dlmtr
    ) {
        Function<P, String> extractor = titleExtractor;

        if (extractor == null) {
            extractor = value -> String.valueOf(value);
        }

        String delimiter = S.isNullOrEmpty(dlmtr)
                ? " -> "
                : dlmtr;

        StringBuilder sb = new StringBuilder();

        int count = 0;

        for (T item : items(false)) {
            if (count > 0) {
                sb.append(delimiter);
            }

            sb.append(extractor.apply(item.getValue()));

            count++;
        }

        return sb.toString();
    }

    /** */
    @Override
    public String toString() {
        return toString(null, null);
    }

    /** */
    private static <P, T extends ITreeDataSetItem<P>> List<T> buildPath(
            T firstItem,
            T lastItem
    ) {
        Objects.requireNonNull(firstItem, "'firstItem' is null");
        Objects.requireNonNull(lastItem, "'lastItem' is null");

        LinkedList<T> path = new LinkedList<>();

        T item = lastItem;

        while (item != null) {
            path.addFirst(item);

            if (item == firstItem) {
                return path;
            }

            item = cast(item.getParentItem());
        }

        throw new IllegalArgumentException(
                "First item is not an ancestor of last item"
        );
    }

    /** */
    private static <P, T extends ITreeDataSetItem<P>> List<T> buildPathToRoot(
            T lastItem
    ) {
        Objects.requireNonNull(lastItem, "'lastItem' is null");

        LinkedList<T> path = new LinkedList<>();

        T item = lastItem;

        while (item != null) {
            path.addFirst(item);
            item = cast(item.getParentItem());
        }

        return path;
    }

    /** */
    @SuppressWarnings("unchecked")
    private static <P, T extends ITreeDataSetItem<P>> T cast(
            ITreeDataSetItem<P> item
    ) {
        return (T) item;
    }
}