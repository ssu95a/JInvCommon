package ru.inversion.utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectStreamClass;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.*;

/**
 *
 * @author ssu @
 */
public class U {

    /** */
    static public final Object EMPTY = new Object();
    
    /** */
	public static <T> T nvl ( T value, T valueIfNull ) {
		return value == null ? valueIfNull : value;
	}

    /** */
	public static <T> T nvl ( T value, Supplier<T> valueIfNull ) {
		return value == null ? valueIfNull.get() : value;
	}
    
	/** */
	public static <T> T nvl2( T value, T valueIfNotNull, T valueIfNull ) {
		return value == null ? valueIfNull : valueIfNotNull;
	}
    
	/** */
	public static <T> T nvl2( T value, Supplier<T> IfNotNull, Supplier<T> IfNull ) {
		return value == null ? IfNull.get() : IfNotNull.get();
	}

	/** */
	public static <T> T nvl2( T value, Supplier<T> IfNotNull, T valueIfNull ) {
		return value == null ? valueIfNull : IfNotNull.get();
	}
    
	/** */
	public static <T> T nullIf( T value, T nullValue ) {
		return value == null ? null : ( equals( value, nullValue ) ? null : value );
	}
    
	/** */
    @SafeVarargs
	public static <T> boolean in( T value, T ... av ) {
		
		if( value == null ) {
			for( T t : av ) {
				if( t == null )
					return true;
			}
		}
		else {
			for( T t : av ) {
				if( value.equals(t) )
					return true;
			}
		}
		return false;
	}

    /** */
    @SafeVarargs
    public static <T> boolean notIn( T value, T ... av ) {
        return !in( value, av );
    }

	/** */
	public static boolean inChar( char ch, char ... chrs ) {
		
        for( char t : chrs ) {
            if( t == ch )
                return true;
        }
		return false;
	}

    /** */
	public static boolean notInChar( char ch, char ... chrs ) {
		return !inChar( ch, chrs );
	}

    /** */
    public static boolean inIntValue( Number n, int ... ints ) {
        if( n == null )
            return false;
        return inInt( n.intValue(), ints );
    }

    /** */
    public static boolean inInt( int i, int ... ints ) {

        for( int j : ints ) {
            if( j == i )
                return true;
        }
        return false;
    }

    /** */
    public static boolean notInInt( int i, int ... ints ) {
        return !inInt( i, ints );
    }

    /** */
    public static boolean inLong( long i, long ... longs ) {

        for( long j : longs ) {
            if( j == i )
                return true;
        }
        return false;
    }

    /** */
    public static boolean notInLong( long i, long ... longs ) {
        return !inLong( i, longs );
    }


    /** */
    public static boolean inNum( Number n, Number ... nums ) {

        for( Number j : nums ) {

            if( n != null && j != null ) {
                if(n.getClass() != j.getClass())
                    throw new IllegalArgumentException("elements in 'nums' must be of the same type");
            }

            if( U.equals( n, j ) )
                return true;
        }
        return false;
    }

    /** */
    public static boolean notInNum( Number n, Number ... nums ) {
        return !inNum( n, nums );
    }


	/** Возвращает первое не Null значение из списка параметров */
    @SafeVarargs
	public static <T> T firstNotNull( T ... av ) {
		
        for( T t : av ) 
        {
			if( t != null )
				return t;
		}
		return null;
	}

    /** */
    public static <T> T firstNotNull( Iterator<T> iter ) {
        return iter == null ? null : firstNotNull( U.iterable(iter) );
    }

    /** */
    public static <T> T firstNotNull( Iterable<T> iter ) {
        if( iter == null )
            return null;
        for( T t : iter )
             if( t != null )
                 return t;
        return null;
    }

    /** возвращает признак, что в списке параметров есть null */
	public static  boolean containsNull( Object ... av ) {
		for( Object t : av ) {
			if( t == null )
				return true;
		}
		return false;
    }

    /** возвращает признак, что в списке параметров есть not null */
	public static  boolean containsNotNull( Object ... av ) {
		for( Object t : av ) {
			if( t != null )
				return true;
		}
		return false;
    }
    
    /** выполняет ф-цию, если параметр не null */
	public static <T,R> R callIfNotNull( T v, Function<T,R> f ) {
        return v == null ? null : f.apply(v);
    }

    /** выполняет ф-цию, если параметр не null */
	public static <T> void callIfNotNull( Consumer<T> f, T v ) {
        if( v != null ) f.accept(v);
    }

    /** выполняет ф-цию, если */
	public static <T,R> R callIf( Supplier<Boolean> s, T v, Function<T,R> f ) {
        return s.get( ) ? f.apply(v) : null;
    }
    
    /** выполняет ф-цию, если */
	public static <T> void callIf( Supplier<Boolean> s, Consumer<T> f, T v ) {
        if( s.get() ) f.accept(v);
    }
    
