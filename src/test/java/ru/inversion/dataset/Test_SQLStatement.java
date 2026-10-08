package ru.inversion.dataset;

import org.junit.jupiter.api.*;
import ru.inversion.tc.TaskContext;

import javax.persistence.*;

import java.sql.ResultSet;
import java.sql.SQLException;

import static ru.inversion.login.Logon.login;

/** */
public class Test_SQLStatement {

    @Entity
    @Table(name = "cus_addr")
    @NamedNativeQueries(
        value = {
            @NamedNativeQuery(name = "addr",query = "select * from cus_addr where icusnum<17"),
            @NamedNativeQuery(name = "addr.pg",query = "select * from cus_addr where icusnum=17")
        }
    )
    public static class PCusAddr {

        private Long ICUSNUM;

        private String CODE;

        private Integer ver;

        @Id
        @Column(name = "ICUSNUM")
        public Long getICUSNUM() {
            return ICUSNUM;
        }
        public void setICUSNUM( Long ICUSNUM ) {
            this.ICUSNUM = ICUSNUM;
        }

        @Column(name = "CODE")
        public String getCODE() {
            return CODE;
        }
        public void setCODE( String CODE ) {
            this.CODE = CODE;
        }

        @Transient
        public Integer getVer() {
            return ver;
        }
        public void setVer( Integer ver ) {
            this.ver = ver;
        }
    }


    // вызывается один раз на класс-тест перед запуском теста
    @BeforeAll
    static void init() {
        login( new String[] {"--ru.inversion.userid=FWFhJL0NBU1BFUkBkZXY4aQ=="} );
    }

    // вызывается один раз на класс-тест по завершению тестирования
    @AfterAll
    static void end() {
    }

    /** */
    @BeforeEach
    void beforeEach() {
    }



    @Disabled
    @Test
    @DisplayName( value = "testNamedNativeQuery" )
    void testNamedNativeQuery() throws DataSetException{
        SQLDataSet<PCusAddr> dsAddr = new SQLDataSet<>(new TaskContext(), PCusAddr.class );
        dsAddr.execute();
        Assertions.assertEquals( dsAddr.getLoadedRowCount(), 4 );
    }

    @Disabled
    @Test
    @DisplayName( value = "testGetCompleteWherePredicate" )
    void testGetCompleteWherePredicate() throws DataSetException{
        SQLDataSet<PCusAddr> dsAddr = new SQLDataSet<>(new TaskContext(), PCusAddr.class );
        dsAddr.setWherePredicate("ICUSNUM<>4");
        System.out.println( dsAddr.getCompletedWherePredicate() );
        dsAddr.execute();
        dsAddr.setWherePredicate("ICUSNUM is not null",true);

        System.out.println( dsAddr.getCompletedWherePredicate() );
        //Assertions.assertEquals( dsAddr.getLoadedRowCount(), 4 );
    }

    @Disabled
    @Test
    @DisplayName( value = "test_getWherePredicate" )
    void test_getWherePredicate(){
        SQLDataSet<PCusAddr> dsAddr = new SQLDataSet<>( null, PCusAddr.class );
        dsAddr.setWherePredicate( "ICUSNUM<>4");
        dsAddr.setWherePredicate( "ICUSNUM is not null", true );

        System.out.println( dsAddr.getCompletedSQL() );


        //Assertions.assertEquals( dsAddr.getLoadedRowCount(), 4 );
    }

    @Disabled
    @Test
    @DisplayName( value = "test_DBUnique" )
    void test_DBUnique(){
    }

    @Test
    @DisplayName( value = "test_CharSymb" )
    void test_CharSymb() throws DataSetException {
        SQLDataSet<Integer> dsChar = new SQLDataSet<>( new TaskContext(), Integer.class );
        dsChar.setRowMapper(new IRowMapper< Integer >() {
            @Override
            public Integer mapRow( ResultSet rs, int rowNum ) throws SQLException {
                return rs.getInt(1);
            }
        });
        dsChar.setSQL (
            "select COUNT(1) n from cdop where CCDOPTYPE = 9\n" +
            "UNION ALL\n" +
            "select COUNT(1) from cdop where CCDOPTYPE = '9 '\n" +
            "UNION ALL\n" +
            "select COUNT(1) from cdop where CCDOPTYPE = '9'\n" +
            "UNION ALL\n" +
            "select COUNT(1) from cdop where CCDOPTYPE = ~'9'~" +
            "UNION ALL\n" +
            "select COUNT(1) from cdop where CCDOPTYPE = CAST ('9' AS ~CHAR(2) )~"
        );
        dsChar.queryAllRows().execute();
        dsChar.getRows().forEach( System.out::println );
        //System.out.println(dsChar.getLoadedRowCount()  );

    }

    @Test
    @DisplayName( value = "test_61Rec" )
    void test_61Rec() throws DataSetException {
        SQLDataSet<Object[]> dsChar = new SQLDataSet<>( new TaskContext(), Object[].class );
        dsChar.setSQL (
                "SELECT IPLFSFLD,CPLFSREFER,CPLFSNAME,IPLFSTYPE FROM PL_FILE_STR\n" +
                        "WHERE null is null\n" +
                        " and (IPLFID = 452)"
        );
        dsChar.execute();
        //dsChar.getRows().forEach( System.out::println );
        System.out.println(dsChar.getLoadedRowCount()  );

    }

}
