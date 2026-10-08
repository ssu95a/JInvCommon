package ru.inversion.csv;

import java.io.File;
import java.util.List;

/** */
public interface ICsvMetaData {
    /** */
    String getFormat();
    /** */
    int getColumnCount();
    /** */
    List<String> getColumns();
    /** */
    String getColumnName( int index );
    /** */
    int getColumnIndex( String name );
    /** */
    File getCsvFile();
    /** */
    boolean hasColumnNames();
}