    /** */
    public static <T> Iterable<T> iterable( Iterator<T> iter ) 
    {
        return () -> iter;
    }
    
    /** */
    public static <T> void forEachWithIndex( Iterable<T> iterable,  BiConsumer<T,Integer> action ) {
        
        if( iterable != null ) {
            
            Objects.requireNonNull( action, "action is null");
            
            int i = 0;
            
            for( T t : iterable ) 
                action.accept(t, i++ );
            
        }
    }
    
    /** */
    public static <T> void forEachWithIndex( Iterator<T> iter,  BiConsumer<T,Integer> action ) {
        
        if( iter != null ) {
            forEachWithIndex( iterable(iter), action );
        }
    }
    
    /** */
    @SuppressWarnings("unchecked")
    public static <V,R> R decode( V value, Object ... vs ) {
        
        if( vs == null || vs.length < 2 )
            throw new IllegalArgumentException("vs size must be > 1");

        for( int i = 0; i < vs.length; i = i + 2 ) {

            V v = (V)vs[i];
            
            if( U.equals( value, v ) )
                if( i < vs.length-1 )
                    return (R)vs[i+1];
        }

        if( vs.length % 2 == 0 )
            return null;

        return (R)vs[vs.length-1];
    }

    /**
    public static <V,R> R decodeSup( V value, Supplier<R> ... vs ) {

            if( vs == null || vs.length < 2 )
                throw new IllegalArgumentException("vs size must be > 1");

            for( int i = 0; i < vs.length; i = i + 2 ) {

                V v = (V)vs[i];

                if( U.equals( value, v ) )
                    if( i < vs.length-1 )
                        return vs[i+1] == null ? null : vs[i+1].get();
            }

            if( vs.length % 2 == 0 )
                return null;

            return vs[vs.length-1] == null ? null : vs[vs.length-1].get();
        }
        */
    /** */
    public static <T> List<T> toList( Iterable<T> iterable ) {
        
        if( iterable == null )
            return Collections.<T>emptyList();
        
        List<T> retList = new ArrayList<>();
        
        for( T t : iterable )
            retList.add(t);
        
        return retList;
    }

    /** */
    public static <T> List<T> toList( Iterator<T> iter ) {
        return toList( U.iterable(iter) );
    }

    /** */
    public static boolean equals( Object o1, Object o2 ) {
        
        if( o1 == o2 ) 
            return true;
        
        if( o1 == null || o2 == null || o1.getClass() != o2.getClass() )
            return false;

        if( o1 instanceof BigDecimal ) {
            return ((BigDecimal)o1).compareTo( (BigDecimal)o2 ) == 0;
        }
        
        return o1.equals(o2);
    }

    /** */
    public static <T> Map<T,Object> toMap( Object ... kv ) {

        if( kv == null || kv.length < 2 )
            throw new IllegalArgumentException("'kv' size must be > 1");

        if( kv.length == 2 )
        {
            if( kv[0] == null )
                throw new IllegalArgumentException("'kv' first elem is null");

            return Collections.singletonMap( (T)kv[0], kv[1] );
        }

        Map<T,Object> retMap = null;

        for( int i = 0; i < kv.length; i = i + 2 ) {

            Object o = kv[i];

            if( o == null )
                continue;

            T k = (T)o;//.toString();

            if( i+1 == kv.length )
                break;

            Object v = kv[i+1];

            if( retMap == null )
                retMap = new HashMap<>();

            retMap.put( k, v );
        }
        return retMap == null ? Collections.emptyMap() : retMap;
    }

    /** */
    public static <T> Iterator<T> toIterator( T ... items ) {

        if( items.length == 0 )
            return Collections.emptyIterator();

        return new Iterator< T >() {

            private int i = 0;

            @Override
            public boolean hasNext() {
                return i < items.length;
            }

            @Override
            public T next() {

                if( !hasNext() )
                    throw new NoSuchElementException( "i=" + i );

                return items[i++];
            }
        };
    }

    /** */
    public static <T> Iterable<T> toIterable( T ... items ) {
        return iterable( toIterator(items) );
    }

    /** */
    public static <T> void forEach( Consumer<T> c, T ... items ) {

        if( items.length == 0 || c == null )
            return ;

        for( T t : items )
             c.accept(t);
    }

    /** */
    public static <I,O> Iterator<O> mapIter( Iterator<I> iter, Function<I,O> mapper ) {

        if( iter == null )
            return null;

        if( mapper == null )
            return (Iterator<O>)iter;

        return new Iterator< O >() {
            @Override
            public boolean hasNext() {
                return iter.hasNext();
            }

            @Override
            public O next() {
                return mapper.apply( iter.next() );
            }
        };
    }

