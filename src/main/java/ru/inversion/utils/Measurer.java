package ru.inversion.utils;

import java.time.Duration;
import java.util.function.Consumer;

/**
 *
 * @author sulimoff
 */
public class Measurer implements AutoCloseable {
    
    /** */
    final private Consumer<Duration> consumer;
    
    /** */
    final long    start = System.nanoTime();
    
    /** */
    private Duration duration;
            
    /** */
    public Measurer( Consumer<Duration> consumer ) {
        this.consumer = consumer;
    }
    
    /** */
    public Duration getDuration() {
        return duration;
    }
    
    /** */
    @Override
    public void close( ) {
        
        duration = Duration.ofNanos( System.nanoTime() - start );
        
        if( consumer != null )
            consumer.accept(duration);
    }
}
