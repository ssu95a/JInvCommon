package ru.inversion.dataset;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
  * Интерфейс для работы с набором параметров передаваемого в DataSet или 
  * другие объекты где необходимо задавать параметры.
 * <p>
  * Основное предназначение для передачи параметров, поэтому в наследуемых классах достаточно переопределить 
  * методы {@link IParameters#getParameter }
  * 
  * Предполагается совместное и независимое друг от друга использование именованных и индексируемых параметров.
  * 
  * @see ParametersByIndex 
  * @see ParametersByName 
  * @see IDataSet
  * 
  * @author Sulimoff
  * @version 2.0.1
 */
public interface IParameters {

    public static IParameters EMPTY = new IParameters() {
        @Override
        public Object getParameter ( String parameterName) { return null; }
        @Override
        public Object getParameter ( int parameterIndex  ) { return null; }
        @Override
        public boolean hasParameter( int parameterIndex  ) {
            return false;
        }
        @Override
        public boolean hasParameter( String parameterName) {
            return false;
        }
        @Override
        public boolean isEmpty() { return true;}
    };

    /** 
     * Константа для случая когда значение параметра не установлено.
     * <p>
     * Для тех случаев когда необходимо разделять ситуации когда параметр не задан и когда он равен {@code null}
     */
    public static final Object NO_PARAMETER_VALUE = new Object();
    
    /** 
     * Возвращает значение параметра с именем {@code parameterName} или {@code null} если такой параметр отсутствует.
     * <p>
     * Если необходимо отделять ситуацию когда параметр реально отсутствует и когда может равен {@code null},
     * то нужно воспользоваться методом {@link #hasParameter}
     * 
     * @param parameterName имя параметра значение которого нужно получить
     * 
     * @return значение параметра с переданным именем, или {@code null} если такого нет
     * 
     * @see #hasParameter
     */
	Object getParameter( String parameterName );

    /** 
     * Возвращает значение параметра с индексом {@code parameterIndex} или {@code null} если такой параметр отсутствует.
     * <p>
     * Если необходимо отделять ситуацию когда параметр реально отсутствует и когда может содержать {@code null},
     * то нужно воспользоваться методом {@link #hasParameter}
     * 
     * @param parameterIndex 
     *          индекс параметра, значение которого нужно получить,
     * 
     * @return значение параметра с индексом, или {@code null} если такого нет
     * 
     * @see #hasParameter
     */
	Object getParameter( int parameterIndex );

    /** 
     * Установка значения именованного параметра {@code parameterName}.
     * <p>
     * Если для данного параметра значение было установлено, то оно подменяется на новое.
     * Установлено ли значение для параметра возможно проверить через метод {@link #hasParameter}.
     * 
     * Не является обязательным для реализации.
     * 
     * @param parameterName 
     *          имя параметра значение которого нужно установить
     * 
     * @param value         
     *          значение параметра, допускается передача {@code null}
     * 
     * @throws UnsupportedOperationException если имплементирующий класс не реализует этот метод
     */
	default 
        void setParameter( String parameterName, Object value ) { 
            throw new UnsupportedOperationException( "setParameter( String, Object )" );
        }

    /** 
     * Установка значения параметра по индексу {@code parameterIndex}.
     * <p>
     * Если для данного параметра значение было установлено, то оно подменяется на новое.
     * Есть ли значение для параметра возможно проверить через метод {@link #hasParameter}.
     * 
     * Не является обязательным для реализации.
     * 
     * @param parameterIndex
     *          индекс, параметра значение которого нужно получить
     *
     * @param value
     *          значение параметра, допускается передача {@code null}
     *
     * @throws UnsupportedOperationException если имплементирующий класс не реализует этот метод
     */
	default
        void setParameter( int parameterIndex, Object value )   {
            throw new UnsupportedOperationException( "setParameter( int, Object )" );
        }

    /** 
     * Установка значения именованного параметра {@code parameterName}.
     * <p>
     * Является дублирующим для метода {@link #setParameter}.
     * Применяется если пишется код в стиле "Fluent interface"
     * 
     * @param parameterName 
     *          имя параметра значение которого нужно установить
     * 
     * @param value         
     *          значение параметра, допускается передача {@code null}
     *
     * @return ссылку на объект, самого себя, {@code this}
     * 
     * @see #setParameter
     */
    default
        IParameters set( String parameterName, Object value ) {
            setParameter(parameterName, value); return this;
        }
    
    /** 
     * Установка значения параметра по индексу {@code parameterIndex}.
     * <p>
     * Является дублирующим для метода {@link #setParameter}.
     * Применяется если пишется код в стиле "Fluent interface"
     * 
     * @param parameterIndex 
     *          индекс, параметра значение которого нужно получить
     * 
     * @param value          
     *          значение параметра, допускается передача {@code null}
     * 
     * @return ссылку на объект, самого себя, {@code this}
     * 
     * @see #setParameter
     */
    default
        IParameters set( int parameterIndex, Object value ) {
            setParameter(parameterIndex,value); return this;
        }
    
    /** 
     * Установка значения параметров из JavaBean.
     * <p>
     * Параметры и значение берутся из переданного объекта. 
     * Поведение метода определяется имплементацией класса, реализующей интерфейс. 
     * 
     * Не является обязательным для реализации.
     * 
     * @param bean 
     *        объект, откуда нужно брать параметры,
     *        м.б. {@code null}, тогда никаких параметров не устанавливается.
     * 
     * @throws UnsupportedOperationException 
     *          если имплементирующий класс не реализует этот метод
     * 
     * @see #setParameter
     */
    default 
        void setParameterBean( Object bean ) { 
            throw new UnsupportedOperationException( "setParameterBean( Object bean )" );
        }
    
    /**
     * Получение {@code map} со значениями именованных параметров.
     * <p>
     * Возвращаются только именованные параметры установленные ранее.
     * Нет ограничения на возврат копии {@code map} параметров или возвращении {@code map} только для чтения, 
     * либо просто ссылку на внутреннюю коллекцию, поведение метода зависит от реализации.
     * 
     * Не является обязательным для реализации.
     * 
     * @return  {@code Map <String,Object>} с именованными параметрами, 
     *           если нет установленных именованных параметров, то предполагается что возвращается пустая {@code map}, не null!
     * 
     * @throws UnsupportedOperationException 
     *         если имплементирующий класс не реализует этот метод
     * 
     * @see #getParametersList
     */        
    default 
        Map <String,Object> getParametersMap( ) { 
            throw new UnsupportedOperationException( "getParametersMap( )" );
        }
    
    /** 
     * Получение {@code map} со значениями именованных параметров.
     * <p>
     * Возвращаются только именованные параметры установленные ранее.
     * Необходимо заполнить {@code map} переданный из вне, если установлен признак {@code onlyDef == true}
     * то заполняется только те параметры, наименование которых передано в ключе {@code valuMap}, в противном случае
     * нужно вернуть все параметры.
     *  
     * Не является обязательным для реализации.

     * @param valueMap  
     *          {@code map} куда необходимо скопировать именованные параметры, 
     *          если передан {@code null}, то ничего не происходит
     * 
     * @param onlyDef
     *          {@code true } - необходимо установить значения только тех параметров, наименование которых содержатся в ключах valueMap.
     *          {@code false} - необходимо скопировать все параметры  
     * 
     * @throws UnsupportedOperationException 
     *          если имплементирующий класс не реализует этот метод
     */
    default 
        void getParametersMap( Map <String,Object> valueMap, boolean onlyDef ) { 
            throw new UnsupportedOperationException( "getParametersMap.2( )" );
        }
    
    /** 
     * Получение списка индексированных параметров.
     * <p>
     * Возвращаются только индексированные параметры установленные ранее.
     * Нет ограничения на возврат копии списка параметров или возвращении списка только для чтения, 
     * либо просто ссылку на внутреннюю коллекцию, данное поведение зависит от реализации.
     * 
     * Не является обязательным для реализации.
     * 
     * @return  {@code List<Object>} с индексированными параметрами, 
     *           если нет установленных индексированных параметров то предполагается возвращение пустого {@code List} не {@code null}!
     * 
     * @throws UnsupportedOperationException 
     *          если имплементирующий класс не реализует этот метод
     * 
     * @see #getParametersMap
     */
    default 
        List<Object> getParametersList( ) { 
            throw new UnsupportedOperationException( "getParametersList( )" );
        }
    
    /** 
     * Получение списка индексированных параметров.
     * <p>
     * Возвращаются только индексированные параметры установленные ранее, в переданную коллекцию.
     * 
     * Не является обязательным для реализации.
     * 
     * @param valueList 
     *          {@code List} куда необходимо скопировать индексированные параметры, если они присутствуют.
     *          если передан {@code null}, то ничего не происходит.
     * 
     * @throws UnsupportedOperationException 
     *         если имплементирующий класс не реализует этот метод
     * 
     * @see #getParametersMap
     */
    default 
        void getParametersList( List<Object> valueList ) { 
            throw new UnsupportedOperationException( "getParametersList.2( )" );
        }
    
    /** 
     * Проверка наличия индексированного параметра с определенным индексом.
     * <p>
     * В реализации по умолчанию считается, что параметр присутствует всегда,
     * и если реально нет с таким индексом то в качестве значения возвращается {@code null}
     * 
     * @param parameterIndex 
     *        индекс параметра который проверяется
     * 
     * @return {@code true} если параметр присутствует, {@code false} если нет.
     * 
     * @see #getParameter(int) 
     */
    default 
        boolean hasParameter( int parameterIndex ) { 
            return true; 
        }
    
    /** 
     * Проверка наличия именованного параметра с определенным именем.
     * <p>
     * В реализация по умолчанию, считается что параметр присутствует всегда, 
     * и если реально нет с таким именем то в качестве значения возвращается {@code null}
     * 
     * @param parameterName 
     *          наименование параметра  который проверяется
     * 
     * @return {@code true} если параметр присутствует, {@code false} если нет.
     * 
     * @see #getParameter(String) 
     */
    default 
        boolean hasParameter( String parameterName ) { 
            return true; 
        }

    /** */
    default boolean isEmpty() { return false; }

    /** */
    default Set<String> getParametersNames() {
        throw new UnsupportedOperationException( "getParametersNames" );
    }

    /** */
    default <T extends IParameters> T to() { return (T)this; }
}
