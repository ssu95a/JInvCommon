package ru.inversion.datacall;

import ru.inversion.dataset.IParameters;
import ru.inversion.meta.impl.work.IDBWork;
import ru.inversion.parser.token.Token;
import ru.inversion.tc.TaskContext;
import ru.inversion.utils.Checks;
import ru.inversion.utils.ParametersValues;
import ru.inversion.utils.Tags;
import ru.inversion.utils.converter.TypeConverter;

import java.sql.Types;
import java.util.*;

import static javax.persistence.ParameterMode.IN;
import static javax.persistence.ParameterMode.OUT;

/**
 * Выполнение скрипта на сервере через пакет dbms_sql
 * <p>
 * ver 1.1
 */
public class DbmsSqlWork implements IDBWork<Void,IDBWork>, AutoCloseable {

    /** Значения параметров из вне */
    private final IParameters callBackParameters;

    private final CallStatement callStatement;

    /** */
    private final CallWork storedProcWork;

    /** Подготовленный PL/SQL блок */
    final private String plSql;

    /** */
    final List<String> vars = new ArrayList<>();

    /** Исходные имена и индексы в том же порядке, что и vars. */
    private final List<Comparable<?>> parameterIds = new ArrayList<>();

    /** Результат последнего вызова серверной функции. */
    private Integer resultCode;
    private String resultInfo;

    /**
    Выполнение SQL выражения через пакет DBMS_SQL.
    <p>
    <pre>
    FUNCTION execute_Dbms_Sql_Work (
            p_plSql      IN   VARCHAR2,
            p_vars       IN   SYS.ODCIVARCHAR2LIST,
            p_vals       IN   SYS.ODCIVARCHAR2LIST,
            p_typs       IN   SYS.ODCIVARCHAR2LIST,
            p_outs       OUT  SYS.ODCIVARCHAR2LIST,
            p_resultInfo OUT  VARCHAR2
    )
    RETURN INTEGER: 0 — успех, -1 — ошибка;
    </pre>
    При ошибке в {@code p_resultInfo} ее описание
    <p>
    Остальные ошибки {@code execute_Dbms_Sql_Work} пробрасывает исключением.
    */
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
    DbmsSqlWork(CallStatement callStatement, IParameters callBackParameters, CallWork storedProcWork)
    {
        this.callStatement      = callStatement;
        this.callBackParameters = callBackParameters;
        this.storedProcWork     = Checks.Require.object( storedProcWork, "storedProcWork" );

        this.plSql = callStatement.prepareSql (

                new AbstractCallMakerBase() {

                    private final Map<Comparable<?>, String> bindNames = new TreeMap<>(ParametersValues.ParamIdComparator);

                    @Override
                    public void accept( Token token, CallPrepareContext context )
                    {
                        final Comparable<?> id;

                        switch( token.getType() ) {
                            case NAMED_PARAMETER:
                                id = token.getValue().toString();
                                break;
                            case INDEXED_PARAMETER:
                                id = nIndex++;
                                break;
                            default:
                                context.append(token);
                                return;
                        }

                        String bindName = bindNames.get(id);

                        if( bindName == null )
                        {
                            bindName = "b" + ( vars.size() + 1);

                            bindNames.put(id, bindName);
                            vars.add(bindName);

                            parameterIds.add(id);
                        }

                        context.append(" :").append(bindName).sp();

                        if( context.isCommentParameters() )
                            context.append("/* ").append(bindName.substring(1)).append(", :").append(token.getValue()).append(" */ ");
                    }
                },
                (type, id, value) -> { }
        );

    }

    /** Готовит значения непосредственно перед очередным запуском. */
    List<String> inputValues( )
    {
        final List<String> vals = new ArrayList<>( vars.size() );

        for( Comparable<?> id : parameterIds )
        {
            Object value = null;

            if( callBackParameters != null )
            {
                if( id instanceof Integer )
                {
                    int index = (Integer)id;
                    if( callBackParameters.hasParameter(index) )
                        value = callBackParameters.getParameter(index);
                }
                else
                {
                    String name = id.toString();
                    if( callBackParameters.hasParameter(name) )
                        value = callBackParameters.getParameter(name);
                }
            }

            vals.add( value == null ? null : TypeConverter.convert(value, String.class) );
        }

        return vals;
    }

    /** Проверяет и преобразует весь ответ до первой обратной записи. */
    void applyOutputs(List<String> outs) {

        if( outs == null || outs.size() != vars.size() )
            throw new IllegalStateException( Tags.PRODUCT_LABEL + "DBMS_SQL returned " + (outs == null ? 0 : outs.size()) + " values for " + vars.size() + " variables" );

        if( callBackParameters == null )
            return;

        Map<Comparable<?>, Object> converted = new LinkedHashMap<>();

        for( int i = 0; i < parameterIds.size(); i++ )
        {
            final Comparable<?> id = parameterIds.get(i);
            final ParameterMetadata<?> parameter = callStatement.getParameter(id);
            // Для параметров, найденных только в тексте блока, сохраняем строковую обратную запись.
            final boolean declared = parameter != null && (!(parameter instanceof RawParameterMetadata) || ((RawParameterMetadata)parameter).isDeclared());
            if( declared && parameter.getMode() == IN )
                continue;

            final Class<?> javaType = declared && parameter.isOut() ? parameter.getParameterType() : null;
            Object value = outs.get(i);
            if( javaType != null ) {
                try {
                    value = TypeConverter.convert(value, javaType);
                }
                catch( RuntimeException ex ) {
                    throw new CallException(Tags.PRODUCT_LABEL + "Error converting DBMS_SQL output for parameter '"
                            + id + "' to " + javaType.getTypeName(), ex);
                }
            }
            converted.put(id, value);
        }

        converted.forEach((id, value) -> {
            if( id instanceof Integer )
                callBackParameters.setParameter((Integer)id, value);
            else
                callBackParameters.setParameter((String)id, value);
        });
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
                throw new CallException( Tags.PRODUCT_LABEL + "DBMS_SQL launcher returned " + resultCode + (resultInfo == null ? "" : ": " + resultInfo)                );

            applyOutputs(storedProcWork.get("outs"));

        }
        catch( CallException | Error e) {
            failure = e;
            throw e;
        }
        catch( RuntimeException ex ) {
            failure = new CallException(Tags.PRODUCT_LABEL + "Error on execute work", ex);
            throw (CallException)failure;
        }
        finally
        {
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
    String resultInfo()  { return resultInfo; }

    /** */
    @Override
    public void close()  {
        if( storedProcWork != null )
            storedProcWork.close();
    }
}
