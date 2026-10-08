package ru.inversion.utils.parser.state;

import ru.inversion.utils.parser.ITokenHandler;

/** */
public abstract class AbstractTokenHandler<T> implements ITokenHandler<T> {

    private int useCount = 0;

    @Override
    public int getUseCount() {
        return useCount;
    }

    @Override
    public void incrementUse() {
        useCount++;
    }
}
