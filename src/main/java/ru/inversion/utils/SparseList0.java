package ru.inversion.utils;

import java.util.*;

public class SparseList0<E> extends AbstractList<E> implements List<E>, RandomAccess {

    /** */
    static final private int DEFAULT_CACHE_SIZE = 3;

    /** */
    static final private int DEFAULT_ESTIMATED_PAGE_SIZE = 50;

    /** */
    private interface PageKey extends Comparable<PageKey> {

        /** */
        PageKey page();

        /** */
        boolean isIndex();
    }

    /** */
    private class IndexKey implements PageKey {

        final private int index;

        private IndexKey( int index ) {
            this.index = index;
        }
        @Override
        public PageKey page() {
            return null;
        }
        /** */
        public boolean isIndex() { return true; }
        /** */
        @Override
        public int compareTo( PageKey p ) {
            Page g = (Page)p;
            if( index >= g.page().getStartIndex() && index <= g.page().getEndIndex() )
                return 0;
            return index > g.page().getStartIndex() ? 1 : -1;
        }
    }

    /** */
    private class Page<E> implements PageKey {

        final private List<E> data;

        private int startIndex;

        private Page next;

        public Page( int startIndex, Collection<? extends E> c, Page next ) {

            this.startIndex = startIndex;

            if( !noCopyData || !(c instanceof ArrayList) ) {

                this.data = new ArrayList<>( Math.max( c.size(), estimatePageSize ) );

                final Iterator< ? extends E > iter = c.iterator();
                // copy
                for( int i = 0; i < c.size(); i++ ) {
                     this.data.add( iter.next() );
                }
            }
            else
            {
                this.data = (List)c;
            }

            this.next = next;
        }

        /** */
        public boolean containsIndex( int index ) {
            return index >= startIndex && index <= getEndIndex();
        }

        /** */
        void onRemoveElem( int nCount ) {
            startIndex -= nCount;
            if( next != null )
                next.onRemoveElem(nCount);
        }
        /** */
        void onAddElem( int nCount ) {
            startIndex += nCount;
            if( next != null )
                next.onAddElem(nCount);
        }

        /** */
        private int realIndex( int index ) {
            return index - startIndex;
        }

        /** */
        public PageKey key() {
            return this;
        }

        /** */
        public int getStartIndex() {
            return startIndex;
        }

        /** */
        public int getEndIndex() {
            return getStartIndex() + data.size() - 1;
        }

        /** */
        public E get( int index ) {
            return data.get( realIndex(index) );
        }

        /** */
        public boolean add( E elem ) {
            if( next != null )
                next.onAddElem(1);
            return data.add(elem);
        }

        /** */
        public void add( int index, E elem ) {

            data.add( realIndex(index), elem );

            if( next != null )
                next.onAddElem(1);
        }

        /** */
        public boolean addAll( Collection<? extends E> c ) {

            if( next != null )
                next.onAddElem( c.size() );

            return data.addAll( c );
        }

        /** */
        public boolean prepend( Collection<? extends E> c ) {

            if( next != null )
                next.onAddElem( c.size() );

            return data.addAll( 0, c );
        }

        /** */
        public boolean addAll( int index, Collection<? extends E> c ) {

            if( next != null )
                next.onAddElem( c.size() );

            return data.addAll( realIndex(index), c );
        }

        /** */
        public E remove( int index ) {

            if( next != null )
                next.onRemoveElem(1);

            return data.remove( realIndex(index) );
        }

        /** */
        public boolean remove( Object o ) {

            boolean remove = data.remove(o);

            if( remove && next != null )
                next.onRemoveElem(1);

            return remove;
        }
        /** */
        public E set( int index, E e ) {
//            if( index == getEndIndex() )
//                add(e);
//            else
//                return data.set( realIndex(index), e );
//
//            return null;
            return data.set( realIndex(index), e );

        }

        /** */
        public boolean removeAll( Collection< ? > c ) {

            final int before = data.size();

            data.removeAll(c);

            if( before > data.size() ) {

                if( next != null )
                    next.onRemoveElem( before - data.size() );

                return true;
            }
            else
                return false;
        }

        /** */
        public boolean retainAll( Collection< ? > c ) {

            final int before = data.size();

            data.retainAll(c);

            if( before > data.size() ) {

                if( next != null )
                    next.onRemoveElem( before - data.size() );

                return true;
            }
            else
                return false;
        }

        /** */
        public boolean isEmpty() {
            return data.isEmpty();
        }

