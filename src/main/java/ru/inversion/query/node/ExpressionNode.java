package ru.inversion.query.node;

import ru.inversion.parser.token.Token;

import java.util.List;

/** */
public class ExpressionNode extends Node {

    final List<Token> tokenList;

    public ExpressionNode( List<Token> tokenList ) {
        this( null, tokenList );
    }

    public ExpressionNode( CompositeNode parent, List<Token> tokenList ) {
        super(parent);
        this.tokenList = tokenList;
    }

    @Override
    public NodeTypeEnum getType( ) {
        return NodeTypeEnum.EXPRESSION;
    }

    @Override
    public String toString() {
        return "ExpressionNode {tokenList=" + tokenList +'}';
    }
}
