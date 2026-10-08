package ru.inversion.query.node;

import ru.inversion.parser.token.Token;

/** */
public class ParameterNode extends Node {

    final Token token;

    /** */
    public ParameterNode( CompositeNode parent, Token token ) {
        super(parent);
        this.token = token;
    }

    /** */
    @Override
    public NodeTypeEnum getType() {
        return NodeTypeEnum.PARAMETER;
    }

    /** */
    public Token.TypeEnum getParameterType() {
        return token.getType();
    }

    @Override
    public String toString() {
        return "ParameterNode{" +
                "token=" + token +
                '}';
    }
}
