package chat.server;

import chat.common.Connection;
import chat.common.ConnectionException;
import chat.common.Message;
import chat.common.Protocol;

import java.io.IOException;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

final class ServerClientConnection extends Connection {
    private final ChatServer server;
    private final ChatDatabase database;
    private final ServerLogger logger;
    private volatile String username;

    ServerClientConnection(Socket socket, ChatServer server, ChatDatabase database, ServerLogger logger) throws IOException {
        super(socket);
        this.server = server;
        this.database = database;
        this.logger = logger;
    }

    String getUsername() { return username; }

    @Override
    public void run() {
        try {
            if (!authenticate()) return;
            String line;
            while ((line = input.readLine()) != null) handleFrame(line);
        } catch (IOException exception) {
            if (username != null) logger.log("Connection ended for " + username + ": " + exception.getMessage());
        } finally {
            if (username != null) server.removeOnline(username, this);
            try { close(); } catch (IOException ignored) { }
        }
    }

    private boolean authenticate() throws IOException {
        String first = input.readLine();
        if (first == null) return false;
        String[] fields = first.split("\t", -1);
        if (fields.length != 3 || !(fields[0].equals("LOGIN") || fields[0].equals("REGISTER"))) {
            sendError("Invalid login request.");
            return false;
        }
        String requestedName;
        char[] password;
        try {
            requestedName = Protocol.decode(fields[1]).trim();
            password = Protocol.decode(fields[2]).toCharArray();
        } catch (IllegalArgumentException exception) {
            sendError("Invalid login request.");
            return false;
        }
        if (!requestedName.matches("[A-Za-z0-9_]{3,20}")) {
            java.util.Arrays.fill(password, '\0');
            sendError("Username must be 3–20 letters, numbers, or underscores.");
            return false;
        }
        if (password.length < 8) {
            java.util.Arrays.fill(password, '\0');
            sendError("Password must contain at least 8 characters.");
            return false;
        }
        try {
            if (fields[0].equals("REGISTER")) {
                database.register(requestedName, password);
            } else if (!database.authenticate(requestedName, password)) {
                sendError("Incorrect username or password.");
                return false;
            }
        } catch (SQLException exception) {
            sendError(fields[0].equals("REGISTER") ? "That username is already registered." : "Could not access the user database.");
            return false;
        } catch (GeneralSecurityException exception) {
            sendError("Password verification is unavailable.");
            return false;
        } finally {
            java.util.Arrays.fill(password, '\0');
        }

        username = requestedName;
        if (!server.addOnline(requestedName, this)) {
            username = null;
            sendError("That account is already connected.");
            return false;
        }
        logger.log(username + " connected from " + socket.getRemoteSocketAddress());
        sendLine("AUTH_OK\t" + Protocol.encode(username));
        try {
            List<Message> history = database.recentMessages(username, 100);
            for (Message message : history) sendLine(Protocol.messageFrame(message));
        } catch (SQLException exception) {
            sendError("Could not load chat history.");
        }
        server.broadcastUsers();
        return true;
    }

    private void handleFrame(String line) throws IOException {
        String[] fields = line.split("\t", -1);
        if (fields.length != 3 || !fields[0].equals("CHAT")) {
            sendError("Unrecognized chat message.");
            return;
        }
        try {
            String recipient = Protocol.decode(fields[1]);
            String content = Protocol.decode(fields[2]).trim();
            if (content.isEmpty()) return;
            if (content.length() > 2000) {
                sendError("Messages can contain up to 2000 characters.");
                return;
            }
            if (!recipient.isEmpty() && !server.isOnline(recipient)) {
                sendError("That user is no longer online.");
                return;
            }
            server.publish(new Message(0, username, recipient, content, LocalDateTime.now()));
        } catch (IllegalArgumentException exception) {
            sendError("Invalid message format.");
        } catch (SQLException exception) {
            sendError("Could not save the message.");
            logger.log("Database error while saving a message: " + exception.getMessage());
        }
    }

    void sendServerFrame(String frame) throws IOException { sendLine(frame); }

    private void sendError(String message) throws IOException { sendLine("ERROR\t" + Protocol.encode(message)); }
}
