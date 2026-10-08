package ru.inversion.datacall;

import ru.inversion.parser.token.Token;
import ru.inversion.utils.TriConsumer;

import java.util.function.BiConsumer;

public class CallPrepareContext {

    /** осуществлять вывод или прекратить */
    private boolean enableOut = true;

    /** буфер для вывода текста */
    final private StringBuilder a = new StringBuilder();

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

    /** текущий уровень вывода */
    private int level = 0;

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
        return a.length() == 0 ? 0 : a.charAt( a.length() - 1 );
    }

    /** */
    public CallPrepareContext append( char ch ) {

        if( isEnableOut() )
            //if( !U.inChar( lastChar(), ' ', '\n' ) )
                a.append(ch);

        return this;
    }

    /** */
    public CallPrepareContext append( Object o ) {

        if( isEnableOut() )
            a.append( o );

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
                    level++;
            break;
            case PARENTHESIS_RIGHT:
                if( isOrderByMode() )
                    if( level == 0 )
                        setOrderByMode(false);
                    else
                        level--;
            break;
            case COMMENT:
                if( isSkipComments() )
                    return this;
        }

        if( isEnableOut() ) {
            t.accept(a);
        }

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
        return a.toString();
    }

    /** */
    public TriConsumer< Integer,Comparable,Object > parameterHandler() {
        return parameterHandler;
    }
}
