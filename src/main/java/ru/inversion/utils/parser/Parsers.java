package ru.inversion.utils.parser;


import ru.inversion.utils.U;

import java.io.Reader;
import java.io.StringReader;

/** */
public class Parsers {

    /** */
    public static Iterable<Token<?>> parseSimple(Reader r )
    {
        return U.iterable( new Parser( Tokenizer.parse(r).iterator() ) );
    }

    /** */
    public static Iterable<Token<?>> parsePLxxSql(Reader r )
    {
        return U.iterable( new PLxxSqlParser( parseSimple(r).iterator() ) );
    }

    /** */
    public static Iterable<Token<?>> parseSql(Reader r )
    {
        return U.iterable( new SqlParser( parseSimple(r).iterator() ) );
    }


    final private static String testHandlerString2 = "DECLARE\n" +
            "    cCurCtx  VARCHAR2 (2000);            \n" +
            "    tabRole    T_TAB_VARCHAR2_2000 := :ROLE_LIST;\n" +
            "    tabRoleOk  T_TAB_VARCHAR2_2000 := T_TAB_VARCHAR2_2000 ();\n" +
            "    tabAction    SYS.ODCINumberList := :ACTION_LIST;       \n" +
            "    --tabActionOk  SYS.ODCINumberList := SYS.ODCINumberList ();\n" +
            "    ival number := 0.01 - ( -000.1 );\n" +
            "    ival2 number := -000.3;\n" +
            "BEGIN       \n" +
            "    FOR i IN 1..tabRole.Count () LOOP\n" +
            "        IF UTIL.In_Role (tabRole (i)) THEN\n" +
            "           tabRoleOk.Extend ();\n" +
            "           tabRoleOk (tabRoleOk.Last ()) := tabRole (i);  \n" +
            "        END IF;        \n" +
            "    END LOOP;                \n" +
            "\n" +
            "/*FOR i IN 1..tabAction.Count () LOOP\n" +
            "   IF ODB_ACCESS_1.Is_Action (tabAction (i)) THEN\n" +
            "      tabActionOk.Extend (); \n" +
            "      tabActionOk (tabActionOk.Last ()) := tabAction (i); \n" +
            "   END IF;\n" +
            "END LOOP; */               \n" +
            "    select /*+index */ 1 into tmp from dual order by 1;\n" +
            "    :ROLE_LIST_OK := tabRoleOk;       \n" +
            "    :ACTION_LIST_OK := tabActionOk;        \n" +
            "                        \n" +
            "    :DTRAN_WITH_TIME   := PREF.Get_PrefUpper('OPERLIST.OPTIONS.DTRAN_WITH_TIME', 'Y');\n" +
            "    :PART_CURRENT_ONLY := PREF.Get_PrefUpper('OPERLIST.PART_CURRENT_ONLY', 'Y');\n" +
            " \n" +
            "    IF :CONTEXT_PREF IS NOT NULL THEN\n" +
            "       cCurCtx := PREF.Get_Preference (:CONTEXT_PREF);\n" +
            "       IF cCurCtx IS NOT NULL THEN\n" +
            "          XXI_CONTEXT.Restore_Context (cCurCtx);\n" +
            "       END IF; \n" +
            "    ELSIF :ID_SMR IS NOT NULL THEN\n" +
            "       XXI_CONTEXT.New_IDSmr (:ID_SMR, :SAVE_CONTEXT);\n" +
            "    END IF; \n" +
            "    :CURRENT_CONTEXT := cCurCtx;\n" +
            "END;";

    public static void main( String[] args ) {
        try {

            // File f = new File("D:\\JavaProjects\\Core\\Common\\JInvCommon\\src\\test\\resources\\ru\\inversion\\datacall\\plsql\\CalcClientAttr.sql");
            parsePLxxSql(new StringReader(testHandlerString2)).forEach(System.out::println);


        }
        catch( Throwable th ) {
            th.printStackTrace();
        }
    }
}
