package ru.inversion.db;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.inversion.db.work.DBWork;
import ru.inversion.utils.Triplet;

/**
 *
 * @author ssu @
 */
@Deprecated
public class StoredProc implements DBWork {
	
	final private static Logger log = LoggerFactory.getLogger( StoredProc.class );
    
    final private String name;

    final private List< Triplet<Object,Integer,Boolean> > parameters = new ArrayList<>();

    /** */
	public StoredProc( String name ) {
		this.name = name;
		parameters.add( Triplet.makeTriplet(null, null, null) );
	}
    
	/** */
	public String getName( ) {
		return name;
	}
    
 	/** */
	public void addParameter( Object value ) {
		parameters.add( Triplet.makeTriplet( value, null, Boolean.TRUE ) );
	}
    
 	/** */
	public void setParameter( int index, Object value ) {

		if( index >= parameters.size() ) {
			for( int i = parameters.size(); i < index; i++ )
				addParameter( null );
			addParameter( value );
		}
		else
			parameters.set( index, Triplet.makeTriplet( value, null, Boolean.TRUE ) );
	}
    
	/** */
	public void registerReturnParameter( int type ) {
		parameters.set( 0, Triplet.makeTriplet( null, type, null ) );
	}
    
	/** */
	public Object getReturnParameter( ) {
		return parameters.get(0).first;
	}
    
	/** */
	public int registerOutParameter( int type ) {
		parameters.add( Triplet.makeTriplet(null, type, Boolean.FALSE) );
		return parameters.size() - 1;
	}
    
	/** */
	public Object getOutParameter( int index ) {
		return parameters.get(index).first;
	}   
    
    /** */
    @Override
	public void execute( Connection connection ) throws SQLException {

		try {

			StringBuilder sb = new StringBuilder();

			for( Triplet<Object,Integer,Boolean> t : parameters ) {
				if( t.third == null ) {
					  if( t.second == null )	
						sb.append("{ call ").append(name).append('(');
					  else		
						sb.append("{?= call ").append(name).append('(');
					 continue;
				}
				if( sb.charAt(sb.length()-1) == '(' )
					sb.append("?");
				else
					sb.append(",?");
			}
			sb.append(") }");
			
			//cs = c.prepareCall( sb.toString() );
            try( CallableStatement cs = connection.prepareCall( sb.toString() ) ) {
			
                log.debug("run stored proc: " + sb.toString() );
			
                int i = 1;
			
                for( Triplet< Object, Integer, Boolean > t : parameters ) {

                    if( t.third == null ) {
                        if( t.second != null ) {
                            log.debug("   - return type: " + t.second );
                            cs.registerOutParameter(i++, t.second );
                        }
                        continue;
                    }
                    if( t.third ) {
                        if( t.first != null || 1 == 1 ) {
                            log.debug("   - param value: " + t.first );
                            cs.setObject(i++, t.first);
                        }
                        else {
                            log.debug("   - null param, type: " + t.second );
                            cs.setNull(i++, t.second);
                        }
                    }
                    else {
                        log.debug("   - out param, type: " + t.second );
                        cs.registerOutParameter(i++, t.second);
                    }
                }

                log.debug("   EXECUTE ... " );
                cs.execute( );
                log.debug("OK" );

    			i = 1;

                for( Triplet< Object, Integer, Boolean > t : parameters ) {

                    if( t.third == null ) {
                        if( t.second != null ) {
                            t.first = cs.getObject(i++);
                            log.debug("   - return value: " + t.first );
                        }
                        continue;
                    }

                    if( !t.third ) {
                        t.first = cs.getObject(i++);
                        log.debug("   - out param value: " + t.first );
                    }else{
                                        i++;
                                    }
                }
            }
        }
		catch( SQLException e ) {
			log.error( "error in call SP:" + name, e );
			throw e;	
		}
	}

    @Override
    public void close() throws Exception {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
}
