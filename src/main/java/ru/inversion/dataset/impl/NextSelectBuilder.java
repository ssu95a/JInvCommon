package ru.inversion.dataset.impl;

import ru.inversion.dataset.ISQLExpression;
import ru.inversion.db.dialect.SqlDialect;

/**
 * @deprecated use {@link DataSetSelectBuilder}.
 */
@Deprecated
public final class NextSelectBuilder extends DataSetSelectBuilder {

    public NextSelectBuilder(String sql, SqlDialect dialect) {
        super(sql, dialect);
    }

    @Override
    public NextSelectBuilder addPredicate(String sql) {
        super.addPredicate(sql);
        return this;
    }

    @Override
    public NextSelectBuilder addPredicate(ISQLExpression expression) {
        super.addPredicate(expression);
        return this;
    }

    @Override
    public NextSelectBuilder queryAlias(String alias) {
        super.queryAlias(alias);
        return this;
    }

    @Override
    public NextSelectBuilder optimizerHint(String hint) {
        super.optimizerHint(hint);
        return this;
    }

    @Override
    public NextSelectBuilder orderBy(String sql) {
        super.orderBy(sql);
        return this;
    }

    @Override
    public NextSelectBuilder orderBy(ISQLExpression expression) {
        super.orderBy(expression);
        return this;
    }

    @Override
    public NextSelectBuilder rowLimit(Integer value) {
        super.rowLimit(value);
        return this;
    }

    @Override
    public NextSelectBuilder mark(String expression) {
        super.mark(expression);
        return this;
    }
}
