package ru.inversion.dataset.parser;

/**
 *
 * @author ssu
 */
public class ExprInfo {

    /** */
    private boolean mysterious;
    
    /** */
    private String  alias,
                    tableFrom;
    
    /** */
    private boolean hasOrderBy, hasWhere;

    /** */
    public boolean isMysterious( ) {
        return mysterious;
    }

    /** */
    public void setMysterious( boolean mysterious ) {
        this.mysterious = mysterious;
    }

    /** */
    public String getAlias( ) {
        return alias;
    }

    /** */
    public void setAlias( String alias ) {
        this.alias = alias;
    }

    /** */
    public String getTableFrom( ) {
        return tableFrom;
    }

    /** */
    public void setTableFrom( String tableFrom ) {
        this.tableFrom = tableFrom;
    }

    /** */
    public boolean isHasOrderBy( ) {
        return hasOrderBy;
    }

    /** */
    public void setHasOrderBy( boolean hasOrderBy ) {
        this.hasOrderBy = hasOrderBy;
    }
    
    /** */
    public boolean isHasWhere( ) {
        return hasWhere;
    }

    /** */
    public void setHasWhere( boolean hasWhere ) {
        this.hasWhere = hasWhere;
    }
}
