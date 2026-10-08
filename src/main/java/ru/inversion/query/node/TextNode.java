package ru.inversion.query.node;

import static ru.inversion.query.node.NodeTypeEnum.TEXT;

/** */
public class TextNode extends Node {

    final private String text;

    public TextNode( CompositeNode parent, String text ) {
        super(parent);
        this.text = text;
    }

    public TextNode( String text ) {
        super( );
        this.text = text;
    }

    @Override
    public NodeTypeEnum getType() {
        return TEXT;
    }
}
