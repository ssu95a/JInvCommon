package ru.inversion.dataset.parser;

/**
 *
 * @author ssu @
 */
public class SQLItem {

	public enum ItemTypeEnum {
		TEXT_EXPRESSION,
		INTEGER,
		STRING,
		NAMED_PARAMETER,
		COMMENT,
		INDEXED_PARAMETER,
		DECIMAL,
        UNCHANGED,
        HINT,
        QUOTES;

		/** */
		public boolean isConstant()
		{
			switch(this) {
				case INTEGER:
				case STRING:
				case DECIMAL:
					return true;
			}
			return false;
		}
	}
	
	private final ItemTypeEnum type;
	private final Object	   value;

	public SQLItem( Object value, ItemTypeEnum type) {
		this.value = value;
		this.type = type;
	}

	/** */
	public Object getValue( ) {
		return value;
	}

	/** */
	public ItemTypeEnum getType() {
		return type;
	}

	/** */
	public boolean isConstant() {
		return getType().isConstant();
	}

}
