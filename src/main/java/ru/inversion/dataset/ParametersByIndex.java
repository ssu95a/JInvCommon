package ru.inversion.dataset;

import java.util.List;

/**
  * Интерфейс для работы с набором индексируемых параметров.
  * Предполагается использование только индексируемых параметров.
  * 
  * @see IParameters 
  * @see ParametersByName 
  * 
  * @author sulimoff
  * @version 1.0.0
  */
@FunctionalInterface
public interface ParametersByIndex extends IParameters {
    /** Методы заглушки базового интерфейса */
    @Override
    default Object getParameter( String parameterName ) {return null;}
    @Override
    default boolean hasParameter(String parameterName) { return false; }

    /** 
     * Возвращает параметр по его индексу.
     * см. {@link IParameters#getParameter}
     */
    @Override Object getParameter( int parameterIndex );

    /** */
    static ParametersByIndex of( List<Object> list ) {
        return new ParametersByIndex() {
            @Override
            public Object getParameter( int parameterIndex ) {
                return list.get(parameterIndex);
            }
            @Override
            public boolean hasParameter( int parameterIndex ) { return parameterIndex > -1 && parameterIndex <  list.size(); }
        };
    }
}
