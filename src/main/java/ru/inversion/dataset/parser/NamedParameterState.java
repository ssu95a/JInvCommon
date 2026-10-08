package ru.inversion.dataset.parser;

/**
 *
 * @author ssu @
 */
public class NamedParameterState extends AbstractState {

	final static private StringBuilder castOper = new StringBuilder("::");

	private StringBuilder parameterName = null;
	
	public NamedParameterState( ) {
	}
	
	@Override
	protected void clear( ) {
		parameterName = null;
	}

	@Override
	protected boolean doProcess(ReaderScaner.IContext context) {

		if( parameterName == null && context.current() == ':' )
		{
			//PL/pgSQL cast expr - ::
			if( context.current() == ':' && context.previous() == ':' )
				parameterName = castOper;
		}
		else
		{
			if( parameterName == castOper )
				return false;

			if( !isAlphaDigit( context.current() ) && context.current() != '.' )
				 return false;
			
			if( parameterName == null ) 
				parameterName = new StringBuilder();
			
			parameterName.append( context.current() );
		}
		return true;
	}
	/** */
	@Override
	protected SQLItem produceItem() {
		if( parameterName == castOper )
			return new SQLItem( "::", SQLItem.ItemTypeEnum.TEXT_EXPRESSION );
		return new SQLItem( parameterName.toString(), SQLItem.ItemTypeEnum.NAMED_PARAMETER );
	}
}
