package ru.inversion.dataset.mark;

/**
 * Возможные способы организации пометки.
 * <p>
 * Заложены на существующие механизмы ЦАБС "XXI век".
 * 
 * @author sulimoff
 */
public enum MarkModeEnum {

    /**
     * Пометка не поддерживается.
     */
	NONE, 
    
    /**
     * В качестве PK для пометки выступает ROWID записи.
     * <p>
     * Отметки записей хранятся в таблице MRK
     */
    MRK,
    
    /**
     * В качестве PK для пометки выступает поле типа INTEGER.
     * <p>
     * Отметки записей хранятся в таблице MRK_ID
     */
    MRK_ID, 
    
    /**
     * В качестве PK для пометки выступает 2 поля типа INTEGER.
     * <p>
     * Отметки записей хранятся в таблице MRK_ID2
     */
    MRK_ID2, 
    
    /**
     * В качестве PK для пометки выступает поле типа VARCHAR2.
     * <p>
     * Отметки записей хранятся в таблице MRK_U
     */
    MRK_U,

    MRK_CUSTOM;

    /** 
     * Переводит enum тип в целочисленный.
     * <p>
     * Нужно для выполнения хранимых процедур связанных с пометкой.
     * */
    public int toInt( ) {
        
		int result = -1;
        
		switch (this) {
            case MRK:
                result = 0;
                break;
            case MRK_ID:
                result = 1;
                break;
            case MRK_ID2:
                result = 2;
                break;
            case MRK_U:
                result = 3;
                break;
            case MRK_CUSTOM:
                result = 4;
                break;
        }
        return result;
    }

    /** */
    static public MarkModeEnum fromClass( Class clazz ) {

        if( clazz == null || !IMarkable.class.isAssignableFrom(clazz) )
            return NONE;

        if( IIDMarkable.class.isAssignableFrom(clazz) )
            return MRK_ID;
        else if( IRowIDMarkable.class.isAssignableFrom(clazz) )
            return MRK;
        else if( IUMarkable.class.isAssignableFrom(clazz) )
            return MRK_U;
        else if( IID2Markable.class.isAssignableFrom(clazz) )
            return MRK_ID2;

        return NONE;
    }
}
