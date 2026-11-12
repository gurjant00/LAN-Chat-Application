package chat.common;

import java.util.List;

public interface MessageHandler {
    void onAuthenticated(String username);
    void onMessage(Message message);
    void onUsersChanged(List<String> usernames);
    void onError(String message);
    void onDisconnected();
}
