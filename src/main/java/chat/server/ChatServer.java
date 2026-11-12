package chat.server;

import chat.common.ConnectionException;
import chat.common.Message;
import chat.common.Protocol;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.SocketException;
import java.net.Socket;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public final class ChatServer {
    private final int port;
    private final ChatDatabase database;
    private final ServerLogger logger;
    private final ConcurrentHashMap<String, ServerClientConnection> online = new ConcurrentHashMap<>();
    private volatile ServerSocket listeningSocket;
    private volatile boolean running;
    private volatile boolean stopRequested;

    public ChatServer(int port, ChatDatabase database, ServerLogger logger) {
        this.port = port;
        this.database = database;
        this.logger = logger;
    }

    public void start() throws ConnectionException {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            listeningSocket = serverSocket;
            running = true;
            logger.log("LAN chat server listening on port " + port);
            while (running) {
                Socket socket;
                try {
                    socket = serverSocket.accept();
                } catch (SocketException exception) {
                    if (!running) break;
                    throw exception;
                }
                try {
                    ServerClientConnection connection = new ServerClientConnection(socket, this, database, logger);
                    Thread clientThread = new Thread(connection, "chat-client-" + socket.getRemoteSocketAddress());
                    clientThread.start();
                } catch (IOException exception) {
                    socket.close();
                    throw exception;
                }
            }
        } catch (IOException exception) {
            if (!stopRequested) throw new ConnectionException("Could not accept client connections on port " + port, exception);
        } finally {
            running = false;
            listeningSocket = null;
        }
    }

    public void stop() {
        stopRequested = true;
        running = false;
        ServerSocket serverSocket = listeningSocket;
        if (serverSocket != null) {
            try { serverSocket.close(); }
            catch (IOException exception) { logger.log("Error closing server socket: " + exception.getMessage()); }
        }
    }

    public boolean addOnline(String username, ServerClientConnection connection) {
        return online.putIfAbsent(key(username), connection) == null;
    }

    public void removeOnline(String username, ServerClientConnection connection) {
        online.remove(key(username), connection);
        logger.log(username + " disconnected");
        broadcastUsers();
    }

    public boolean isOnline(String username) { return online.containsKey(key(username)); }

    public void publish(Message message) throws SQLException, IOException {
        long id = database.saveMessage(message.getSender(), message.getRecipient(), message.getContent());
        Message saved = new Message(id, message.getSender(), message.getRecipient(), message.getContent(), message.getSentAt());
        String frame = Protocol.messageFrame(saved);
        if (saved.isBroadcast()) {
            for (ServerClientConnection client : online.values()) client.sendServerFrame(frame);
            logger.log("Broadcast from " + saved.getSender());
        } else {
            ServerClientConnection sender = online.get(key(saved.getSender()));
            ServerClientConnection recipient = online.get(key(saved.getRecipient()));
            if (sender != null) sender.sendServerFrame(frame);
            if (recipient != null && recipient != sender) recipient.sendServerFrame(frame);
            logger.log("Private message from " + saved.getSender() + " to " + saved.getRecipient());
        }
    }

    public synchronized void broadcastUsers() {
        List<String> names = new ArrayList<>();
        for (ServerClientConnection connection : online.values()) names.add(connection.getUsername());
        names.sort(String.CASE_INSENSITIVE_ORDER);
        StringBuilder frame = new StringBuilder("USERS");
        for (String name : names) frame.append('\t').append(Protocol.encode(name));
        for (ServerClientConnection client : online.values()) {
            try { client.sendServerFrame(frame.toString()); }
            catch (IOException exception) { logger.log("Could not update user list for " + client.getUsername()); }
        }
    }

    private static String key(String username) { return username.toLowerCase(java.util.Locale.ROOT); }
}
