package chat.common;

import java.io.IOException;

public class ConnectionException extends IOException {
    public ConnectionException(String message, Throwable cause) { super(message, cause); }
    public ConnectionException(String message) { super(message); }
}
