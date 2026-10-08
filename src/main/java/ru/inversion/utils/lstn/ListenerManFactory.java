package ru.inversion.utils.lstn;

import java.util.EventObject;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 *
 * @author ssu
 */
public class ListenerManFactory {
    
    /** */
    private static class LME<L,E extends EventObject> extends ListenerManBase<L> implements IListenerManEvent<L,E> {
        
        private final BiConsumer<L,E> fireStarter;
        
        /** */
        public LME( BiConsumer<L,E> fireStarter ) {
            this.fireStarter = Objects.requireNonNull( fireStarter, "fireStarter is null" );
        }
        
        /** */
        @Override
        public void fire( E event ) {
            fireListeners( event, (L t)-> fireStarter.accept(t, event ));
        }
    }
    
    private static class LMC<L> extends ListenerManBase<L> implements IListenerManConsumer<L> {

        /** */
        public LMC( ) {
        }
        
        /** */
        @Override
        public void fire( Consumer<L> consumer ) {

            if( consumer == null )
                return;

            fireListeners( null, consumer);
        }
    }
    
    /** */
    public static <L,E extends EventObject> IListenerManEvent<L,E> createListenerManEvent( BiConsumer<L,E> fireStarter ) { 
        return new LME<>(fireStarter);
    }
    
    /** */
    public static <L> IListenerManConsumer<L> createListenerManConsumer( ) { 
        return new LMC<>( );
    }
   
}
