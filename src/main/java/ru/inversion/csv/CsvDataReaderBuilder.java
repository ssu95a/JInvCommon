package ru.inversion.csv;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.csv.DuplicateHeaderMode;
import ru.inversion.meta.EntityMetadataFactory;
import ru.inversion.meta.IEntityMetaData;
import ru.inversion.meta.IEntityProperty;
import ru.inversion.utils.S;
import ru.inversion.utils.Tags;
import ru.inversion.utils.U;

import java.beans.IntrospectionException;
import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

/** */
public class CsvDataReaderBuilder<P> {

    public enum FirstRecordModeEnum
    {
        HEADER,
        DATA,
        SKIP
    }

    private Object source;

    private List<String> headerColumnList;

    private FirstRecordModeEnum firstRecMode;

    private Function<ICsvRecord, P> mapper;

    private String format;

    private Class<P> rowClass;

    private boolean headerIgnoreCase = true;

    private CSVFormat csvFormat;

    private char delimiter = 0;

    public CsvDataReaderBuilder<P> csvFormat( CSVFormat v ) {
        this.csvFormat = v;
        return this;
    }

    public CsvDataReaderBuilder<P> delimiter( char ch ) {
        this.delimiter = ch;
        return this;
    }

    public CsvDataReaderBuilder<P> headerIgnoreCase( boolean v ) {
        this.headerIgnoreCase = v;
        return this;
    }

    public CsvDataReaderBuilder<P> file( File file ) {
        this.source = file;
        return this;
    }

    public CsvDataReaderBuilder<P> file( Path file ) {
        this.source = file;
        return this;
    }

    public CsvDataReaderBuilder<P> text( String text ) {
        this.source = text;
        return this;
    }

    public CsvDataReaderBuilder<P> reader( Reader reader ) {
        this.source = reader;
        return this;
    }

    public CsvDataReaderBuilder<P> iostream( InputStream is ) {
        this.source = is;
        return this;
    }

    public CsvDataReaderBuilder<P> headerColumnList( List<String> headerColumnList ) {
        this.headerColumnList = headerColumnList;
        return this;
    }

    public CsvDataReaderBuilder<P> headerColumnList( String ... h ) {
        this.headerColumnList = Arrays.asList(h);
        return this;
    }

    public CsvDataReaderBuilder<P> firstRecordMode( FirstRecordModeEnum firstRecMode ) {
        this.firstRecMode = firstRecMode;
        return this;
    }

    public CsvDataReaderBuilder<P> mapper( Function<ICsvRecord, P> mapper ) {
        this.mapper = mapper;
        return this;
    }

    public CsvDataReaderBuilder<P> format( String format ) {
        this.format = format;
        return this;
    }

    public CsvDataReaderBuilder<P> rowClass( Class<P> rowClass ) {
        this.rowClass = rowClass;
        return this;
    }

    /** */
    private CSVFormat initFormat() throws CsvException
    {
        if( csvFormat != null )
            return csvFormat;

        try {

            CSVFormat csvFormat;

            try {
                csvFormat =
                    S.isNullOrEmpty(format) ?
                        CSVFormat.DEFAULT
                        :
                        CSVFormat.valueOf(format);
            }
            catch( Throwable th ) {
                throw new IllegalStateException( Tags.PRODUCT_LABEL + "Illegal 'format' parameter value: " + format, th );
            }

            CSVFormat.Builder fBuilder = CSVFormat.Builder.create(csvFormat);

            if( firstRecMode == FirstRecordModeEnum.HEADER )
            {
                //csvFormat = csvFormat.withFirstRecordAsHeader();
                fBuilder.setHeader().setSkipHeaderRecord(true);
            }
            else
            {
                if( firstRecMode == FirstRecordModeEnum.SKIP )
                    //csvFormat = csvFormat.withSkipHeaderRecord();
                    fBuilder.setSkipHeaderRecord(true);

                if( headerColumnList != null )
                    //csvFormat = csvFormat.withHeader( headerColumnList.toArray( new String[1] ) );
                    fBuilder.setHeader( headerColumnList.toArray( new String[1] ));
            }

            //if( csvFormat.getAllowDuplicateHeaderNames() )
            //    csvFormat = csvFormat.withAllowDuplicateHeaderNames(false);
            fBuilder.setDuplicateHeaderMode(DuplicateHeaderMode.DISALLOW);

            //if( headerIgnoreCase && !csvFormat.getIgnoreHeaderCase())
            //    csvFormat = csvFormat.withIgnoreHeaderCase();
            fBuilder.setIgnoreHeaderCase(headerIgnoreCase);

            if( delimiter != 0 )
                fBuilder.setDelimiter(delimiter);

            return fBuilder.build();
        }
        catch( Throwable th ) {
            throw new CsvException( Tags.PRODUCT_LABEL + "Error on init CSVFormat object", th );
        }
    }

