package ru.inversion.parser.node;

import ru.inversion.parser.token.Token;

import java.util.function.Function;

/**
 * Created by ssu on 14.07.2017.
 */

/** */
public interface INodeState<N extends INode> extends Function<INodeState.Context,N> {
    /** */
    public static class Context {

        private Token
                current,
                next;
        /** */
        private CompositeNode parent;

        /** */
        public Context( ) {
        }

        /** */
        public Context move( Token n ) {

            this.current = next;
            this.next    = n;

            return this;
        }

        /** */
        public Token current() {
            return current;
        }

        /** */
        public Token next() {
            return next;
        }

        /** */
        public CompositeNode getParent() {
            return parent;
        }

        /** */
        public void setParent( CompositeNode parent ) {
            this.parent = parent;
        }
    }

    /** */
    static Function<String,NodeTypeEnum> g_getNodeType = s -> {
        for(NodeTypeEnum n : NodeTypeEnum.values()) {
            if( n.name().equals(s) )
                return n;
        }
        return null;
    };

}
