package ru.inversion.datacall;

import ru.inversion.parser.statement.PrepareModeEnum;
import ru.inversion.datacall.maker.CallMaker_Query;
import ru.inversion.datacall.maker.CallMaker_QueryEx;
import ru.inversion.datacall.maker.CallMaker_QueryTextOnly;
import ru.inversion.datacall.maker.CallMaker_WithParametersValue;
import ru.inversion.parser.token.Token;
import ru.inversion.utils.Tags;

import java.util.function.BiConsumer;

/** */
public abstract class AbstractCallMakerBase implements BiConsumer< Token, CallPrepareContext > {

    public static BiConsumer<Token, CallPrepareContext > createPLSqlMaker( PrepareModeEnum prepareMode ) {

        switch( prepareMode ) {
            case QUERY:
                return new CallMaker_Query();
            case QUERY_EX:
                return new CallMaker_QueryEx();
//            case EXPLAIN_PLAN:
//            case META_DATA:
            case QUERY_WITH_VALUES:
                return new CallMaker_WithParametersValue();
            case QUERY_TEXT_ONLY:
                return new CallMaker_QueryTextOnly();
        }

        throw new IllegalArgumentException( Tags.PRODUCT_LABEL + "Error 'prepareMode' value: " + prepareMode );
    }

    /** */
    protected int nIndex = 0;
}
