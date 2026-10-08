package ru.inversion.utils;

import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class Test_SparseList0 {

    final List<Integer> spList = new SparseList0<>();
    final List<Integer> arList = new ArrayList<>();

    // вызывается один раз на класс-тест перед запуском теста
    @BeforeAll
    static void init() {
    }

    // вызывается один раз на класс-тест по завершению тестирования
    @AfterAll
    static void end() {
    }

    @BeforeEach
    void beforeEach() {
        spList.clear();
        arList.clear();
    }

    /** */
    @Test
    @DisplayName( value = "Test SparseList: create" )
    void test_create( ) {

        final List<Integer> l = new SparseList0<>();

        assertTrue  ( l.isEmpty(), "isEmpty"   );
        assertEquals( 0, l.size(), "size == 0" );

        final Integer removeValue = l.remove(0);
        assertTrue  ( l.isEmpty(), "isEmpty"   );
        assertEquals( 0, l.size(), "size == 0" );
        assertTrue  ( removeValue != null      );
        assertEquals( 0, removeValue.intValue());
    }

    /** */
    @Test
    @DisplayName( value = "Test SparseList: add" )
    void test_add( ) {

        final List<Integer> l = new SparseList0<>();

        assertTrue  ( l.add(0)   , "add: return true" );
        assertFalse ( l.isEmpty(), "!isEmpty"  );
        assertEquals( 1, l.size(), "size == 1" );

        assertTrue  ( l.add(2)   , "add: return true" );
        assertFalse ( l.isEmpty(), "!isEmpty"  );
        assertEquals( 2, l.size(), "size == 2" );

        assertTrue  ( l.add(0)   , "add: return true" );
        assertFalse ( l.isEmpty(), "!isEmpty"  );
        assertEquals( 3, l.size(), "size == 3" );
    }

    @Test
    @DisplayName( value = "Test SparseList: addAll" )
    void test_addAll( ) {

        final List<Integer> data1 = Arrays.asList( 10, 20, 30, 40, 50 );
        final List<Integer> data2 = Arrays.asList(-10,-20,-30,-40, 50 );

        final List<Integer> l = new SparseList0<>();

        assertTrue  ( l.addAll( data1 ) , "addAll: return true" );
        assertFalse ( l.isEmpty(), "!isEmpty"  );
        assertEquals( data1.size(), l.size(), "size == 5" );

        assertTrue  ( l.addAll( data2 ) , "addAll: return true" );
        assertFalse ( l.isEmpty(), "!isEmpty"  );
        assertEquals( data1.size() + data2.size(), l.size(), "size == 10" );
    }

    @Test
    @DisplayName( value = "Test SparseList: add_index" )
    void test_addIndex( ) {

        final List<Integer> l = new SparseList0<>();

        l.add( 0, 1 );
        assertEquals( 1, l.size(), "size == 1" );
        assertEquals( 1, l.get(0).intValue(), "add" );

        l.add( 0, 0 );
        assertEquals( 2, l.size(), "size == 2" );
        assertEquals( 0, l.get(0).intValue(), "add" );

        l.add( 10, 10 );
        assertEquals( 3, l.size(), "size == 3" );
        assertEquals( 10,l.get(10).intValue(), "add:index" );

        l.add( 49, 49 );
        assertEquals( 4, l.size(), "size == 4" );
        assertEquals( 10,l.get(10).intValue(), "add:index" );

        l.add( 50, 50 );
        assertEquals( 5, l.size(), "size == 5" );
        assertEquals( 10,l.get(10).intValue(), "add:index" );

        l.add( 51, 51 );
        assertEquals( 6, l.size(), "size == 5" );
        assertEquals( 10,l.get(10).intValue(), "add:index" );
    }

    @Test
    @DisplayName( value = "Test SparseList: addAll_index" )
    void test_addAll_index( ) {

        final List<Integer> data1 = Arrays.asList( 10, 20, 30, 40, 50 );
        final List<Integer> data2 = Arrays.asList(-10,-20,-30,-40, 50 );

        final List<Integer> l = new SparseList0<>();

        assertTrue  ( l.addAll( 10, data1 ) , "addAll: return true" );
        assertFalse ( l.isEmpty(), "!isEmpty"  );
        assertEquals( data1.size(), l.size(), "size == 5" );

        assertTrue  ( l.addAll( 50, data2 ) , "addAll: return true" );
        assertFalse ( l.isEmpty(), "!isEmpty"  );
        assertEquals( data1.size() + data2.size(), l.size(), "size == 10" );
    }

    @Test
    @DisplayName( value = "Test SparseList: get" )
    void test_get( ) {

        final List<Integer> l = new SparseList0<>();
        final List<Integer> data1 = Arrays.asList( 0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100 );
        final List<Integer> data2 = Arrays.asList( 0, -10, -20, -30, -40, -50, -60, -70, -80, -90, -100 );

        l.addAll( data1 );
        l.addAll( 100, data2 );

        assertEquals( 0, l.get(0 ).intValue(), "get == 0" );
        assertEquals( 100, l.get(10).intValue(), "get == 100" );

        assertEquals( 0, l.get(100).intValue(), "get == 0" );
        assertEquals( -100, l.get(110).intValue(), "get == -100" );
    }

    /** */
    private void compare( boolean elems ) {

        assertEquals( arList.size(), spList.size(), "size" );
        assertEquals( arList.get(0), spList.get(0), "get 0" );
        assertEquals( arList.get( arList.size()-1 ), spList.get( spList.size()-1 ), "get last" );

        if( elems ) {
            for( int i = 0; i < arList.size(); i++ )
            {
                assertEquals( arList.get(i), spList.get(i), "get " + 1 );
            }
        }
    }

    /** */
    @Test
    @DisplayName( value = "Test SparseList: add" )
    void test_addCmp( ) {

        spList.add(10);
        arList.add(10);

        compare(false);

        for( int i = 0; i < 200; i++ ) {
            spList.add(i);
            arList.add(i);
        }

        compare(false);
    }

    @Test
    @DisplayName( value = "Test SparseList: addAll" )
    void test_addAllCmp( ) {

        spList.addAll( Arrays.asList(10,20,30,40,50) );
        arList.addAll( Arrays.asList(10,20,30,40,50) );

        compare(false);

        spList.add(1000);
        arList.add(1000);

    }

    @Test
    @DisplayName( value = "Test SparseList: add_Index" )
    void test_addIndexCmp( ) {

        spList.add( 0, 0 );
        arList.add( 0, 0 );

        compare(false);

        spList.add( spList.size()-1, -1 );
        arList.add( arList.size()-1, -1 );

        compare(false);

        int index = arList.size()/2;

        spList.add( index, -1 );
        arList.add( index, -1 );

        compare(false);

        spList.add( index+2, -10 );
        arList.add( index+2, -10 );

        compare(false);

//        spList.add( index+1000, -1001 );
//
//        spList.add( index+2500, -1000 );
//        spList.add( index+2500, -2000 );
//        spList.add( index+2400, -2000 );

    }

    @Test
    @DisplayName( value = "Test SparseList: set" )
    void test_set( ) {

        IntStream.range( 0, 100 ).forEach( (i)->spList.add(i) );
        IntStream.range( 0, 100 ).forEach( (i)->arList.add(i) );

        spList.set( 10, -10000 );
        arList.set( 10, -10000 );

        compare(false);

        for( int i = 0; i < 50; i++ ) {
            if( i != 10 ) {
                spList.set(i, -200);
                arList.set(i, -200);
            }
        }

        compare(true);
    }

    @Test
    @DisplayName( value = "Test SparseList: remove" )
    void test_remove( ) {

        IntStream.range( 0, 300 ).forEach( (i)->spList.add(i) );
        IntStream.range( 0, 300 ).forEach( (i)->arList.add(i) );

        spList.remove( 10 );
        arList.remove( 10 );

        assertEquals( arList.size(), spList.size(), "size" );
        assertEquals( arList.get(10), spList.get(10), "get" );

        for( int i = 0; i < 100; i++ ) {
            spList.remove( i );
            arList.remove( i );
        }

        spList.remove( Integer.valueOf(280) );
        arList.remove( Integer.valueOf(280) );

        spList.removeAll( Arrays.asList( Integer.valueOf(281), Integer.valueOf(282) ));
        arList.removeAll( Arrays.asList( Integer.valueOf(281), Integer.valueOf(282) ));

        assertEquals( arList.size(), spList.size(), "size" );
        assertEquals( arList.get(20), spList.get(20), "get" );
        assertIterableEquals( arList, spList, "ar" );

    }

    @Test
    @DisplayName( value = "Test test_removaAll" )
    void test_removaAll() {

        IntStream.range( 0, 110 ).forEach( (i)->spList.add(i) );
        IntStream.range( 0, 110 ).forEach( (i)->arList.add(i) );

        assertEquals( arList.size(), spList.size(), "size" );
        assertEquals( arList.get(20), spList.get(20), "get" );

        spList.addAll( Arrays.asList( 10, 20, 20, 40, 50 ) );
        arList.addAll( Arrays.asList( 10, 20, 20, 40, 50 ) );

        assertEquals( arList.size(), spList.size(), "size" );
        assertEquals( arList.get(20), spList.get(20), "get" );

//        assertEquals (
//            arList.removeAll( Arrays.asList( 10, 20, 50 ) ),
//            spList.removeAll( Arrays.asList( 10, 20, 50 ) ),
//            "result removeAll"
//        );

        assertEquals (
            spList.removeAll( IntStream.range(0, 100).mapToObj((i) -> Integer.valueOf(i)).collect(Collectors.toList()) ),
            arList.removeAll( IntStream.range(0, 100).mapToObj((i) -> Integer.valueOf(i)).collect(Collectors.toList()) ),
            "result removeAll"
        );


        //spList.get(20);

        assertEquals( arList.size(),  spList.size(), "size" );
        assertEquals( arList.get(20), spList.get(20), "get" );

    }

    @Test
    @Disabled
    @DisplayName( value = "Test test_Size" )
    void test_Size() {

    }

//    @Test
//    @Deprecated
    @DisplayName( value = "Test Test_SparseList" )
    @Disabled
    void testSize() {

        List<Integer> arList = IntStream.range( 0, 1000 ).mapToObj( (i)->Integer.valueOf(i) ).collect(Collectors.toList());


        // spList.addAll( IntStream.range( 0, 1000 ).mapToObj( (i)->Integer.valueOf(i) ).collect(Collectors.toList()) );

        List<Integer> list = IntStream.range(0, 1000).mapToObj((i) -> Integer.valueOf(i)).collect(Collectors.toList());

        list.forEach( (i)->spList.add( i ) );

        assertEquals(  arList.size(), spList.size() );

        assertEquals(  arList.get(100), arList.get(100) );

        arList.addAll( IntStream.range( 0, 300 ).mapToObj( (i)->Integer.valueOf(i) ).collect(Collectors.toList()) );
        spList.addAll( IntStream.range( 0, 150 ).mapToObj( (i)->Integer.valueOf(i) ).collect(Collectors.toList()) );
        spList.addAll( IntStream.range( 150, 300 ).mapToObj( (i)->Integer.valueOf(i) ).collect(Collectors.toList()) );

        assertEquals( arList.size(), spList.size() );

        assertEquals( arList.get(1010), arList.get(1010) );

        arList.set( 1010, -1 );
        spList.set( 1010, -1 );

        assertEquals( arList.size(), spList.size() );

        assertEquals( arList.get(1010), arList.get(1010) );

        spList.set( 1000, -2 );
        arList.set( 1000, -2 );
        assertEquals( arList.size(), spList.size() );

        spList.add( 2, -3 );
        arList.add( 2, -3 );
        assertEquals( arList.size(), spList.size() );

//        spList.remove(1010);
//        spList.set( 11000, -2 );

        int spCount = 0, arCount = 0;

        Iterator<Integer> spIter = spList.iterator();

        while( spIter.hasNext() ) {
            //System.out.println( iter.next() );
            spIter.next();
            spList.get( (int)( Math.random()*1000) );
            spCount++;
        }

        Iterator<Integer> arIter = arList.iterator();

        while( arIter.hasNext() ) {
            //System.out.println( iter.next() );
            arIter.next();
            arCount ++;
        }

        assertEquals( arList.size(), spList.size() );

    }
}