        /** */
        public int size() {
            return data.size();
        }

        /** */
        public boolean contains( Object o ) {
            return data.contains(o);
        }

        /** */
        public int indexOf( Object o ) {

            int idx = data.indexOf(o);

            if( idx != -1 )
                idx = idx + getStartIndex();

            return idx;
        }

        /** */
        @Override
        public Page page() {
            return this;
        }

        @Override
        public boolean isIndex() {
            return false;
        }
        /** */
        public int compareTo( PageKey o ) {

            if( o.isIndex() )
                return o.compareTo(this);

            return Integer.compare( this.getStartIndex(), ((Page)o.page()).getStartIndex() );
        }
    }

    /** */
    private class Cache {
        final private int[] indexes;
        final private Object[] values;
        private boolean wasValue = false;
        public Cache( int size ) {
            indexes = new int[size];
            values  = new Object[size];
        }

        /** */
        public int size() { return indexes.length; }

        /** */
        public void put( int index, E value ) {

            if( indexes.length == 0 )
                return;

            for( int i = 0; i < indexes.length; i++ ) {

                if( indexes[i] == -1) {
                    indexes[i] = index;
                    values [i] = value;
                    return;
                }
            }

            indexes[0] = index;
            values [0] = value;

            for( int i = indexes.length-1; i > 1 ; i-- ) {
                 indexes[i] = indexes[i-1];
                 values [i] = values [i-1];
            }
        }

        /** */
        public E get( int index ) {

            if( indexes.length == 0 )
                return null;

            int i = 0;
            for( int idx : indexes ) {
                wasValue = idx == index;
                if( wasValue)
                    return (E)values[i];
                i++;
            }
            return null;
        }

        /** */
        public boolean wasValue() {
            return wasValue;
        }

        public void clear() {

            if( indexes.length == 0 )
                return;

            Arrays.fill( indexes, -1 );
            Arrays.fill( values , null );
        }
    }

    private Page<E> root;
    private Page<E> last;
    private int size = 0;

    final private TreeMap<PageKey, Page<E>> pages = new TreeMap<>();
    final private Cache cache;
    final private int estimatePageSize;
    final private boolean noCopyData;

    /** */
    public SparseList0( ) {
        this( DEFAULT_ESTIMATED_PAGE_SIZE, DEFAULT_CACHE_SIZE, true );
    }

    public SparseList0( int estimatePageSize, int cacheSize, boolean noCopyData ) {
        this.estimatePageSize = estimatePageSize;
        this.cache            = new Cache( cacheSize <= 0 ? 0 : cacheSize );
        this.noCopyData       = noCopyData;
    }

    /** */
    public boolean isEmpty() {
        return size == 0;
    }

    /** */
    private void initRoot( Collection<? extends E > c, int startIndex ) {
        root = new Page<>( startIndex, c, null );
        last = root;
        pages.put( root.key(), root );
    }

    /** */
    private int getMaxIndex() {
        return last.getEndIndex();
    }

    /** */
    private int getMinIndex() {
        return root.getStartIndex();
    }

    private Page<E> getPage( int index, boolean doThrow ) {

        final Page page = pages.get( new IndexKey(index) );

        if( page == null && doThrow ) {

//            System.out.println(" --- map --- ");
//            pages.values().forEach( (p)-> System.out.println( "idx: " + p.getStartIndex() + ", size: " + p.size() ) );
//
//            System.out.println(" --- link --- ");
//            for( Page p = root; p != null; p = p.next )
//                System.out.println( "idx: " + p.getStartIndex() + ", size: " + p.size() );

            throw new IndexOutOfBoundsException("Bad index " + index + ", minIndex=" + getMinIndex() + ", maxIndex=" + getMaxIndex() );
        }

        return page;
    }

    /** */
    private void checkIndex( int index ) {
        if( index < 0 )
            throw new IndexOutOfBoundsException("Bad index: " + index );
    }

    /** */
    private List<E> toList( E e ) {
        return Collections.singletonList(e);
    }

    /** */
    @Override
    public boolean add( E e ) {
        addAll( isEmpty() ? 0 : getMaxIndex() + 1, toList(e) );
        return true;
    }

    /** */
    @Override
    public void add( int index, E e ) {
        addAll( index, toList(e) );
    }

    /** */
    @Override
    public boolean addAll( Collection< ? extends E > c ) {
        return addAll( isEmpty() ? 0 : getMaxIndex() + 1, c );
    }

