package ru.inversion.datacall;

import ru.inversion.dataset.IParameters;
import ru.inversion.meta.impl.work.IDBWork;
import ru.inversion.parser.token.Token;
import ru.inversion.tc.TaskContext;
import ru.inversion.utils.Tags;
import ru.inversion.utils.converter.TypeConverter;

import java.sql.Types;
import java.util.*;
import java.util.function.BiConsumer;

import static javax.persistence.ParameterMode.IN;
import static javax.persistence.ParameterMode.OUT;

/**
 * Выполнение скрипта на сервере через пакет dbms_sql
 * <p>
 * alpha ver 0.1
 */
public class DbmsSqlWork implements IDBWork<Void,IDBWork>, AutoCloseable {

    /** Метаданные */
    //final private CallStatement callStatement;

    /** БД */
    final private TaskContext tc;

    /** Значения параметров из вне */
    private IParameters callBackParameters;

    /** */
    private CallWork storedProcWork;

    /** Подстановка имен параметров */
    final private Map<String,String> mapParamSubst = new HashMap<>();

    /** */
    final List<String> vars = new ArrayList<>();

    /**
    Выполнение SQL выражения через пакет DBSMS_SQL
    FUNCTION execute_Dbms_Sql_Work (
            p_plSql      IN   VARCHAR2,
            p_vars       IN   T_TAB_VARCHAR2_2000,
            p_vals       IN   T_TAB_VARCHAR2_2000,
            p_typs       IN   T_TAB_VARCHAR2_2000,
            p_outs       OUT  T_TAB_VARCHAR2_2000,
            p_resultInfo OUT  VARCHAR2
    )
    RETURN INTEGER
     * */
    static private CallWork initStoredProc( TaskContext tc ) throws CallException {

        CallWork dbms_sql_work = (CallWork)tc.getProperty("dbms_sql_work");

        if( dbms_sql_work != null )
            return dbms_sql_work;

        dbms_sql_work =

        CallBuilder.NEW()
           .sql("begin :ret := JF_PKG_UTIL.execute_Dbms_Sql_Work( :plSql, :vars, :vals, :typs, :outs, :rsltInf); end;")
           .putParameter( new ParameterMetadataBuilder().mode(OUT).name("ret").sqlType(Types.INTEGER).javaType(Integer.class).isReturnValue(true).build() )
           .putParameter( new ParameterMetadataBuilder().mode(IN ).name("plSql").build() )
           .putParameter( new ParameterMetadataBuilder().mode(IN ).name("vars").sqlType(Types.ARRAY).elementType("SYS.ODCIVARCHAR2LIST").build() )
           .putParameter( new ParameterMetadataBuilder().mode(IN ).name("vals").sqlType(Types.ARRAY).elementType("SYS.ODCIVARCHAR2LIST").build() )
           .putParameter( new ParameterMetadataBuilder().mode(IN ).name("typs").sqlType(Types.ARRAY).elementType("SYS.ODCIVARCHAR2LIST").build() )
           .putParameter( new ParameterMetadataBuilder().mode(OUT).name("outs").sqlType(Types.ARRAY).elementType("SYS.ODCIVARCHAR2LIST").build() )
           .putParameter( new ParameterMetadataBuilder().mode(OUT).name("rsltInf").sqlType(Types.VARCHAR).javaType(String.class).build() )
        .build()
           .createCallWork( tc, null );

        tc.setProperty("dbms_sql_work", dbms_sql_work );

        return dbms_sql_work;
    }

    /** */
    DbmsSqlWork( TaskContext tc, CallStatement callStatement, IParameters callBackParameters ) throws CallException {
        this.tc                 = tc;
        this.callBackParameters = callBackParameters;

        // подготовка выражения
        final List<String> vals = new ArrayList<>();

        final String plSql =
        callStatement.prepareSql(
                new BiConsumer< Token, CallPrepareContext >() {
                    int index = 0;
                    @Override
                    public void accept( Token t, CallPrepareContext c ) {
                        switch(t.getType()) {
                            case NAMED_PARAMETER: {
                                String oldParamName = t.getValue().toString();
                                String newParamName = mapParamSubst.get(t.getValue().toString());

                                if( newParamName == null )
                                {
                                    index++;
                                    newParamName = "b" + index;
                                    mapParamSubst.put( oldParamName, newParamName );
                                    mapParamSubst.put( ":" + newParamName, oldParamName );

                                    vars.add( newParamName );

                                    if( callBackParameters != null && callBackParameters.hasParameter( oldParamName ) ) {
                                        Object v = callBackParameters.getParameter( oldParamName );
                                        vals.add( v == null ? null : TypeConverter.convert( v, String.class) );
                                    }
                                    else
                                        vals.add( null );
                                }

                                c.append(" :").append(newParamName).sp();

                                if( c.isCommentParameters() )
                                    c.append("/* ").append( newParamName.substring(1) ).append(", :").append(t.getValue()).append(" */ ");
                            }
                            break;
                            default:
                                c.append(t);
                        }

                    }
                },
                ( type, id, value ) -> {

                }
        );

        this.storedProcWork = initStoredProc(tc);

        storedProcWork.set( "plSql", plSql )
                      .set( "vars",  vars  )
                      .set( "vals",  vals  )
                      .set( "typs", Collections.emptyList() );
    }

    /** Выполнение запроса */
    public DbmsSqlWork execute( Void unused ) throws CallException {

        try {

            storedProcWork.execute();

            List<String> outs = storedProcWork.get("outs");

            int i = 0;

            if( callBackParameters != null )
            {
                for( String v : vars )
                {
                    String p = this.mapParamSubst.get( ":" + v );
                    callBackParameters.setParameter( p, outs.get(i) );
                    i++;
                }
            }
        }
        catch( Throwable th ) {
            throw new CallException( Tags.PRODUCT_LABEL + "Error on execute work", th );
        }
        finally {
            close( );
        }
        return this;
    }

    /** */
    @Override
    public void close()  {
    }
}
