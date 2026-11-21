package ru.inversion.datacall;

import ru.inversion.dataset.IParameters;
import ru.inversion.dataset.parser.lexer.SqlScanner;
import ru.inversion.parser.statement.LangEnum;
import ru.inversion.parser.statement.PrepareModeEnum;
import ru.inversion.tc.TaskContext;
import ru.inversion.utils.ParametersValues;
import ru.inversion.utils.Tags;
import ru.inversion.utils.U;

import javax.persistence.ParameterMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.stream.StreamSupport;

/**
 *  Описание одного хранимого выражения с параметрами
 */
public class CallStatement {

    /** Дескриптор запроса */
    final private CallDescriptor descriptor;

    /** Метаданные параметров */
    final private Map<Comparable<?>, ParameterMetadata<?>> parametersMetaData;

    /** */
    //private final List<Comparable<?>> outParameterOrder;

    /** Флаг удалять ли добавочную информацию из текста
     *  - комментарии в коде
     *  - комментарии вокруг параметров
     */
    final private boolean removeAdditionalInfo;

    /** Заменять константы на параметры */
    final private boolean replaceConstants;

    /** */
    final private boolean autoCommit;

    /** */
    CallStatement(CallDescriptor descriptor,
                  Map<Comparable<?>, ParameterMetadata<?>> parametersMetaData, List<Comparable<?>> outParameterOrder, boolean removeAdditionalInfo, boolean replaceConstants, boolean autoCommit ) {
        this.descriptor           = descriptor;
        //this.outParameterOrder    = outParameterOrder;
        this.removeAdditionalInfo = removeAdditionalInfo;
        this.replaceConstants     = replaceConstants;
        if( parametersMetaData == null || parametersMetaData.isEmpty() )
            this.parametersMetaData = Collections.emptyMap();
        else {
            final Map<Comparable<?>,ParameterMetadata<?>> copy =
                    new TreeMap<>((java.util.Comparator)ParametersValues.ParamIdComparator);
            copy.putAll(parametersMetaData);
            this.parametersMetaData = Collections.unmodifiableMap(copy);
        }
        this.autoCommit           = autoCommit;

//        isPrepared = isAnonymousBlock() && getLang() == LangEnum.SQL;// && !hasOutParameters();
    }

    public boolean isPrepared() {
        return getLang() == LangEnum.SQL;
    }
    /** */
    public LangEnum getLang() {
        return descriptor.getLang( );
    }
    /** */
    public boolean isAnonymousBlock() {
        return descriptor.isAnonymousBlock();
    }

    String sourceSql() {
        return descriptor.sourceSql();
    }

    List<SqlScanner.Token> sqlTokens() {
        return descriptor.sqlTokens();
    }

    /** */
    public boolean isAutoCommit() {
        return autoCommit;
    }

    /** */
    public String prepareSql( PrepareModeEnum prepareMode, List<Object> parametersValue )
    {
        switch( prepareMode ) {
            case QUERY:
            case QUERY_EX:
                return prepareJdbcSql((position, id) -> parametersValue.add(null),
                                      (position, value) -> parametersValue.add(value));
            case QUERY_TEXT_ONLY:
                return prepareContext().prepareJdbc(replaceConstants, true, null, null);
            case QUERY_WITH_VALUES:
                return prepareContext().rewriteBinds(null);
            default:
                throw new IllegalArgumentException(Tags.PRODUCT_LABEL + "Error 'prepareMode' value: " + prepareMode);
        }
    }

    private CallPrepareContext prepareContext() {
        return new CallPrepareContext(sourceSql(), sqlTokens(), removeAdditionalInfo);
    }

    String rewriteBinds(BiConsumer<Comparable<?>, CallPrepareContext> rewrite) {
        return prepareContext().rewriteBinds(rewrite);
    }

    /** Передаёт bind-параметры и параметризованные литералы по их JDBC-позициям. */
    String prepareJdbcSql( BiConsumer<Integer, Comparable<?>> onBind, BiConsumer<Integer, Object> onLiteral ) {
        return prepareContext().prepareJdbc(replaceConstants, false, onBind, onLiteral);
    }

    /** */
    public Iterable<ParameterMetadata<?>> getParameterIter() {

        if( parametersMetaData == null || parametersMetaData.isEmpty() )
            return U.iterable( Collections.emptyIterator() );

        return U.iterable( parametersMetaData.values().iterator() );
    }

    /** */
    public Iterable<ParameterMetadata<?>> getParameterIter( boolean outOnly ) {

        if( parametersMetaData == null || parametersMetaData.isEmpty() )
            return U.iterable( Collections.emptyIterator() );

        return U.iterable( parametersMetaData.values().stream().filter(p->p.isOut() == outOnly).iterator() );
    }

    /** */
    public ParameterMetadata<?> getParameter( Comparable parameter ) {
        return parametersMetaData == null ? null : parametersMetaData.get(parameter);
    }

    /** Имеются ли OUT параметры в выражении */
    public boolean hasOutParameters()
    {
        return parametersMetaData.values().stream().anyMatch(ParameterMetadata::isOut);
    }

    /** */
    public CallWork createCallWork( TaskContext tc, IParameters callBackParameters ) {
        return createCallWork( tc, callBackParameters, true );

    }
    /** */
    public CallWork createCallWork( TaskContext tc, IParameters callBackParameters, boolean closeAfterExecute ) {

        try {
            return new CallWork( tc, this, callBackParameters, closeAfterExecute );
        }
        catch( Throwable th ) {
            throw new RuntimeException( Tags.PRODUCT_LABEL + "Error on create stored work", th );
        }
    }
}
