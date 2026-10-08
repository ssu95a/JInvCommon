package ru.inversion.dataset;

import ru.inversion.utils.IExceptionInfo;
import ru.inversion.utils.S;
import ru.inversion.utils.Tags;

/**
 * Класс исключений для ошибочных ситуаций при работе с параметрами DataSet.
 * <p>
 * Реализован как Runtime, т.к. может возникнуть из разных неожиданных мест, 
 * в т.ч. и из лямбд, которые объявлены без исключения.
 * 
 * @see IExceptionInfo
 * @author sulimoff
 */
public class DataSetParameterException extends RuntimeException implements IExceptionInfo {
    
    /** */
    public DataSetParameterException( String message ) {
        super(message);
    }
    
    /** */
    public DataSetParameterException( String message, Throwable cause ) {
        super(message, cause);
    }
    
    /** */
    public DataSetParameterException( Throwable cause ) {
        super(cause);
    }

    /** */
    @Override
    public String getCategory( ) {
        return "DataSet";
    }
    
    /** 
     * Выбрасывает исключение если не задано значение индексированного параметра DataSet.
     * @param index
     *          индекс параметра.
     */
     public static void throwNoSetParameterValue( int index ) {
        throw new DataSetParameterException( Tags.PRODUCT_LABEL + "No value for parameter '" + index + "'");
    }
    
    /** 
     * Выбрасывает исключение если не задано значение именованного параметра DataSet.
     * @param name
     *          имя параметра.
     */
    public static void throwNoSetParameterValue( String name ) {
        throw new DataSetParameterException( Tags.PRODUCT_LABEL + "No value for parameter '" + S.nvl(name) + "'");
    }
}
