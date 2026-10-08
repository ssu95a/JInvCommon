package ru.inversion.utils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;

/** */
public class DDpl implements Function<Object,Object>, AutoCloseable {

    private static class Item<T> {

        final private T value;

        private int count = 1;

        public Item( T value ) {
            this.value = value;
        }

        /** */
        public T value() {
            return value;
        }

        /** */
        public int count( ) {
            return count;
        }

        /** */
        public void increment() {
            count++;
        }
    }

    /** */
    @Override
    public Object apply( Object o ) {

        if( o == null )
            return o;

        if( o.getClass() == String.class )
            return get( (String)o );
        if( o.getClass() == BigDecimal.class )
            return get( (BigDecimal)o );
        if( o.getClass() == LocalDateTime.class )
            return get( (LocalDateTime)o );

        return o;
    }

    static final private String oneSymbolStrings[] = new String[256];

    static {
        for( int i = 32; i <= 255; i++ ) {
            oneSymbolStrings[i] = Character.valueOf((char)i).toString();
        }
    }

    private Map<String,Item<String>> stringSet = new HashMap<>();
    //private Map<String,Item<String>> stringSet = new HashMap<>();

    /** */
    private String get( String s ) {

        if( s.isEmpty() )
            return S.EMPTY_STRING;

        if( s.length() == 1 ) {

            char ch = s.charAt(0);

            if( ch <= 255 )
                return oneSymbolStrings[(int)ch];
        }

        if( s.length() > 1024 )
            return s;

        Item<String> item = stringSet.get(s);

        if( item == null ) {
            stringSet.put( s, new Item<>(s) );
            return s;
        }

        if( s != item.value() )
            item.increment();

        return item.value();
    }

    final private TreeMap<BigDecimal,Item<BigDecimal>> decimalSet = new TreeMap<>(BigDecimal::compareTo);
    /** */
    private BigDecimal get( BigDecimal d ) {

        Item<BigDecimal> item = decimalSet.get(d);

        if( item == null ) {
            decimalSet.put( d, new Item(d) );
            return d;
        }

        if( d != item.value() )
            item.increment();

        return item.value();
    }

    final private TreeMap<LocalDateTime,Item<LocalDateTime>> dateTimeSet = new TreeMap<>(LocalDateTime::compareTo);
    /** */
    private LocalDateTime get( LocalDateTime d ) {

        Item<LocalDateTime> item = dateTimeSet.get(d);

        if( item == null ) {
            dateTimeSet.put( d, new Item(d) );
            return d;
        }

        if( d != item.value() )
            item.increment();

        return item.value();
    }

    @Override
    public void close() {

        System.out.println("-----DDPL------");
        {
            System.out.println("String: ");
            System.out.println(" -- size: " + stringSet.size());
            Optional< Item< String > > ms = stringSet.values().stream().max(Comparator.comparingInt(Item::count));
            if(ms.isPresent())
                System.out.println(" -- max : count " + ms.get().count() + ", val - '" + ms.get().value() + "'");
            else
                System.out.println(" -- max : empty");
        }
        {
            System.out.println("Decimal: ");
            System.out.println(" -- size: " + decimalSet.size());
            Optional< Item< BigDecimal > > md = decimalSet.values().stream().max(Comparator.comparingInt(Item::count));
            if(md.isPresent())
                System.out.println(" -- max : count " + md.get().count() + ", val - '" + md.get().value() + "'");
            else
                System.out.println(" -- max : empty");
        }
        {
            System.out.println("LocalDateTime: ");
            System.out.println(" -- size: " + dateTimeSet.size());
            Optional< Item< LocalDateTime > > ml = dateTimeSet.values().stream().max(Comparator.comparingInt(Item::count));
            if(ml.isPresent())
                System.out.println(" -- max : count " + ml.get().count() + ", val - '" + ml.get().value() + "'");
            else
                System.out.println(" -- max : empty");
        }
    }
}
