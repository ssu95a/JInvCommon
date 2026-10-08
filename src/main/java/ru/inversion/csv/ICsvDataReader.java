package ru.inversion.csv;

import java.io.File;

/** */
public interface ICsvDataReader<P> extends AutoCloseable {
    /** */
    ICsvMetaData getMetaData();
    /** */
    Iterable<P> data();
    /** */
    boolean isClosed();

    /** */
    public static void main(String[] args) {

        final File file = new File( "d:\\XXI\\Закачка\\CSV\\data_20201225T0000_structure_20201225T0000.csv" );

        try {

            CsvDataReaderBuilder b = new CsvDataReaderBuilder();
            final Iterable data = b.firstRecordMode(CsvDataReaderBuilder.FirstRecordModeEnum.HEADER)
                    .file(file)
                    //.rowClass(PLDRoskazna.class)
                    //.format("xxx")
                    //.headerColumnList( "ID","F1","F2" )
                    .build().data();

            for( Object o : data )
            {
                System.out.println(o.toString());
                break;
            }


        } catch (Throwable th ) {
            th.printStackTrace();
        }
    }

}

