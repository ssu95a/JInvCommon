package ru.inversion.meta;

import ru.inversion.utils.IExceptionInfo;
import ru.inversion.utils.Tags;

import javax.persistence.PersistenceException;

public class DMLWorkException extends PersistenceException implements IExceptionInfo {

    public DMLWorkException(String message) { super(message); }

    public DMLWorkException(String message, Throwable cause) { super(message, cause); }

    public DMLWorkException(Throwable cause) { super(cause); }

    @Override
    public String getCategory() {
        return "Dml_Work";
    }

    @Override
    public boolean isResolved() {
        return true;
    }

    public static void throwOnWorkFailure (
            String workType, String workName, String entity, String dialect, String stage,Throwable cause
    ) {
        String msg = Tags.PRODUCT_LABEL + "DML work infra error: type=" + workType + ", name='" + workName + "'" + ", entity='" + entity + "'" + ", dialect=" + dialect + ", stage=" + stage;
        throw new DMLWorkException(msg, cause);
    }
}
