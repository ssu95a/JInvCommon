package ru.inversion.utils;

import java.util.Objects;


public class Pair<T1, T2>
{
	public static final Pair<?,?> NullPair = new Pair<>( null, null );

	public T1 first;
	public T2 second;

	public Pair()
	{ }
	/** */
	public Pair( T1 first, T2 second )
	{
		this.first = first;
		this.second = second;
	}
	/** */
	public void setFirst( T1 first ) {
		this.first = first;
	}
	/** */
	public void setSecond( T2 second ) {
		this.second = second;
	}
	/** */
	public T1 getFirst(  ) {
		return first;
	}
	/** */
	public T2 getSecond( ) {
		return second;
	}
	/** */
	@Override
	public String toString()
	{
		return "pair: 1) = " + first + ", 2)  = " + second;
	}

	/** */
	public Object[] toArray()
	{
		return new Object[]{ getFirst(), getSecond() };
	}

	/** */
	public Pair<T1, T2> assign( Pair<T1, T2> pair )
	{
		first  = pair.first;
		second = pair.second;
		return this;
	}
	/** */
	@Override
	public boolean equals(Object obj) {
		Pair p = (Pair)obj;
		return this.first.equals(p.first) && this.second.equals(p.second);
	}
	/** */
	@Override
	public int hashCode() {
		int hash = 7;
		hash = 67 * hash + Objects.hashCode(this.first);
		hash = 67 * hash + Objects.hashCode(this.second);
		return hash;
	}
	/** */
	public static <T1, T2> Pair<T1,T2> makePair( T1 first, T2 second )	{
		return new Pair<>( first, second );
	}
}
	