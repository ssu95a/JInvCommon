package ru.inversion.dataset;

import java.util.List;
import java.util.Objects;

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
        Objects.requireNonNull(list, "list");

        return new ParametersByIndex() {
            @Override
            public Object getParameter( int parameterIndex ) {
                return hasParameter(parameterIndex)
                        ? list.get(parameterIndex)
                        : null;
            }
            @Override
            public boolean hasParameter( int parameterIndex ) {
                return parameterIndex >= 0 && parameterIndex < list.size();
            }
            @Override
            public boolean isEmpty() {
                return list.isEmpty();
            }
        };
    }
}
