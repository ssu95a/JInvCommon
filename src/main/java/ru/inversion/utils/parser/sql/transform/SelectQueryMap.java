package ru.inversion.utils.parser.sql.transform;

import ru.inversion.utils.parser.text.TextRange;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Карта значимых участков внешнего SELECT-запроса.
 */
public final class SelectQueryMap {

    private final SqlAnchor selectItemInsertion;

    /*
     * Если WHERE существует, заполнен wherePredicateRange.
     * Если WHERE отсутствует, заполнен whereInsertion.
     */
    private final TextRange wherePredicateRange;
    private final SqlAnchor whereInsertion;

    /*
     * Если ORDER BY существует, заполнен orderByItemsRange.
     * Если ORDER BY отсутствует, заполнен orderByInsertion.
     */
    private final TextRange orderByClauseRange;
    private final SqlAnchor orderByInsertion;

    private final List<SqlParameterOccurrence> parameters;

    public SelectQueryMap (
        SqlAnchor selectItemInsertion,
        TextRange wherePredicateRange,
        SqlAnchor whereInsertion,
        TextRange orderByClauseRange,
        SqlAnchor orderByInsertion,
        List<SqlParameterOccurrence> parameters
    )
    {
        this.selectItemInsertion = Objects.requireNonNull( selectItemInsertion, "selectItemInsertion" );

        requireExactlyOne( wherePredicateRange, whereInsertion, "Exactly one of wherePredicateRange and whereInsertion must be specified" );
        requireExactlyOne( orderByClauseRange, orderByInsertion,"Exactly one of orderByClauseRange and orderByInsertion must be specified");

        this.wherePredicateRange = wherePredicateRange;
        this.whereInsertion      = whereInsertion;

        this.orderByClauseRange  = orderByClauseRange;
        this.orderByInsertion    = orderByInsertion;

        Objects.requireNonNull( parameters, "parameters" );

        List<SqlParameterOccurrence> copy = new ArrayList<SqlParameterOccurrence>( parameters.size() );

        for (int index = 0; index < parameters.size(); index++)
        {
            copy.add( Objects.requireNonNull( parameters.get(index), "parameters[" + index + "]" ) );
        }

        this.parameters = Collections.unmodifiableList(copy);
    }

    /**
     * Позиция перед первым элементом списка внешнего SELECT.
     * <p>
     * Туда вставляется mark-column
     */
    public SqlAnchor selectItemInsertion() {
        return selectItemInsertion;
    }

    /**
     * Возвращает true, если внешний SELECT содержит WHERE.
     */
    public boolean hasWhere() {
        return wherePredicateRange != null;
    }

    /**
     * Диапазон предиката существующего внешнего WHERE.
     *
     * <p>Диапазон не включает слово WHERE и trivia
     * непосредственно после и перед соседними предложениями.</p>
     *
     * @throws IllegalStateException если WHERE отсутствует
     */
    public TextRange wherePredicateRange() {
        if (!hasWhere()) {
            throw new IllegalStateException(
                    "WHERE is absent"
            );
        }

        return wherePredicateRange;
    }

    /**
     * Позиция, в которой можно вставить новый WHERE.
     *
     * @throws IllegalStateException если WHERE уже существует
     */
    public SqlAnchor whereInsertion() {
        if (hasWhere()) {
            throw new IllegalStateException(
                    "WHERE is present"
            );
        }

        return whereInsertion;
    }

    /**
     * Возвращает true, если внешний SELECT содержит ORDER BY.
     */
    public boolean hasOrderBy() {
        return orderByClauseRange != null;
    }

    /**
     * Диапазон элементов существующего внешнего ORDER BY.
     *
     * <p>Диапазон не включает слова ORDER BY и trailing trivia.</p>
     *
     * <p>Например, для:</p>
     *
     * <pre>
     * order by c1 desc, lower(c2)
     * </pre>
     *
     * <p>диапазон соответствует:</p>
     *
     * <pre>
     * c1 desc, lower(c2)
     * </pre>
     *
     * @throws IllegalStateException если ORDER BY отсутствует
     */
    public TextRange orderByClauseRange() {
        if (!hasOrderBy()) {
            throw new IllegalStateException(
                    "ORDER BY is absent"
            );
        }

        return orderByClauseRange;
    }

    /**
     * Позиция, в которой можно вставить новый ORDER BY.
     *
     * @throws IllegalStateException если ORDER BY уже существует
     */
    public SqlAnchor orderByInsertion() {
        if (hasOrderBy()) {
            throw new IllegalStateException(
                    "ORDER BY is present"
            );
        }

        return orderByInsertion;
    }


    public boolean orderByHasParameters() {
        if (!hasOrderBy()) {
            return false;
        }

        for (SqlParameterOccurrence parameter
                : parameters) {

            TextRange parameterRange =
                    parameter.range();

            if (orderByClauseRange.start()
                    <= parameterRange.start()
                    && parameterRange.end()
                    <= orderByClauseRange.end()) {

                return true;
            }
        }

        return false;
    }

    /**
     * Все найденные параметры в порядке появления.
     */
    public List<SqlParameterOccurrence> parameters() {
        return parameters;
    }

    private static void requireExactlyOne( Object existingClause, Object insertion, String message )
    {
        if( (existingClause == null) == (insertion == null) )
        {
            throw new IllegalArgumentException( message );
        }
    }
}