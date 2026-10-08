package ru.inversion.query;

import ru.inversion.utils.U;

import java.util.concurrent.atomic.AtomicInteger;


public class PredicateItem {

    /** */
    public enum TypeEnum {

        PREDICATE,

        TEMPORARY_FILTER,

        PERMANENT_FILTER,

        AUTO_PREDICATE
    }

    final static private AtomicInteger g_predicateIDSequence = new AtomicInteger(0);

    /** */
    final private String text,
                         tag;
    /** */
    private boolean pre = true;

    /** */
    final private TypeEnum type;

    /** */
    final private Integer id = g_predicateIDSequence.incrementAndGet();

    /** */
    public PredicateItem( TypeEnum type, String text, String tag ) {
        this.text = text;
        this.tag  = tag;
        this.type = type;
    }

    /** */
    public boolean isPre() {
        return pre;
    }

    /** */
    public  boolean isFinal() {
        return !pre;
    }

    /** */
    public  boolean isFilter() {
        return U.in( type, TypeEnum.TEMPORARY_FILTER, TypeEnum.PERMANENT_FILTER );
    }

    /** */
    public  void makeFinal() {
        this.pre = false;
    }

    /** */
    public String getText() {
        return text;
    }

    /** */
    public String getTag() { return tag; }

    /** */
    public TypeEnum getType() {
        return type;
    }

    /** */
    public int getId() { return id; }
}
