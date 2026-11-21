package ru.inversion.datacall;

import ru.inversion.parser.token.Token;
import ru.inversion.utils.TriConsumer;

import java.util.function.BiConsumer;

/** Состояние одного прохода подготовки SQL. */
public class CallPrepareContext {

    /** осуществлять вывод или прекратить */
    private boolean enableOut = true;

    private final StringBuilder sql = new StringBuilder();

    /** режим замены констант на параметры */
    private boolean replaceConstantMode = true;

   /**  Обработчик параметров
     *  - индекс параметра в выражении
     *  - идентификатор параметра: String.class - named, Integer.class - indexed, Type.class - constant
     *  - значение константы
     */
    final private TriConsumer< Integer, Comparable, Object> parameterHandler;

    /** комментировать именные параметры */
    private boolean commentParameters = true;

    /** Пропускать комментарии */
    private boolean skipComments = true;

    /** Режим order by не заменяем константы */
    private boolean orderByMode = false;

    /** Глубина скобок, открытых после ORDER BY. */
    private int orderByParentheses;

    /** возможно ли замена констант на параметры */
    private boolean enableReplaceConstants = true;

    /** */
    final private BiConsumer< Token, CallPrepareContext > sqlMaker;

    /** */
    public CallPrepareContext(
            BiConsumer<Token, CallPrepareContext > sqlMaker,
            TriConsumer< Integer,Comparable,Object > parameterHandler
    )
    {
        this.sqlMaker           = sqlMaker;
        this.parameterHandler   = parameterHandler;
    }

    /** */
    public void setEnableReplaceConstants( boolean v ) {
        enableReplaceConstants = v;
    }
    /** */
    public boolean isEnableReplaceConstants() {
        return enableReplaceConstants;
    }

    /** */
    public boolean isSkipComments() {
        return skipComments;
    }
    public void setSkipComments( boolean skipComments ) {
        this.skipComments = skipComments;
    }

    /** */
    public boolean isOrderByMode() {
        return orderByMode;
    }

    /** */
    public void setOrderByMode( boolean orderByMode ) {
        this.orderByMode = orderByMode;
    }

    /** */
    public boolean isCommentParameters() {
        return commentParameters;
    }

    /** */
    public void setCommentParameters( boolean commentParameters ) {
        this.commentParameters = commentParameters;
    }

    /** */
    public boolean isReplaceConstantMode() {
        return isEnableReplaceConstants() && !orderByMode && replaceConstantMode;
    }

    /** */
    public void setReplaceConstantMode( ) {
        this.replaceConstantMode = !this.replaceConstantMode;
    }

    /** */
    public BiConsumer< Token, CallPrepareContext > sqlMaker() { return sqlMaker; }

    /** */
    private char lastChar() {
        return sql.length() == 0 ? 0 : sql.charAt(sql.length() - 1);
    }

    /** */
    public CallPrepareContext append( char ch ) {

        if( isEnableOut() )
            sql.append(ch);

        return this;
    }

    /** */
    public CallPrepareContext append( Object o ) {

        if( isEnableOut() )
            sql.append(o);

        return this;
    }

    /** */
    public CallPrepareContext append( Token t ) {

        switch( t.getType() ) {
            case ORDER_BY:
                setOrderByMode(true);
                break;
            case UNCHANGED_MARK:
                setReplaceConstantMode();
                break;
            case SEMICOLON:
                if( isOrderByMode() )
                    setOrderByMode(false);
                break;
            case PARENTHESIS_LEFT:
                if( isOrderByMode() )
                    orderByParentheses++;
                break;
            case PARENTHESIS_RIGHT:
                if( !isOrderByMode() )
                    break;
                if( orderByParentheses == 0 )
                    setOrderByMode(false);
                else
                    orderByParentheses--;
                break;
            case COMMENT:
                if( isSkipComments() )
                    return this;
                break;
        }

        if( isEnableOut() )
            t.accept(sql);

        return this;
    }

    /** */
    public CallPrepareContext nl( ) {
        return lastChar() == '\n' ? this : append('\n');
    }

    /** */
    public CallPrepareContext sp( ) {
        return lastChar() == ' ' ? this : append(' ');
    }

    /** */
    public boolean isEnableOut( ) {
        return enableOut;
    }

    /** */
    public void setEnableOut( boolean enableOut ) {
        this.enableOut = enableOut;
    }

    /** */
    public String getSQL() {
        return sql.toString();
    }

    /** */
    public TriConsumer< Integer,Comparable,Object > parameterHandler() {
        return parameterHandler;
    }
}