    /** */
    private Reader initReader( ) throws FileNotFoundException {

        if( source == null )
            throw new IllegalStateException( Tags.PRODUCT_LABEL + "No source for CSV reader" );

        Reader reader = null;

        if( source instanceof File)
            reader = new FileReader((File)source);
        else if( source instanceof String )
            reader = new StringReader( (String)source );
        else if( source instanceof Reader )
            reader = (Reader)source;
        else if( source instanceof InputStream )
            reader = new InputStreamReader((InputStream)source);

        if( reader == null )
            throw new IllegalStateException( Tags.PRODUCT_LABEL + "Unknown type for CSV source" );

        return reader;
    }

    /** */
    private Function< ICsvRecord,P> initMapper( ICsvMetaData metaData ) throws IntrospectionException {

        if( mapper != null )
            return mapper;

        if( rowClass == null )
            return r -> (P)r;

        if( !metaData.hasColumnNames() )
            throw new IllegalStateException( Tags.PRODUCT_LABEL + "No header columns, 'mapper' must be set!");

        final List<IEntityProperty<P,?>>  propertyList = new ArrayList<>();

        IEntityMetaData<P> md = EntityMetadataFactory.getEntityMetaData( rowClass );

        if( headerIgnoreCase )
        {
            TreeMap<String, IEntityProperty<P,?>> m = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            m.putAll( md.getPropertiesMap() );

            metaData.getColumns().forEach(new Consumer< String >() {
                @Override
                public void accept( String s ) {
                    propertyList.add( m.get(s) );
                }
            });
        }
        else
        {
            metaData.getColumns().forEach(new Consumer< String >() {
                @Override
                public void accept( String s ) {
                    propertyList.add( md.getProperty(s) );
                }
            });
        }

        final Class clazz = rowClass;

        return new Function< ICsvRecord, P >() {
            @Override
            public P apply( ICsvRecord record ) {

                try {

                    P pojo = (P)clazz.newInstance();

                    for( int i = 0; i < propertyList.size(); i++ )
                    {
                        String v = record.get(i);
                        if( v != null && propertyList.get(i) != null )
                            propertyList.get(i).invokeSetter( pojo, v );
                    }

                    return pojo;
                }
                catch( Throwable th ) {
                    throw new RuntimeException( Tags.PRODUCT_LABEL + "Error on map csv record to " + clazz.getSimpleName(), th );
                }
            }
        };

    }

    /** */
    public ICsvDataReader<P> build( ) throws CsvException
    {
        try {

            final Reader    csvReader = initReader();

            final CSVFormat csvFormat = initFormat( );

            final CSVParser csvParser;
            try {
                csvParser = csvFormat.parse( csvReader );
            }
            catch( Throwable th ) {
                throw new CsvException(th);
            }

            final ICsvMetaData csvMetaData;

            if( firstRecMode == FirstRecordModeEnum.HEADER || this.headerColumnList != null )
                csvMetaData = new MetaDataImpl( format, csvParser, source instanceof File ? (File)source : null );
            else
                csvMetaData = new MetaDataImpl( format, source instanceof File ? (File)source : null );

            return new ReaderImpl( csvMetaData, csvParser, initMapper(csvMetaData) );
        }
        catch( Throwable th ){
            throw new CsvException(Tags.PRODUCT_LABEL + "Error on create CSV reader", th );
        }
    }

    /** */
    private static class MetaDataImpl implements ICsvMetaData {

        final private CSVParser csvParser;
        final private String    csvFormat;
        final private File      csvFile;

