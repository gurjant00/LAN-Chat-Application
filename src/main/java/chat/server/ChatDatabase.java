package chat.server;

import chat.common.Message;
import chat.common.PasswordHasher;

import java.security.GeneralSecurityException;
import java.io.File;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public final class ChatDatabase {
    private final String url;

    public ChatDatabase(String fileName) throws SQLException {
        this.url = "jdbc:sqlite:" + fileName;
        File databaseFile = new File(fileName);
        File parent = databaseFile.getAbsoluteFile().getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new SQLException("Cannot create database directory: " + parent);
        }
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS users (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        username TEXT NOT NULL COLLATE NOCASE UNIQUE,
                        password_salt TEXT NOT NULL,
                        password_hash TEXT NOT NULL,
                        created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS messages (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        sender_id INTEGER NOT NULL REFERENCES users(id),
                        recipient_username TEXT,
                        content TEXT NOT NULL,
                        sent_at TEXT NOT NULL
                    )
                    """);
        }
    }

    private java.sql.Connection connect() throws SQLException {
        java.sql.Connection connection = DriverManager.getConnection(url);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
        }
        return connection;
    }

    public synchronized void register(String username, char[] password) throws SQLException, GeneralSecurityException {
        String salt = PasswordHasher.newSalt();
        String hash = PasswordHasher.hash(password, salt);
        try (java.sql.Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO users(username, password_salt, password_hash) VALUES(?, ?, ?)")) {
            statement.setString(1, username);
            statement.setString(2, salt);
            statement.setString(3, hash);
            statement.executeUpdate();
        }
    }

    public synchronized boolean authenticate(String username, char[] password) throws SQLException, GeneralSecurityException {
        try (java.sql.Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(
                "SELECT password_salt, password_hash FROM users WHERE username = ?")) {
            statement.setString(1, username);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return false;
                String actual = PasswordHasher.hash(password, result.getString("password_salt"));
                return java.security.MessageDigest.isEqual(actual.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        result.getString("password_hash").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
        }
    }

    public synchronized long saveMessage(String sender, String recipient, String content) throws SQLException {
        try (java.sql.Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO messages(sender_id, recipient_username, content, sent_at) " +
                        "SELECT id, ?, ?, ? FROM users WHERE username = ?", Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, recipient == null || recipient.isBlank() ? null : recipient);
            statement.setString(2, content);
            statement.setString(3, LocalDateTime.now().toString());
            statement.setString(4, sender);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) return keys.getLong(1);
            }
        }
        throw new SQLException("Could not save message: sender account was not found");
    }

    public synchronized List<Message> recentMessages(String username, int limit) throws SQLException {
        List<Message> messages = new ArrayList<>();
        String query = """
                SELECT m.id, u.username AS sender, m.recipient_username, m.content, m.sent_at
                FROM messages m JOIN users u ON u.id = m.sender_id
                WHERE m.recipient_username IS NULL OR m.recipient_username = ? COLLATE NOCASE OR u.username = ?
                ORDER BY m.id DESC LIMIT ?
                """;
        try (java.sql.Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, username);
            statement.setString(2, username);
            statement.setInt(3, limit);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    messages.add(new Message(result.getLong("id"), result.getString("sender"),
                            result.getString("recipient_username"), result.getString("content"),
                            LocalDateTime.parse(result.getString("sent_at"))));
                }
            }
        }
        java.util.Collections.reverse(messages);
        return messages;
    }
}
