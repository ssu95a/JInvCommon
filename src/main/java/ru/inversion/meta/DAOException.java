package ru.inversion.meta;

import ru.inversion.utils.IExceptionInfo;
import ru.inversion.utils.Tags;

import javax.persistence.LockTimeoutException;
import javax.persistence.PersistenceException;

/**
 *
 * @author ssu
 */
public class DAOException extends PersistenceException implements IExceptionInfo {
    /** */
    final private String operation;
    /** */
    public DAOException( ) {
        operation = null;
    }
    /** */
    public DAOException( String message ) {
        super(message);
        operation = null;
    }
    /** */
    public DAOException( Throwable cause ) {
        super(cause);
        operation = null;
    }
    /** */
    public DAOException( String message, Throwable cause ) {
        super(message, cause);
        operation = null;
    }
    /** */
    public DAOException( String operation, String message, Throwable cause ) {
        super(message, cause);
        this.operation = operation;
    }
    /** */
    public String getOperation() {
        return operation;
    }

    /** */
    @Override
    public String getCategory() {
        return "Entity DAO";
    }

    /** */
    public static void throwOnOperation( String operation, Throwable th ) {
        throw new DAOException( operation, Tags.PRODUCT_LABEL + "DAO error when executing the operation '" + operation + "' ", th );
    }

    /** */
    public static void throwOnLoadInstance( String operation, Class entityClass, Throwable th ) {
        throw new DAOException( operation, Tags.PRODUCT_LABEL + "DAO error when loading data class '" + entityClass.getName() + "', procedure '" + operation + "'", th );
    }

    /** */
    public static void throwLockTimeoutException( Throwable initialException, Object entity, String lockTable ) {
        throw new LockTimeoutException( Tags.PRODUCT_LABEL + "Error on lock entity", initialException, new Object[] { lockTable, entity } );
    }
}
