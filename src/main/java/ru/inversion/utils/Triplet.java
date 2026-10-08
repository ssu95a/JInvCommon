package ru.inversion.utils;

public class Triplet<T1, T2, T3> extends Pair<T1, T2> {

	public static final Triplet<?,?,?> NullTriplet = new Triplet<>( null, null, null );

	public T3 third;

	public Triplet()
	{ }
    
	/** */
	public Triplet( T1 first, T2 second, T3 third )
	{
		super( first, second );
		this.third = third;
	}

	public void setThird( T3 third ) {
		this.third = third;
	}

	public T3 getThird() {
		return third;
	}

	/** */
	public String toString()
	{
		return "triplet: 1) = " + first + ", 2) = " + second + ", 3) = " + third;
	}
    
	/** */
	public Triplet<T1, T2, T3> assign( Triplet<T1, T2, T3> triplet )
	{
		super.assign( triplet );
		third = triplet.third;
		return this;
	}

	/** */
	public Object[] toArray( )
	{
		return new Object[]{ getFirst(), getSecond(), getThird() };
	}

	/** */
	public static <T1, T2, T3> Triplet<T1, T2, T3> makeTriplet( T1 first, T2 second, T3 third )
	{
		return new Triplet<T1, T2, T3>( first, second, third );
	}
}
