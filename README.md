# LAN Chat Application

A client–server chat project with two editions:

- **Java LAN edition** — the coursework implementation using sockets, threads, Swing, JDBC, and SQLite.
- **Web edition** — an optional responsive browser client hosted on Vercel and backed by PostgreSQL. It can be opened from a phone or computer over the internet.

The two editions have separate accounts, databases, and chat histories; messages do not sync between them.

## Java LAN edition

### Requirements

- Windows (the provided launch scripts are `.bat` files)
- JDK 25
- Maven 3.9 or newer, either on `PATH` or at `tools/apache-maven-3.10.0/`
- Internet access on the first build so Maven can download dependencies

To check the prerequisites, download Java dependencies, and install the
optional web client's packages, run:

```powershell
py .\requirement.py
```

Use `py .\requirement.py --skip-web` for the Java edition only. The script
does not install JDK or Node.js itself; install those runtimes first and add
them to `PATH` (or set `JAVA_HOME` for Java).

### Run the server and clients

1. On the computer that will host the chat, run:

   ```powershell
   .\run-server.bat
   ```

   The server listens on TCP port `5050` by default. It creates `data/chat.db` and writes `logs/server.log` on the server computer.

2. On the server computer or another computer on the same network, run:

   ```powershell
   .\run-client.bat
   ```

3. In the client, enter the server computer’s LAN IPv4 address and port. Use `127.0.0.1` only when the server and client are on the same computer. Register an account or sign in.

4. Choose **Everyone** to broadcast, or select an online username to send a private message. Press Enter or select **Send**.

Allow inbound TCP traffic on port `5050` in the server computer’s firewall so other LAN devices can connect. To use a different port, start the server and clients with the same port, for example:

```powershell
.\run-server.bat 6060
.\run-client.bat 192.168.1.25 6060
```

The Java protocol sends credentials and messages over plain TCP, so use this edition on a trusted network.

### Integration check

Run the socket and SQLite integration scenarios with:

```powershell
.\run-integration-check.bat
```

The check covers account registration and login, multiple simultaneous clients, online-user updates, broadcast and private delivery, private-message isolation, incorrect-password rejection, and history after reconnect. Its temporary database and log are written under `target/integration-data/`.

### Coursework modules

| Module or syllabus concept | Java implementation |
| --- | --- |
| Server accepting multiple clients | `chat.server.ChatServer` accepts connections and starts a `ServerClientConnection` thread for each client. |
| Client login and chat window | `chat.client.LoginWindow` and `chat.client.ChatWindow` provide registration, login, and the Swing chat interface. |
| Broadcast and private messages | The client selects **Everyone** or an online user; the server routes messages accordingly. |
| Chat history storage | JDBC persists accounts and messages in SQLite. On sign-in, a user receives up to 100 recent broadcasts and messages they sent or received. |
| Online users list | The server updates clients when users sign in or disconnect. |
| `Message`, `User`, `String`, `StringBuffer`, file logs | Shared models are in `chat.common`; the server and client write logs through `ServerLogger` and `ClientLogger`. |
| `MessageHandler`, abstract `Connection`, packages | `chat.common.MessageHandler`, `chat.common.Connection`, and the `chat.client`, `chat.server`, and `chat.common` packages. |
| Threads, `Socket`, `ServerSocket`, connection errors | `ChatServer`, `ServerClientConnection`, `ChatClientConnection`, and `ConnectionException`. |
| JDBC and database tables | `ChatDatabase` creates the `users` and `messages` tables in SQLite when the server starts. Passwords are stored as salted PBKDF2 hashes. |
| Swing widgets, layouts, and events | `ChatWindow` uses `JTextArea`, `JTextField`, `JButton`, `BorderLayout`, and action listeners; Enter sends a message. |

### Java source layout

```text
src/main/java/chat/common/   Shared models, protocol, connection, and message handler
src/main/java/chat/server/   Socket server, client threads, SQLite persistence, and logs
src/main/java/chat/client/   Login screen, chat window, socket client, and logs
src/test/java/chat/server/   Standalone socket and database integration check
```

## Web edition

Open the deployed browser client at **[lan-chat-application-sage.vercel.app](https://lan-chat-application-sage.vercel.app)**. It is designed for phone and desktop browsers and can be used remotely; it does not require the Java server or access to the same Wi-Fi network.

The web edition uses Next.js route handlers, Neon PostgreSQL, salted password hashes, signed HTTP-only sessions, and periodic HTTP polling for chat and presence. Its schema creates the `users`, `messages`, and `presence` tables. Deployment and local-development instructions are in [web/README.md](web/README.md).

## Notes

- Usernames are 3–20 letters, numbers, or underscores. Passwords must be at least 8 characters.
- Run one Java server process; each Java client is a separate process.
- The Java edition is the syllabus-aligned LAN application. The web edition is a separate optional browser implementation.