    /** */
    @Override
    public boolean addAll( int index, Collection< ? extends E > c ) {

        checkIndex( index );

        if( c.isEmpty() )
            return false;

        modCount += c.size();

        if( isEmpty() )
        {
            initRoot( c, index );
        }
        else if( index > getMaxIndex() )
        {
            if( index == getMaxIndex() + 1 && last.size() + c.size() <= estimatePageSize ) {
                last.addAll(c);
            }
            else
            {
                Page newPage = new Page( index, c, null);
                last.next = newPage;
                last      = newPage;
                pages.put( newPage.key(), newPage );
            }

        }
        else if( index < getMinIndex() )
        {
            if( index == getMinIndex() - 1 && root.size() + c.size() <= estimatePageSize )
            {
                root.prepend(c);
            }
            else
            {
                Page newPage = new Page( index, c, null );
                root.next = newPage;
                root      = newPage;
                pages.put( newPage.key(), newPage );
            }
        }
        else
        {
            Page<E> page = getPage( index, false );

            if( page != null )
            {
                page.addAll( index, c );
            }
            else
            {
                final Map.Entry<PageKey,Page<E>> entry = pages.lowerEntry( new IndexKey(index) );

                assert entry != null : "Error on search algo!";

                Page prev = entry.getValue(),
                     next = prev.next;

                Page newPage = new Page( index, c, next );
                prev.next = newPage;

                pages.put( newPage.key(), newPage );
            }
        }

        size += c.size();

        return true;
    }


    /** */
    @Override
    public E remove( int index ) {

        if( isEmpty() )
            return null;

        checkIndex(index);

        Page<E> page = getPage( index, false );

        if( page == null )
            return null;

        modCount++;

        cache.clear();

        E ret = page.remove(index);

        size--;

        return ret;
    }

    /** */
    private Page getPrevPage( Page page ) {

        Page prev = null;

        for( Page p = root; p != null; p = p.next ) {

            if( p == page )
                return prev;

             prev = p;
        }

        return prev;
    }

    /** */
    private void removePage( Page page ) {

        pages.remove( page );

        if( page == root )
        {
            root = root.next;
            root = null;
        }
        else
        {
            Page prev = getPrevPage( page );

            if( prev != null )
                prev.next = page.next;
        }
    }

    /** */
    @Override
    public boolean remove( Object o ) {

        if ( isEmpty() )
            return false;

        cache.clear();

        for( Page p : pageIterable() )
        {
             if( p.remove(o) ) {
                 modCount++;
                 size--;
                 if( p.isEmpty() )
                     removePage(p);
                 return true;
             }
        }

        return false;
    }

    public boolean rAll( Collection< ? > c, boolean remove ) {

        boolean ret = false;

        List<Page> emptyPages = new LinkedList<>();

        final int before = size;

        size = 0;

        for( Page p : pageIterable() ) {

            if( remove )
                if( p.removeAll(c) )
                    ret = true;
            else
                if( p.retainAll(c) )
                    ret = true;

            if( p.isEmpty() )
                emptyPages.add(p);

            size += p.size();
        }

        if( ret )
        {
            modCount += before - size;

            cache.clear();

            if( !emptyPages.isEmpty() )
            {
                emptyPages.forEach( (p)->pages.remove( p ) );

                Page prev = null;
                Page p    = root;

                while( p!= null ) {

                     if( emptyPages.remove(p) ) {

                         if( prev != null )
                             prev.next = p.next;
                     }
                     else
                          prev = p;

                     p = p.next;

                }//end while
            }
        }//end if

        return ret;
    }

    @Override
    public boolean removeAll( Collection<?> c ) {
        return rAll( c, true );
    }
    /** */
    @Override
    public boolean retainAll( Collection<?> c ) {
        return rAll( c, false );
    }

    @Override
    public void clear() {

        modCount += size;

        pages.clear();
        root = null;
        last = null;
        cache.clear();
        size = 0;
    }

    /** */
    @Override
    public E set( int index, E e ) {

        checkIndex( index );

        modCount++;

        Page<E> page = null;

        if( !isEmpty() && ( page = getPage( index, false ) ) !=null ) {
             return page.set( index, e );
        }
        else
        {
            add( index, e );
        }

        return null;
    }

    /** */
    private Iterable<Page<E>> pageIterable() {
        return ()->pages.values().iterator();
    }

    /** */
    @Override
    public E get( int index ) {

        checkIndex( index );

        if( isEmpty() )
            throw new IndexOutOfBoundsException( "Bad index: " + index + ", list is empty" );

        if( root.containsIndex(index) )
            return root.get( index );

        E value = cache.get( index );

        if( !cache.wasValue() ) {
            value = getPage(index, true).get(index);
            cache.put(index, value);
        }

        return value;
    }

