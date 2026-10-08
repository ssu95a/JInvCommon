package ru.inversion.dataset.mark;

public interface IMarkHandler<I> {

    void markItem( I item, boolean mark );

    void markAll( boolean mark );

    boolean isMarkItem( I item );

    default int getCount() {return -1;}
}
