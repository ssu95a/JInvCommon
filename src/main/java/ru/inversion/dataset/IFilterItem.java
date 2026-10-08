package ru.inversion.dataset;

/**
 * Один элемент компонентного фильтра для DataSet.
 * Состоит из имени поля и выражения предиката к этому полю.
 * в предикат SQL выражения DataSet будет добавлено:
 * {@code "and ( [column] [=] [value] )}
 * 
 * @author ssu
 */
public interface IFilterItem {

    /**
     * Возвращает название колонки в SQL выражении к которому должен быть установлен предикат.
     * Может быть как просто название колонки, так и любое выражение которое уместно в том SQL куда фильтр применяется.
     * Например: {@code  'upper(NAME)=upper(?)'}
     * 
     * @return  название поля в SQL выражении 
     */
    String getColumn( );

    /** 
     * Значение устанавливаемого предиката.
     * м.б. {@code null}, константа или выражение на SQL
     * в SQL будет подставлено , если:
     * - {@code null} - 'is null'
     * - константа - [column] = [value]
     * - выражение - [column] [value] 
     * 
     * @return значение.
     */
    Object getValue( );

    /**
     * Тип данных столбца на которое устанавливается предикат.
     * Не используется если значение - выражение, {@code isExpression == true }
     * 
     * @return Тип данных
     */
    Class getType( );

    /**
     * Признак, что в качестве значения предиката устанавливается выражение на SQL.
     * 
     * @return {@code true} - предикат выражение,
     *         {@false} - константа
     */
    default boolean isExpression() {
        return getValueType() == IFilterItem.ValueTypeEnum.EXPRESSION;
    }
    
    /**
     * Если proxyFor присутствует, значит текущий this объект является прокси фильтром для объекта, возвращаемого в этом методе
     * @return 
     */
    IFilterItem getProxyFor( );

    default boolean isIgnore95LikeSym() { return false;}

    enum ValueTypeEnum {
        VALUE, LIST, EXPRESSION;
    }

    default ValueTypeEnum getValueType() {return ValueTypeEnum.VALUE;}

}
