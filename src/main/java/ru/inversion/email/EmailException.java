package ru.inversion.email;

import ru.inversion.utils.IExceptionInfo;

public class EmailException extends Exception implements IExceptionInfo {

    final private String content;

    public EmailException( String message ) {
        super(message);
        content = null;
    }

    public EmailException( String message, Throwable cause ) {
        super(message, cause);
        content = null;
    }

    public EmailException( String message, String content, Throwable cause ) {
        super(message, cause);
        this.content = content;
    }

    @Override
    public String getContentText() {
        return content;
    }

    @Override
    public String getCategory() {
        return "e-mail";
    }
}