    @Override
    public boolean contains( Object o ) {
        for( Page p : pageIterable() )
             if( p.contains(o) )
                 return true;
        return false;
    }

    @Override
    public Iterator<E> iterator() {

        if( isEmpty() )
            return Collections.emptyIterator();

        return new Iterator<E>() {

            final private Iterator<Page<E>> pageIter = pageIterable().iterator();
            private Iterator<E> iter = pageIter.next().data.iterator();

            private boolean hasNext = false;
            private E currentElem;

            {
                iterate();
            }

            private void iterate( ) {

                hasNext = false;

                while( !hasNext ) {

                    hasNext = iter.hasNext();

                    if( hasNext )
                        currentElem = iter.next();
                    else {

                        if( pageIter.hasNext() )
                            iter = pageIter.next().data.iterator();
                        else
                            break;
                    }
                }// while
            }

            @Override
            public boolean hasNext() {
                return hasNext;
            }

            @Override
            public E next() {

                if( !hasNext() )
                    throw new NoSuchElementException();

                E ret = currentElem;
                iterate();
                return ret;
            }
        };
    }

    @Override
    public Object[] toArray() {

        if( isEmpty() )
            return new Object[0];

        Object[] arr = new Object[size()];

        int i = 0;

        for( Page p : pageIterable() ) {
             for( Object e : p.data ) {
                  arr[i++] = e;
             }
        }

        return arr;
    }

    @Override
    public <T> T[] toArray( T[] a ) {

        if( isEmpty() )
            return a;

        final int size = size();

        if( a.length < size )
            a = (T[])java.lang.reflect.Array.newInstance( a.getClass().getComponentType(), size );

        int i = 0;

        Object[] result = a;

        for( Page p : pageIterable() ) {
            for( Object e : p.data ) {
                 result[i++] = e;
            }
        }

        if( a.length > size )
            a[size] = null;

        return a;
    }

    /** */
    @Override
    public boolean containsAll( Collection< ? > c ) {

        for( Object e : U.iterable( iterator() ) )
            if( !contains(e) )
                return false;
        return true;
    }

    /** */
    @Override
    public int size() {
        return size;
    }

    @Override
    public int indexOf( Object o ) {

        int idx = -1;

        if( !isEmpty() )
        {
            for(Page p : pageIterable() ) {
                idx = p.indexOf(o);
                if( idx != -1 )
                    break;
            }
        }

        return idx;
    }

    @Override
    public int lastIndexOf( Object o ) {

        int idx = -1;

        if( !isEmpty() )
        {
            int i;

            for( Page p : pageIterable() ) {
                 i = p.indexOf(o);
                 if( i > 0 )
                     idx = i;
            }
        }

        return idx;
    }
/*
    @Override
    public ListIterator< E > listIterator() {
        return null;
    }

    @Override
    public ListIterator< E > listIterator( int index ) {
        return null;
    }
*/

    @Override
    public List< E > subList( int fromIndex, int toIndex ) {
        throw new UnsupportedOperationException("subList");
    }


    /** */
    public static class Builder<T> {

        private boolean noCopyData        = true;
        private int estimatePageSize      = DEFAULT_ESTIMATED_PAGE_SIZE;
        private int cacheSize             = DEFAULT_CACHE_SIZE;
        private Collection<T> initialData = null;

        public Builder noCopyData( boolean v ) {
            this.noCopyData = v;
            return this;
        }
        public Builder estimatePageSize( int v ) {
            this.estimatePageSize = v;
            return this;
        }
        public Builder cacheSize( int v ) {
            this.cacheSize = v;
            return this;
        }
        public Builder initialData( Collection<T> c ) {
            this.initialData = c;
            return this;
        }

        public SparseList0<T> build() {

            final int pageSize = estimatePageSize <= 0 ? DEFAULT_ESTIMATED_PAGE_SIZE : estimatePageSize;

            final SparseList0< T > list = new SparseList0<>(
                pageSize,
                cacheSize < 0 ? DEFAULT_CACHE_SIZE : cacheSize,
                noCopyData
            );

            if( initialData != null )
            {
                if( initialData.size() / 2 > pageSize ) {
                    initialData.forEach( (e)->list.add(e) );
                }
                else
                {
                    list.addAll( initialData );
                }
            }

            return list;
        }
    }

}
