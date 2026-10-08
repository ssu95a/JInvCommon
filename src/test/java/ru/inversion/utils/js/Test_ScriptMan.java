package ru.inversion.utils.js;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.inversion.dataset.IParameters;
import ru.inversion.dataset.ParametersByName;
import ru.inversion.utils.Pair;
import ru.inversion.utils.ParametersValues;
import ru.inversion.utils.dco.Dco;

import java.io.Reader;
import java.io.StringReader;
import java.time.LocalDateTime;
import java.util.function.Consumer;
import java.util.function.Function;

/** */
public class Test_ScriptMan {

    // вызывается один раз на класс-тест перед запуском теста
   // @BeforeAll
    static void init() {
    }

    private static String scriptJs = "function createXml(xml, pojo) {\n" +
            "    var requestId = Packages.java.util.UUID.randomUUID().toString();\n" +
            "xml.e(\"xxx\").value(1);\n" +
            "    var msgType = pojo.getIREQ_TYPE().intValue();\n" +
            "\n" +
            "    switch( msgType ) {\n" +
            "        case 1:\n" +
            "            xml.e(\"FNSPriostanSchRequest\").e(\"ИННФЛ\").value(pojo.getCREQ_DATA());\n" +
            "            break;\n" +
            "        case 2:\n" +
            "            xml.e(\"FNSPriostanSchRequest\").e(\"ИННЮЛ\").value(pojo.getCREQ_DATA());\n" +
            "            break;\n" +
            "        case 3:\n" +
            "            xml.e(\"FNSPriostanSchRequest\").e(\"КИО\").value(pojo.getCREQ_DATA());\n" +
            "            break;\n" +
            "    }\n" +
            "\n" +
            "    if( dco.e(\"test\").getVal() != null) {\n" +
            "        requestId = Packages.ru.inversion.utils.U.decode (\n" +
            "                        msgType,\n" +
            "                        1, \"000000000000000000000000000000000003\",\n" +
            "                        2, getHardcodedTestId(pojo),\n" +
            "                        3, \"000000000000000000000000000000000005\"\n" +
            "        );\n" +
            "    }\n" +
            "\n" +
            "    xml.e(\"FNSPriostanSchRequest\").a(\"ИдЗапроса\").value( requestId );\n" +
            "}\n" +
            "\n" +
            "function getHardcodedTestId(data) {\n" +
            "    var inn = data.getCREQ_DATA();\n" +
            "    if ( inn.equals( \"7104032433\" ) ){\n" +
            "        return \"000000000000000000000000000000000002\";\n" +
            "    }\n" +
            "\n" +
            "    if ( inn.equals( \"6321137567\" ) ){\n" +
            "        return \"000000000000000000000000000000000004\";\n" +
            "    }\n" +
            "    \n" +
            "    return \"000000000000000000000000000000000001\";\n" +
            "}\n" +
            "\n" +
            "createXml(dco, pojo);";

    // вызывается один раз на класс-тест по завершению тестирования
    @AfterAll
    static void end() {
    }
    @Test
    @DisplayName( value = "test create by root name" )
    void test_execute( )
    {
//        ScriptMan sm = ScriptMan.create( new Function< String, Reader >() {
//            @Override
//            public Reader apply( String s ) {
//                if( "1".equals(s) )
//                    return new StringReader( "var greeting='hello world';print(greeting);greeting");
//                if( "2".equals(s) )
//                    return new StringReader("var ret; var ret1; var func1 = function(){   \n" +
//                            "    print(\"Simple JavaScript function!!!\");   \n" +
//                            "}   \n" +
//                            "  \n" +
//                            "var func2 = function(reader) {   \n" +
//                            "    print(\"Hello \" + reader );   \n" +
//                            "}   \n" +
//                            "\n" +
//                            "func1();\n" +
//                            "func2(p1);" +
//                            "ret = 'xxx';" +
//                            "ret1 = 'return';");
//                return null;
//            }
//        });

        ScriptMan sm = ScriptMan.create(new IScriptSupplier() {
            @Override
            public String makeGlobalId( Object id ) {
                return "test" + id;
            }

            @Override
            public boolean isOutdated( Object id, LocalDateTime time ) {
                return true;
            }

            @Override
            public Pair<Reader,LocalDateTime> apply( Object s ) {
                if( "2".equals(s) )
                    return Pair.makePair( new StringReader( scriptJs ), LocalDateTime.now() );
                if( "1".equals(s) )
                    return Pair.makePair(new StringReader("var ret; var ret1; var func1 = function(){   \n" +
                            "    print(\"Simple JavaScript function!!!\");   \n" +
                            "}   \n" +
                            "  \n" +
                            "var func2 = function(reader) {   \n" +
                            "    print(\"Hello \" + reader );   \n" +
                            "}   \n" +
                            "\n" +
                            "func1();\n" +
                            "//func2(p1);" +
                            "ret = 'xxx';" +
                            "ret1 = 'return';"),LocalDateTime.now() ) ;
                return null;
            }
        });
        sm.addListener(new Consumer< ScriptManEvent >() {
            @Override
            public void accept( ScriptManEvent e ) {
                System.out.println( "sc: " + e.getType() + ", script " + e.getScriptId() + ", before " + e.isBefore() );
            }
        });


        IParameters parameters = new ParametersValues();
        parameters.set("dco",  new Dco("root"));
        parameters.set("pojo", null );

        //sm.execute("1", null );
        System.out.println ( "ret: " +
            sm.execute("2", parameters ).toString()
        );

    }
}

