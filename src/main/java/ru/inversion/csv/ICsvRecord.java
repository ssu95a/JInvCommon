package ru.inversion.csv;

import java.util.Iterator;

/** */
public interface ICsvRecord {

    /** */
    ICsvMetaData getMetaData();

    /** */
    Iterator< String > values();

    /** */
    int size();

    /** */
    String get( int index   );

    /** */
    String get( String name );

    /** */
    long getRecordNum();
}
