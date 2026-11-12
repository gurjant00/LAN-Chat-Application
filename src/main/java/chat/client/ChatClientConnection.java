package chat.client;

import chat.common.Connection;
import chat.common.ConnectionException;
import chat.common.Message;
import chat.common.MessageHandler;
import chat.common.Protocol;

import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public final class ChatClientConnection extends Connection {
    private final MessageHandler handler;
    private final ClientLogger logger;
    private volatile boolean authenticated;

    private ChatClientConnection(Socket socket, MessageHandler handler, ClientLogger logger) throws IOException {
        super(socket);
        this.handler = handler;
        this.logger = logger;
    }

    public static ChatClientConnection connect(String host, int port, String username, char[] password,
                                                boolean register, MessageHandler handler, ClientLogger logger)
            throws ConnectionException {
        try {
            Socket socket = new Socket(host, port);
            ChatClientConnection connection = new ChatClientConnection(socket, handler, logger);
            String command = register ? "REGISTER" : "LOGIN";
            connection.sendLine(command + "\t" + Protocol.encode(username) + "\t" + Protocol.encode(new String(password)));
            Thread reader = new Thread(connection, "chat-server-reader");
            reader.setDaemon(true);
            reader.start();
            return connection;
        } catch (IOException exception) {
            throw new ConnectionException("Cannot connect to " + host + ":" + port, exception);
        } finally {
            java.util.Arrays.fill(password, '\0');
        }
    }

    public void sendChat(String recipient, String content) throws ConnectionException {
        if (!authenticated) throw new ConnectionException("Login is not complete.");
        try {
            sendLine("CHAT\t" + Protocol.encode(recipient == null ? "" : recipient) + "\t" + Protocol.encode(content));
        } catch (IOException exception) {
            throw new ConnectionException("Could not send message.", exception);
        }
    }

    @Override
    public void run() {
        try {
            String line;
            while ((line = input.readLine()) != null) handleFrame(line);
        } catch (IOException exception) {
            if (authenticated) logger.log("Chat connection ended: " + exception.getMessage());
        } finally {
            try { close(); } catch (IOException ignored) { }
            handler.onDisconnected();
        }
    }

    private void handleFrame(String line) {
        String[] fields = line.split("\t", -1);
        try {
            switch (fields[0]) {
                case "AUTH_OK" -> {
                    if (fields.length != 2) throw new IllegalArgumentException("Bad authentication response");
                    authenticated = true;
                    String username = Protocol.decode(fields[1]);
                    logger.log("Connected as " + username);
                    handler.onAuthenticated(username);
                }
                case "MESSAGE" -> handler.onMessage(Protocol.parseMessage(fields));
                case "USERS" -> {
                    List<String> usernames = new ArrayList<>();
                    for (int i = 1; i < fields.length; i++) usernames.add(Protocol.decode(fields[i]));
                    handler.onUsersChanged(usernames);
                }
                case "ERROR" -> handler.onError(fields.length > 1 ? Protocol.decode(fields[1]) : "Server error.");
                default -> handler.onError("Received an unknown response from the server.");
            }
        } catch (IllegalArgumentException exception) {
            handler.onError("Received an invalid response from the server.");
        }
    }
}
