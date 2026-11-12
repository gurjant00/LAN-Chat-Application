package chat.server;

import chat.common.Message;
import chat.common.Protocol;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Standalone integration check; run after test-compile with the SQLite JDBC jar on the classpath. */
public final class ChatServerIntegrationCheck {
    private static final String PASSWORD = "password1";

    private ChatServerIntegrationCheck() {}

    public static void main(String[] args) throws Exception {
        int port = args.length > 1 ? Integer.parseInt(args[1]) : findFreePort();
        Path databasePath = Path.of("target", "integration-data", "chat.db");
        Path logPath = Path.of("target", "integration-data", "server.log");
        ChatDatabase database = new ChatDatabase(databasePath.toString());
        ServerLogger logger = new ServerLogger(logPath.toString());
        ChatServer server = new ChatServer(port, database, logger);
        Thread serverThread = new Thread(() -> {
            try { server.start(); }
            catch (Exception exception) { throw new RuntimeException(exception); }
        }, "integration-server");
        serverThread.setDaemon(true);
        serverThread.start();

        try (Socket ready = waitForServer(port)) {
            ready.close();
        }
        String aliceName = "alice1";
        String bobName = "bob123";
        String charlieName = "charlie1";
        List<String> checks = new ArrayList<>();
        TestClient alice = registerOrLogin(port, aliceName, true);
        alice.expectUsersContaining(aliceName);
        checks.add("registration and login");
        TestClient bob = registerOrLogin(port, bobName, true);
        alice.expectUsersContaining(bobName);
        bob.expectUsersContaining(aliceName);
        TestClient charlie = registerOrLogin(port, charlieName, true);
        alice.expectUsersContaining(charlieName);
        bob.expectUsersContaining(charlieName);
        charlie.expectUsersContaining(aliceName);
        checks.add("three simultaneous clients and online list");

        String runId = Long.toHexString(System.nanoTime());
        String broadcastText = "integration broadcast " + runId;
        String privateText = "integration private " + runId;
        alice.sendChat("", broadcastText);
        Message aliceBroadcast = alice.readMessage();
        Message bobBroadcast = bob.readMessage();
        Message charlieBroadcast = charlie.readMessage();
        require(aliceBroadcast.getContent().equals(broadcastText) && aliceBroadcast.isBroadcast(), "Alice receives broadcast");
        require(bobBroadcast.getContent().equals(broadcastText) && bobBroadcast.isBroadcast(), "Bob receives broadcast");
        require(charlieBroadcast.getContent().equals(broadcastText) && charlieBroadcast.isBroadcast(), "Charlie receives broadcast");
        checks.add("broadcast delivery");

        alice.sendChat(bobName, privateText);
        Message alicePrivate = alice.readMessage();
        Message bobPrivate = bob.readMessage();
        require(alicePrivate.getContent().equals(privateText) && bobPrivate.getContent().equals(privateText), "private message reaches sender and recipient");
        charlie.socket.setSoTimeout(600);
        try {
            String unexpected = charlie.input.readLine();
            throw new AssertionError("Private message leaked to a third client: " + unexpected);
        } catch (SocketTimeoutException expected) {
            // No frame should be delivered to a third client.
        }
        charlie.socket.setSoTimeout(5000);
        checks.add("private message isolation");

        require(countContent(database.recentMessages(aliceName, 100), runId) == 2, "Alice history includes sent broadcast and private message");
        require(countContent(database.recentMessages(bobName, 100), runId) == 2, "Bob history includes broadcast and received private message");
        require(countContent(database.recentMessages(charlieName, 100), runId) == 1, "Charlie history excludes private message");
        alice.close();
        alice = registerOrLogin(port, aliceName, false);
        List<Message> restoredHistory = alice.readHistoryUntilUsers();
        require(countContent(restoredHistory, runId) == 2, "history is restored on reconnect");
        checks.add("persisted, recipient-scoped history");

        try (TestClient rejected = connect(port, aliceName, "wrongpass1", false)) {
            String response = rejected.input.readLine();
            require(response != null && response.startsWith("ERROR\t"), "incorrect password is rejected");
        }
        checks.add("invalid login rejection");

        System.out.println("PASS " + String.join("; ", checks));
        System.out.println("Demo users: alice1 / password1, bob123 / password1, charlie1 / password1");
        System.out.println("Chat history and SQLite database are live at " + databasePath.toAbsolutePath());
        alice.close(); bob.close(); charlie.close();
        if (java.util.Arrays.asList(args).contains("--keep-alive")) {
            System.out.println("READY: server remains available on port " + port + " for the Swing demo.");
            new CountDownLatch(1).await();
        } else {
            server.stop();
            serverThread.join(1000);
        }
    }

