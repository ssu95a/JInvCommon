package ru.inversion.dataset;

import java.util.Map;
import java.util.function.Function;

/**
  * Интерфейс для работы с набором именованных параметров.
  * Предполагается использование только именованных параметров.
  * 
  * @see IParameters 
  * @see ParametersByName 
  * 
  * @author sulimoff
  * @version 1.0.0
  */
@FunctionalInterface
public interface ParametersByName extends IParameters {
    /** Методы заглушки базового интерфейса */
    @Override
	default Object getParameter ( int parameterIndex ) { return null; }
    @Override
    default boolean hasParameter( int parameterIndex ) { return false;}
    
    /** 
     * Возвращает параметр по его имени.
     * см. {@link IParameters#getParameter}
     */
    @Override
    Object getParameter( String parameterName );

    /** */
    static ParametersByName of( Map<String,Object> map ) {
        return new ParametersByName() {
            @Override
            public Object getParameter( String parameterName ) {
                return map.get(parameterName);
            }
            @Override
            public boolean hasParameter( String parameterName ) {
                return map.containsKey(parameterName);
            }
        };
    }

    /** */
    static ParametersByName of( Function<String,Object> f ) {
        return new ParametersByName() {
            @Override
            public Object getParameter( String parameterName ) {
                return f.apply(parameterName);
            }
        };
    }

}
