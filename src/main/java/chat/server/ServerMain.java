package chat.server;

import chat.common.ConnectionException;

import java.sql.SQLException;

public final class ServerMain {
    private ServerMain() {}

    public static void main(String[] args) {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 5050;
        String databasePath = args.length > 1 ? args[1] : "data/chat.db";
        String logPath = args.length > 2 ? args[2] : "logs/server.log";
        try {
            ChatDatabase database = new ChatDatabase(databasePath);
            ServerLogger logger = new ServerLogger(logPath);
            ChatServer server = new ChatServer(port, database, logger);
            Runtime.getRuntime().addShutdownHook(new Thread(server::stop, "chat-server-shutdown"));
            server.start();
        } catch (SQLException exception) {
            System.err.println("Could not initialize SQLite: " + exception.getMessage());
        } catch (ConnectionException exception) {
            System.err.println("Server connection error: " + exception.getMessage());
        } catch (java.io.IOException | NumberFormatException exception) {
            System.err.println("Could not start server: " + exception.getMessage());
        }
    }
}