        private MetaDataImpl( String csvFormat, CSVParser csvParser, File csvFile ) {
            this.csvParser = csvParser;
            this.csvFormat = csvFormat;
            this.csvFile   = csvFile;
        }
        private MetaDataImpl( String csvFormat, File csvFile ) {
            this.csvParser = null;
            this.csvFormat = csvFormat;
            this.csvFile   = csvFile;
        }
        @Override
        public String getFormat() { return csvFormat; }
        @Override
        public int getColumnCount() { return hasColumnNames() ? csvParser.getHeaderNames().size() : -1;}
        @Override
        public List<String> getColumns() { return hasColumnNames() ?  csvParser.getHeaderNames() : null;}
        @Override
        public String getColumnName( int index ) { return hasColumnNames() ? csvParser.getHeaderNames().get(index) : null; }
        @Override
        public int getColumnIndex( String name ) { return hasColumnNames() ? csvParser.getHeaderMap().get(name) : -1;}
        @Override
        public File getCsvFile() {return csvFile;}
        @Override
        public boolean hasColumnNames() {return csvParser != null; }
    }

    /** */
    private static class RecordImpl implements ICsvRecord {

        final private ICsvMetaData metaData;
        final private CSVRecord    csvRecord;

        private RecordImpl( ICsvMetaData metaData, CSVRecord csvRecord ) {
            this.metaData  = metaData;
            this.csvRecord = csvRecord;
        }
        @Override
        public ICsvMetaData getMetaData() {
            return metaData;
        }
        @Override
        public Iterator<String> values() {
            return csvRecord.iterator();
        }
        @Override
        public int size() {
            return csvRecord.size();
        }
        @Override
        public String get( int index ) {
            return csvRecord.get(index);
        }
        @Override
        public String get( String name ) {
            return csvRecord.get(name);
        }
        @Override
        public long getRecordNum() {
            return csvRecord.getRecordNumber();
        }
        @Override
        public String toString() {
            return csvRecord.toString();
        }
    }

    /** */
    private static class ReaderImpl<P> implements ICsvDataReader<P> {

        final private ICsvMetaData metaData;
        final private CSVParser    csvParser;
        final private Function< ICsvRecord, P> mapper;

        /** */
        private ReaderImpl( ICsvMetaData metaData, CSVParser csvParser, Function< ICsvRecord, P> mapper ) {
            this.metaData  = metaData;
            this.csvParser = csvParser;
            this.mapper    = mapper == null ? r -> (P)r : mapper;
        }

        /** */
        @Override
        public ICsvMetaData getMetaData() {
            return metaData;
        }

        /** */
        @Override
        public boolean isClosed() {
            return this.csvParser.isClosed();
        }

        /** */
        @Override
        public void close() throws Exception {
            this.csvParser.close();
        }

        /** */
        @Override
        public Iterable<P> data() {

            final Iterator<P> iter = new Iterator<P>() {

                final Iterator<CSVRecord> csvIterator = csvParser.iterator();

                @Override
                public boolean hasNext() {
                    return csvIterator.hasNext();
                }
                @Override
                public P next() {
                    return mapper.apply( new RecordImpl( metaData, csvIterator.next() ) );
                }
            };

            return U.iterable( iter );
        }
    }

//    /** */
//    public static void main( String[] args ) {
//        try {
//
//            CsvDataReaderBuilder b = new CsvDataReaderBuilder();
//            final Iterable data = b.firstRecordMode (FirstRecordModeEnum.HEADER)
//                    .reader (new StringReader("TABNUM;BSOT_NAME;OKLAD;MAINWORK;DATDOL\n" +
//                            "63460;Lapteva Nastya;1000;Y;09.07.2017\n" +
//                            "63460;Lapteva Nastya;3000;N;11.07.2017\n" +
//                            "6370;Globushkin Test;2000.2;Y;10.07.2019\n" +
//                            ";;;;"))
//                    .csvFormat( CSVFormat.Builder.create (CSVFormat.DEFAULT).setHeader().setSkipHeaderRecord(true).setDelimiter (";").build ()) // ДЛЯ ПРИМЕРА ДЕФОТНЫЙ, А ВООБЩЕ С ЛЮБЫМ НЕ РАБОТАЕТ
//                    .rowClass (POkladImport.class)
//                    .build ()
//                    .data ();
//
//            data.forEach( System.out::println );
//        }
//        catch( Throwable th ) {
//            th.printStackTrace();
//        }
//    }
}
