package ru.inversion.swln;

import java.util.Set;
import java.util.TreeSet;

/** */
public class Obj {

    private String name, note;
    //private Obj parent;

    private Set<EventObj> events = new TreeSet<>();

    /** */
    public Obj( String name, String note )
    {
        this.name = name;
        this.note = note;
    }

    /** */
    public Obj getParent( )
    {
        return null;
    }


}
