package ru.inversion.utils;

/**
 *
 * @author ssu
 */
public interface IExceptionInfo {
    
    /** */
    default String getCategory( )        { return null; }
    
    /**
     * Детали связанные с сообщением об ошибке.
     */
    default String getDetailedMessage( ) { return null; }

    /** 
     * Дополнительное, пояснительное, содержательное сообщения об ошибке.
     */
    default String getContentText( )     { return null; }

    /** 
     * Основное, "головное", сообщение об ошибке.
     */
    default String getHeaderText( )      { return ((Throwable)this).getLocalizedMessage();}

    /** */
    default boolean isResolved() { return false; }

    /** 
     * Отключен из-за двусмысленности названия.
     * Имелось ввиду основное сообщение об исключении, а не заголовок окна сообщения об ошибке
     * Перенесено в метод getHeaderText()
     */
    @Deprecated
    default String getTitle( )            { return getHeaderText() ;}
    /** */
    @Deprecated
    default String getMessageText( )     { return null; }
}
