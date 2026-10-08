package ru.inversion.dataset.parser;

import ru.inversion.utils.converter.TypeConverter;

import java.math.BigDecimal;

/**
 *
 * @author ssu @
 */
public class NumberState extends AbstractState {

	private boolean		  eof		 = false;
	private boolean		  isDecimal	 = false;
	private StringBuilder numberText = null;
	
	public NumberState() {
	
	}
	
	@Override
	protected void clear() {
		isDecimal = false;
		numberText= null;
	}
	/** */
	@Override
	protected boolean doProcess( ReaderScaner.IContext context) {
		
		if( context.current() == '.' ) {

			if ( isDecimal )
				throw new NumberFormatException("SQLParser: error digit format");
			
			isDecimal = true;

			numberText.append( context.current() );
			
			return true;
		}
		else {

			if( isDigit( context.current() ) || ( numberText == null && context.current() == '-' ) )
			{	
				if( numberText == null )
					numberText = new StringBuilder();
		
				numberText.append( context.current() );
				
				return true;
			}
		}
		
		if( isAlpha( context.current() ) )
			throw new NumberFormatException("SQLParser: error digit format");
		
		return false;
	}
	/** */
	@Override
	protected SQLItem produceItem() {

		if( isDecimal )
			return new SQLItem( new BigDecimal( numberText.toString() ), SQLItem.ItemTypeEnum.DECIMAL );
		else
		{
			if( numberText.length() <= 19  ) {

				return new SQLItem( TypeConverter.convert( numberText.toString(), Number.class ), SQLItem.ItemTypeEnum.INTEGER );
				//return new SQLItem( Long.parseLong( numberText.toString() ), SQLItem.ItemTypeEnum.INTEGER );
			}
			return new SQLItem( numberText.toString(), SQLItem.ItemTypeEnum.STRING );
		}
	}
}
