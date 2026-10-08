package ru.inversion.datacall;


import java.util.EventListener;

/**
 * Слушатель событий DataCall.
 * <p>
 * Вызывается из DataCall при наступлении событий.
 * Может быть зарегистрирован/отменен в DataCall через
 * {@link IDataCall#addDataCallListener(IDataCallListener)}/{@link IDataCall#removeDataCallListener(IDataCallListener)}
 *
 * При добавлении одного и того же экземпляра слушателя несколько раз, будет зарегистрирован только один раз.
 * 
 * @see IDataCall
 * @see DataCallEvent
 * 
 * @author sulimoff
 */
public interface IDataCallListener extends EventListener {
	
    /** 
     * Метод который вызывается при наступлении событий в DataCall
     * <p>
     * @param e
     *          Событие из DataCall
     * 
     * @see DataCallEvent
     */
	void dataCallChanged( DataCallEvent e );
}