    private static TestClient registerOrLogin(int port, String username, boolean register) throws Exception {
        TestClient client = connect(port, username, PASSWORD, register);
        String first = client.input.readLine();
        if (first != null && first.startsWith("ERROR\t") && register) {
            client.close();
            client = connect(port, username, PASSWORD, false);
            first = client.input.readLine();
        }
        require(first != null && first.startsWith("AUTH_OK\t"), "expected successful authentication for " + username + ", got " + first);
        return client;
    }

    private static TestClient connect(int port, String username, String password, boolean register) throws IOException {
        Socket socket = new Socket("127.0.0.1", port);
        socket.setSoTimeout(5000);
        TestClient client = new TestClient(socket);
        client.send((register ? "REGISTER" : "LOGIN") + "\t" + Protocol.encode(username) + "\t" + Protocol.encode(password));
        return client;
    }

    private static Socket waitForServer(int port) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            try { return new Socket("127.0.0.1", port); }
            catch (IOException exception) { Thread.sleep(50); }
        }
        throw new IOException("Server did not start on port " + port);
    }

    private static int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) { return socket.getLocalPort(); }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static long countContent(List<Message> messages, String token) {
        return messages.stream().filter(message -> message.getContent().contains(token)).count();
    }

    private static final class TestClient implements AutoCloseable {
        private final Socket socket;
        private final BufferedReader input;
        private final BufferedWriter output;

        private TestClient(Socket socket) throws IOException {
            this.socket = socket;
            input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            output = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        }

        private void send(String line) throws IOException {
            output.write(line);
            output.newLine();
            output.flush();
        }

        private void sendChat(String recipient, String content) throws IOException {
            send("CHAT\t" + Protocol.encode(recipient) + "\t" + Protocol.encode(content));
        }

        private String readFrame(String expectedType) throws IOException {
            String line;
            while ((line = input.readLine()) != null) {
                if (line.startsWith(expectedType + "\t") || line.equals(expectedType)) return line;
                if (line.startsWith("ERROR\t")) throw new IOException("Server returned " + Protocol.decode(line.substring(6)));
            }
            throw new IOException("Server closed connection before " + expectedType);
        }

        private Message readMessage() throws IOException {
            return Protocol.parseMessage(readFrame("MESSAGE").split("\t", -1));
        }

        private void expectUsersContaining(String username) throws IOException {
            String[] fields = readFrame("USERS").split("\t", -1);
            for (int i = 1; i < fields.length; i++) {
                if (Protocol.decode(fields[i]).equalsIgnoreCase(username)) return;
            }
            throw new AssertionError("Online list did not contain " + username);
        }

        private List<Message> readHistoryUntilUsers() throws IOException {
            List<Message> messages = new ArrayList<>();
            String line;
            while ((line = input.readLine()) != null) {
                if (line.startsWith("MESSAGE\t")) messages.add(Protocol.parseMessage(line.split("\t", -1)));
                else if (line.startsWith("USERS\t") || line.equals("USERS")) return messages;
                else if (line.startsWith("ERROR\t")) throw new IOException("Server returned " + Protocol.decode(line.substring(6)));
            }
            throw new IOException("Server closed connection before history completed");
        }

        @Override public void close() throws IOException { socket.close(); }
    }
}
