package ru.inversion.swln;

import java.time.LocalDate;

/** */
public class EventObj implements Comparable<EventObj>{

    private LocalDate happened;
    private char      let = 0;
    private String    info;
    //
    @Override
    public int compareTo( EventObj o ) {
        int r = happened.compareTo(o.happened);
        if( r == 0 )
            r = Character.compare( let, o.let );
        return r;
    }
}
