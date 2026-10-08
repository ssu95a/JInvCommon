package ru.inversion.parser.statement;

import java.util.List;
import java.util.function.BiConsumer;
import ru.inversion.dataset.IParameters;
import ru.inversion.parser.node.INode;

/** */
public class PrepareContext {

    /** */
    private boolean enableOut = true;

    /** */
    private int level = 0;

    /** */
    final private StringBuilder a = new StringBuilder();

    /** */
    private boolean replaceConstantMode = true;

    /** */
    final private IParameters callBackParameters;

    /** */
    private List<Object> parametersValues;

    /** */
    final private BiConsumer<INode,PrepareContext> sqlMaker;

    /** */
    public PrepareContext (
        BiConsumer<INode,PrepareContext> sqlMaker,
        IParameters callBackParameters,
        List<Object> parametersValues
    )
    {
        this.callBackParameters = callBackParameters;
        this.sqlMaker           = sqlMaker;
        this.parametersValues   = parametersValues;
    }

    /** */
    public BiConsumer< INode, PrepareContext > sqlMaker( ) {
        return sqlMaker;
    }

    /** */
    public boolean isReplaceConstantMode() {
        return replaceConstantMode;
    }

    /** */
    public void setReplaceConstantMode( ) {
        this.replaceConstantMode = !this.replaceConstantMode;
    }

    /** */
    public PrepareContext append( char ch ) {

        if( isEnableOut() )
            a.append(ch);

        return this;
    }

    /** */
    public PrepareContext append( Object o ) {

        if( isEnableOut() )
            a.append(o);

        return this;
    }

    /** */
    public PrepareContext nl( ) {
        return append('\n');
    }

    /** */
    public PrepareContext sp( ) {
        return append(' ');
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
    public IParameters callBackParameters() {
        return callBackParameters;
    }

    /** */
    public String getSQL() {
        return a.toString().replaceAll(" +", " ").replaceAll(" ,", ", ");
    }

    /** */
    public List< Object > parametersValues() {
        return parametersValues;
    }

    /** */
    public void incrementLevel() {
        level++;
    }

    /** */
    public void decrementLevel() {
        level--;
    }

    /** */
    public int getLevel() { return level; }
}
