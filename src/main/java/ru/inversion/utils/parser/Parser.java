package ru.inversion.utils.parser;

import java.math.BigDecimal;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;

import static ru.inversion.utils.parser.Token.*;
import static ru.inversion.utils.parser.Token.TypeEnum.*;

/**
 *
 */
class Parser implements Iterator<Token<?>> {

    /**
     *
     */
    private enum FrameStateEnum {
        UNDEFINED,
        SIGNED_DECIMAL, // -1.1 или +1.1
        UNSIGNED_DECIMAL
    }

    /**
     *
     */
    private static class FrameContext {

        final private Token< ? >[] buffer = new Token[4];
        final private Iterator<Token< ? >> baseIter;

        private Token< ? > lastToken;

        public FrameContext( Iterator<Token< ? >> baseIter ) {

            this.baseIter = baseIter;

            for(int i = 0; i < buffer.length && baseIter.hasNext(); i++) {
                buffer[i] = baseIter.next();
            }
        }

        /**
         *
         */
        Token< ? >[] buffer() {
            return buffer;
        }

        /**
         *
         */
        public Token< ? > readNext() {
            return baseIter.hasNext() ? baseIter.next() : null;
        }

        /**
         *
         */
        public boolean eof() {
            return buffer[0] == null && !baseIter.hasNext();
        }

        /**
         *
         */
        private boolean isFilled( int count ) {

            if(buffer.length < count)
                return false;

            for(int i = 0; i < count; i++) {
                if(buffer[i] == null)
                    return false;
            }

            return true;
        }

        /**
         *
         */
        private void shift( int count ) {
            // Сдвигаем буфер
            for(int i = 0; i < buffer.length - count; i++)
                buffer[i] = buffer[i + count];

            for(int i = buffer.length - count; i < buffer.length; i++) {
                buffer[i] = readNext();
            }
        }

        /**
         *
         */
        public FrameStateEnum getState() {

            boolean doIt = isFilled(4);

            if(doIt)
            {
                if( (buffer[0] == Minus || buffer[0] == Plus) && buffer[1].isInteger() && buffer[2] == Token.Dot && buffer[3].isInteger())
                    return FrameStateEnum.SIGNED_DECIMAL;
            }

            if(!doIt)
                doIt = isFilled(3);

            if(doIt)
            {
                if( buffer[0].isInteger() && buffer[1] == Token.Dot && buffer[2].isInteger() )
                    return FrameStateEnum.UNSIGNED_DECIMAL;
            }

            return FrameStateEnum.UNDEFINED;
        }

        /**
         *
         */
        public boolean isUnaryContext() {
            return lastToken == null || !lastToken.isNumeric();
        }
    }

    final private FrameContext frameContext;

    /**
     *
     */
    Parser( Iterator<Token<?>> baseIter ) {
        frameContext = new FrameContext(Objects.requireNonNull(baseIter, "'baseIter' is null"));
    }

    /**
     *
     */
    private Token< ? > tryDecimal() {

        final Token< ? >[] buffer = frameContext.buffer();
        final FrameStateEnum state = frameContext.getState();
        final Token< ? > result;
        final String s;

        if(state == FrameStateEnum.SIGNED_DECIMAL) {
            if(frameContext.isUnaryContext()) {
                s = buffer[0].getValue().toString() + buffer[1].getValue().toString() + "." + buffer[3].getValue().toString();
                frameContext.shift(4);
                result = new Token<>(DECIMAL, new BigDecimal(s));
            } else {
                result = buffer[0];
                frameContext.shift(1);
            }
        } else if(state == FrameStateEnum.UNSIGNED_DECIMAL) {
            s = buffer[0].getValue().toString() + "." + buffer[2].getValue().toString();
            frameContext.shift(3);
            result = new Token<>(DECIMAL, new BigDecimal(s));
        } else
            result = null;

        return result;
    }


    @Override
    public boolean hasNext() {
        return !frameContext.eof();
    }

    /**
     *
     */
    @Override
    public Token< ? > next() {

        final Token< ? > n = nextToken();

        frameContext.lastToken = n;

        return n;
    }

    /**
     *
     */
    private Token<?> nextToken() {

        if( !hasNext() )
            throw new NoSuchElementException();

        Token<?> result = tryDecimal();
        if(result != null)
            return result;

        result = frameContext.buffer()[0];

        frameContext.shift(1);

        return result;
    }
}
