package ru.inversion.dataset;

import org.junit.jupiter.api.*;

/** */
public class Test_SQLDataSet_Predicates {

    // вызывается один раз на класс-тест перед запуском теста
    @BeforeAll
    static void init(){ }

    // вызывается один раз на класс-тест по завершению тестирования
    @AfterAll
    static void end() { }

    /** */
    @BeforeEach
    void beforeEach() {
    }

    @Disabled
    @Test
    @DisplayName( value = "Метод getWherePredicate - получение полного предиката заданного через set WHERE методы" )
    void test_getWherePredicate()
    {
        final String w[] = {
            "ICUSNUM<>4",
            "ICUSNUM is not null",
            "ICUSNUM > :ICUSNUM"
        };

        SQLDataSet<PCusTest> dsAddr = new SQLDataSet<>( null, PCusTest.class );
        Assertions.assertNull( dsAddr.getWherePredicat() );

        dsAddr.setFilter("icsunum > 0", true, true );
        Assertions.assertNull( dsAddr.getWherePredicat() );


        for( String s : w )
        {
            dsAddr.setWherePredicate( s, true );
        }

        Assertions.assertEquals (
            dsAddr.getWherePredicate().replaceAll("\\s", ""),
            "("+String.join(")and(",w).replaceAll("\\s", "")+")"
        );
    }

    @Disabled
    @Test
    @DisplayName( value = "Метод getCompletedWherePredicate - получение полного предиката заданного в dataSet" )
    void test_getCompletedWherePredicate() throws DataSetException
    {
        final String w[] = {
                "ICUSNUM<>4",
                "ICUSNUM is not null",
                "ICUSNUM > :ICUSNUM"
        };

        SQLDataSet<PCusTest> dsAddr = new SQLDataSet<>( null, PCusTest.class );
        dsAddr.setCallbackParameters(new ParametersByName() {
            @Override
            public Object getParameter( String parameterName ) {
                return 2000;
            }
        });


        for( String s : w )
        {
            dsAddr.setWherePredicate( s, true );
        }

        //dsAddr.setFilter("CL_NAME<>'Иванов'",true,true);
        //System.out.println( dsAddr.getWherePredicate() );

        Assertions.assertEquals (
                dsAddr.getCompletedWherePredicate().replaceAll("\\s", ""),
                "("+String.join(")and(",w).replaceAll("\\s", "")+")"
        );
    }


    @Test
    void test_Opl_SQL()
    {
        final String query =
                "select to_char(sp.dpcertend, 'DD.MM.YYYY') || ' ( ' || sp.CPCRTSHORTNAME || ' / ' || sp.CPCRTSERIALNUM || ' )'   CERT_INFO, sp.ipcrtid PCERT_ID\n" +
                        "from sg_opr so, sg_pcrt sp\n" +
                        "where so.copruser = upper(user)\n" +
                        "    and so.ioprid =  sp.mpcrtoperator\n" +
                        "    and sp.dpcertend > clock_timestamp() and sp.cpcrtsignstatus = 'VALID'\n" +
                        "    and sp.mpcrtcrsysid in (\n" +
                        "      select cneglbp_crsys_id\n" +
                        "      from eds_v_glbp )\n" +
                        "order by sp.dpcertend";

        SQLDataSet<Object[]> dsQuery = new SQLDataSet<>( null, Object[].class );
        dsQuery.setSQL(query);

        System.out.println( "SQL: \n"  + dsQuery.getCompletedSQL() );


    }

}
