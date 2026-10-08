package ru.inversion.datacall;


import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.inversion.datacall.impl.SQLExprXMLLoader;
import ru.inversion.dataset.IParameters;
import ru.inversion.db.dialect.SqlDialectFactory;
import ru.inversion.login.Logon;
import ru.inversion.parser.statement.PrepareModeEnum;
import ru.inversion.tc.TaskContext;

import java.net.URL;
import java.util.ArrayList;
import java.util.Map;

public class TestXMLParsing {

    final URL defXml = TestXMLParsing.class.getResource("plsql/def.xml");
    // вызывается один раз на класс-тест перед запуском теста
    @BeforeAll
    static void init() {
    }

    // вызывается один раз на класс-тест по завершению тестирования
    @AfterAll
    static void end() {
    }

    @Test
    void testParse() throws Exception {

        Logon.login(new String[]{"--ru.inversion.userid=Fc3VsaW1vZmYvcXFxQGpkYmM6cG9zdGdyZXNxbDovL3BncHJvL3BnZGV2"});

        IDataCall callSelecting = SQLCallBuilder.NEW(new TaskContext()).url(defXml).name("Get_Block_Id").build();
        callSelecting.execute();
        /*
        try( SQLExprXMLLoader xmlLoader = new SQLExprXMLLoader(defXml) ) {

            Map<String, CallStatement> stmnts = xmlLoader.parse(SqlDialectFactory.POSTGRES);
            System.out.println("size: " + stmnts.size() );

            ArrayList<Object> values = new ArrayList<>();
            CallStatement callSelecting = stmnts.get("Selecting");
            String sql = callSelecting.prepareSql(PrepareModeEnum.QUERY, values);

            System.out.println("size: " + values.size() );

            CallWork callWork = callSelecting.createCallWork(new TaskContext(), IParameters.EMPTY);
            callWork.execute();
            System.out.println( callWork.getClass() );
        }
        */
    }
}
