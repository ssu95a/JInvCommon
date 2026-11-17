package ru.inversion.datacall;

import ru.inversion.dataset.IParameters;
import ru.inversion.meta.impl.work.IDBWork;
import ru.inversion.tc.TaskContext;
import ru.inversion.utils.Tags;
import ru.inversion.utils.converter.TypeConverter;

import java.sql.Types;
import java.util.*;

import static javax.persistence.ParameterMode.IN;
import static javax.persistence.ParameterMode.OUT;

/**
 * Выполнение скрипта на сервере через пакет dbms_sql
 * <p>
 * alpha ver 0.1
 */
public class DbmsSqlWork implements IDBWork<Void,IDBWork>, AutoCloseable {

    /** Значения параметров из вне */
    private final IParameters callBackParameters;

    /** */
    private final CallWork storedProcWork;

    /** Подготовленный PL/SQL блок */
    final private String plSql;

    /** */
    final List<String> vars = new ArrayList<>();

    /** Исходные имена в том же порядке, что и vars. */
    private final List<String> parameterNames = new ArrayList<>();

    /** Результат последнего вызова серверной функции. */
    private Integer resultCode;
    private String resultInfo;

    /**
    Выполнение SQL выражения через пакет DBMS_SQL.
    FUNCTION execute_Dbms_Sql_Work (
            p_plSql      IN   VARCHAR2,
            p_vars       IN   SYS.ODCIVARCHAR2LIST,
            p_vals       IN   SYS.ODCIVARCHAR2LIST,
            p_typs       IN   SYS.ODCIVARCHAR2LIST,
            p_outs       OUT  SYS.ODCIVARCHAR2LIST,
            p_resultInfo OUT  VARCHAR2
    )
    RETURN INTEGER: 0 — успех, -1 — p_vals короче p_vars;
    остальные ошибки сервер пробрасывает исключением.
     * */
    static private CallWork initStoredProc( TaskContext tc ) throws CallException {
        return CallBuilder.NEW()
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
    }

    /** */
    DbmsSqlWork( TaskContext tc, CallStatement callStatement, IParameters callBackParameters ) throws CallException {
        this(callStatement, callBackParameters, initStoredProc(tc));
    }

    /** Подмена только внутреннего вызова для теста контракта без Oracle. */
    DbmsSqlWork(CallStatement callStatement, IParameters callBackParameters, CallWork storedProcWork) {
        this.callBackParameters = callBackParameters;
        this.storedProcWork = Objects.requireNonNull(storedProcWork, "storedProcWork");

        // Идентичность имен совпадает с CallStatement и ParametersValues.
        final Map<String,Integer> positions = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        this.plSql = callStatement.prepareSql(
                (t, c) -> {
                        switch(t.getType()) {
                            case NAMED_PARAMETER: {
                                final String sourceName = t.getValue().toString();
                                Integer index = positions.get(sourceName);
                                if( index == null ) {
                                    index = vars.size() + 1;
                                    positions.put(sourceName, index);
                                    vars.add("b" + index);
                                    parameterNames.add(sourceName);
                                }
                                final String newParamName = vars.get(index - 1);

                                c.append(" :").append(newParamName).sp();

                                if( c.isCommentParameters() )
                                    c.append("/* ").append(newParamName.substring(1)).append(", :").append(t.getValue()).append(" */ ");
                            }
                            break;
                            default:
                                c.append(t);
                        }
                },
                (type, id, value) -> { }
        );

    }

    /** Читает значения непосредственно перед очередным запуском. */
    List<String> inputValues() {
        final List<String> vals = new ArrayList<>(vars.size());
        for( String parameterName : parameterNames ) {
            if( callBackParameters != null && callBackParameters.hasParameter(parameterName) ) {
                final Object value = callBackParameters.getParameter(parameterName);
                vals.add(value == null ? null : TypeConverter.convert(value, String.class));
            }
            else
                vals.add(null);
        }
        return vals;
    }

    /** Проверяет весь ответ до первой обратной записи. */
    void applyOutputs(List<String> outs) {
        if( outs == null || outs.size() != vars.size() )
            throw new IllegalStateException(
                    Tags.PRODUCT_LABEL + "DBMS_SQL returned "
                            + (outs == null ? 0 : outs.size())
                            + " values for " + vars.size() + " variables"
            );

        if( callBackParameters != null )
            for( int i = 0; i < parameterNames.size(); i++ )
                callBackParameters.setParameter(parameterNames.get(i), outs.get(i));
    }

    /** Выполнение запроса */
    public DbmsSqlWork execute( Void unused ) throws CallException {
        Throwable failure = null;

        try {
            resultCode = null;
            resultInfo = null;
            final List<String> vals = inputValues();

            storedProcWork.set( "plSql", plSql )
                          .set( "vars",  vars  )
                          .set( "vals",  vals  )
                          .set( "typs", Collections.emptyList() );

            storedProcWork.execute();

            resultCode = storedProcWork.get("ret");
            resultInfo = storedProcWork.get("rsltInf");
            if( !Integer.valueOf(0).equals(resultCode) )
                throw new CallException(
                        Tags.PRODUCT_LABEL + "DBMS_SQL launcher returned "
                                + resultCode
                                + (resultInfo == null ? "" : ": " + resultInfo)
                );
            applyOutputs(storedProcWork.get("outs"));
        }
        catch( CallException ex ) {
            failure = ex;
            throw ex;
        }
        catch( RuntimeException ex ) {
            failure = new CallException(Tags.PRODUCT_LABEL + "Error on execute work", ex);
            throw (CallException)failure;
        }
        catch( Error error ) {
            failure = error;
            throw error;
        }
        finally {
            try {
                close();
            }
            catch( RuntimeException | Error closeFailure ) {
                if( failure == null )
                    throw closeFailure;
                failure.addSuppressed(closeFailure);
            }
        }
        return this;
    }

    String preparedSql() { return plSql; }
    Integer resultCode() { return resultCode; }
    String resultInfo() { return resultInfo; }

    /** */
    @Override
    public void close()  {
        if( storedProcWork != null )
            storedProcWork.close();
    }
}
