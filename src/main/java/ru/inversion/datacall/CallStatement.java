package ru.inversion.datacall;

import ru.inversion.dataset.IParameters;
import ru.inversion.parser.statement.LangEnum;
import ru.inversion.parser.statement.PrepareModeEnum;
import ru.inversion.parser.token.Token;
import ru.inversion.tc.TaskContext;
import ru.inversion.utils.Tags;
import ru.inversion.utils.TriConsumer;
import ru.inversion.utils.U;

import javax.persistence.ParameterMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
        this.parametersMetaData   = parametersMetaData;
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

    /** */
    public boolean isAutoCommit() {
        return autoCommit;
    }

    /** */
    public String prepareSql( PrepareModeEnum prepareMode, List<Object> parametersValue )
    {
        return prepareSql( AbstractCallMakerBase.createPLSqlMaker(prepareMode), parametersValue );
    }

    /** */
    public String prepareSql( BiConsumer<Token, CallPrepareContext > sqlMaker,  List<Object> parametersValue ) {

        TriConsumer<Integer,Comparable,Object> handler = ( index, paramId, paramValue ) -> parametersValue.add( paramValue );

        return prepareSql( sqlMaker, handler );
    }

    /** */
    public String prepareSql( BiConsumer<Token, CallPrepareContext > sqlMaker, TriConsumer<Integer,Comparable,Object> handler )
    {
        CallPrepareContext context = new CallPrepareContext( sqlMaker, handler );

        if( removeAdditionalInfo ) {
            context.setSkipComments     (true );
            context.setCommentParameters(false);
        }

        context.setEnableReplaceConstants( replaceConstants );

        descriptor.preparePLSql( context );

        return context.getSQL( );
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
        //return outParameterOrder != null && !outParameterOrder.isEmpty();
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