    /** */
    public static <I,O> Iterator<O> mapIter( Iterator<I> iter, Function<I,O> mapper, Predicate<I> filter ) {

        if( iter == null )
            return null;

        final Function<I,O> m = U.nvl( mapper, i -> (O)i );
        final Predicate<I>  f = U.nvl( filter, i -> true );

        return new Iterator< O >() {

            I       next    = null;
            Boolean hasNext = null;

            @Override
            public boolean hasNext() {

                if( hasNext == null )
                    findNext();

                return hasNext;
            }

            @Override
            public O next() {

                if( !hasNext() )
                    throw new NoSuchElementException();

                O retValue = m.apply(next);

                findNext();

                return retValue;
            }

            private void findNext( ) {

                hasNext = false;

                while( iter.hasNext() )
                {
                    this.next = iter.next();

                    if( hasNext = f.test(this.next) )
                        return;
                }
            }
        };
    }

    /** */
    private static class T implements BiFunction<Character,Integer,String> {
        private StringBuilder sb;
        @Override
        public String apply( Character c, Integer n ) {

            if( c == '=' || Character.isSpaceChar(c) )
                return sb.toString();

            if( c == '"')
                throw new IllegalArgumentException( Tags.PRODUCT_LABEL + "Bad symbol '\"' at " + n + " position");

            if( sb == null )
                sb = new StringBuilder();

            sb.append(c);

            return null;
        }
    }

    /** */
    private static class Q implements BiFunction<Character,Integer,String> {
        boolean eol = false;
        private StringBuilder sb;
        @Override
        public String apply( Character c, Integer n ) {

            if( eol )
            {
                if( c == '=' || Character.isSpaceChar(c) )
                    return sb.toString();
                else
                    throw new IllegalArgumentException( Tags.PRODUCT_LABEL + "Bad symbol '" + c + "' at " + n + " position");
            }

            if( c == '"' )
                eol = sb != null;
            else
            {
                if( sb == null )
                    sb = new StringBuilder();
                sb.append(c);
            }
            return null;
        }
    }

    /** */
    private static class PIter implements Iterator<String> {

        final static String EQ = "=";

        final private String str;
        private BiFunction<Character,Integer,String> state;
        private String current;
        private int nChar = 0;

        /** */
        public PIter( String str ) {
            this.str = str;
            skip();
            getNext();
        }

        //
        private void skip()
        {
            char c;
            for( ; nChar < str.length(); nChar++ )
            {
                c= str.charAt(nChar);

                if( c != '=' && !Character.isSpaceChar(c) )
                    break;
            }
        }

        /** */
        @Override
        public boolean hasNext() {
            return current != null;
        }

        /** */
        @Override
        public String next() {

            if( !hasNext() )
                throw new NoSuchElementException();

            String s = current;
            getNext( );
            return s;
        }

        /** */
        private void getNext( )
        {
            current = null;

            while( nChar < str.length() )
            {
                char ch = str.charAt(nChar);

                nChar++;

                if( state == null )
                {
                    if( Character.isSpaceChar(ch) )
                        continue;

                    if( ch == '=' ) {
                        current = EQ;
                        break;
                    }

                    if( ch == '"' )
                        state = new Q();
                    else
                        state = new T();
                }

                current = state.apply( ch, nChar );

                if( current != null ) {

                    state = null;

                    nChar--;

                    break;
                }
            }//end while

            if( nChar == str.length() && state != null ) {
                current = state.apply( ' ', nChar );
                state = null;
            }
        }
    }

    public static Map<String,Object> parseArgsStr( String str )
    {
        if( S.isNullOrEmpty(str) )
            return Collections.emptyMap();

        Map<String,Object> retMap = new LinkedHashMap<>();

        boolean mode = true;
        String  kv = null;

        for( String s : iterable( new PIter(str) ) )
        {
            if( s == PIter.EQ ) {
                mode = false;
            }
            else
            {
                if( mode )
                {
                    kv = s;
                    retMap.put( kv, null );
                }
                else
                {
                    retMap.put( kv, s );
                    mode = true;
                }
            }
        }

        return retMap;
    }


    private static final int BUFFER_SIZE = 2048;

    public static long copyStreams( InputStream is, OutputStream os )
        throws IOException
    {
        long nread = 0L;
        byte[] buf = new byte[BUFFER_SIZE];
        int n;
        while( (n = is.read(buf)) > 0 )
        {
            os .write(buf, 0, n);
            nread += n;
        }
        return nread;
    }

    /** */
    public static Long getSerialVersionUID( Class clazz ) {

        //Objects.requireNonNull( clazz, "clazz is null");

        if( clazz == null )
            return null;

        ObjectStreamClass oc = ObjectStreamClass.lookup(clazz);
        if( oc != null )
            return oc.getSerialVersionUID();

        return null;
    }

    public static void main( String[] args ) {

        try {
            System.out.println( parseArgsStr("C:\\Users\\Parakhina.INVERSION\\Desktop\\reminder_new\\alt2.bat \"{USERID}\"") );
        }
        catch( Throwable th ) {
            th.printStackTrace();
        }
    }

}
