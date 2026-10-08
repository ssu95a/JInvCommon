package ru.inversion.query.node;

import ru.inversion.parser.token.Token;

public class OptimizerHintNode extends QueryNode {

    private String override;

    private final Token token;

    /** */
    public OptimizerHintNode( Token token ) {
        super(NodeTypeEnum.HINT);
        this.token = token;
    }
}
