# LAN Chat Application

A Java 25 client-server chat application built with Swing, sockets, threads, and JDBC with SQLite.

## Requirements

- Java 25 (the current Java LTS release)
- Apache Maven 3.9 or later
- Network access to Maven Central the first time Maven downloads SQLite JDBC and build plugins

## Build and run

1. Install Java 25 and Maven, then open a terminal in this folder.
2. On the computer that will host the chat, run:

   ```powershell
   .\run-server.bat
   ```

   The server listens on port `5050`, stores its SQLite database at `data/chat.db`, and writes its log to `logs/server.log`.

3. On the server computer or another computer on the same LAN, run:

   ```powershell
   .\run-client.bat
   ```

4. Enter the server computer's LAN IPv4 address in **Server address**. Use `127.0.0.1` only when the client and server run on the same computer. Create an account with **Register**, or sign in with **Login**.
5. Select **Everyone** for a broadcast, or choose an online username for a private message. Press Enter or click **Send**.

The first Maven build downloads the SQLite JDBC driver. The server computer must allow incoming TCP connections on port 5050 for other LAN clients to connect. You can set a different port by passing it to `run-server.bat`, for example `run-server.bat 6060`, then launch a client with the matching address and port: `run-client.bat 192.168.1.25 6060`.

To run the socket and database integration check, use `.\run-integration-check.bat`. It exercises registration, login, three simultaneous clients, online list updates, broadcast and private delivery, private-message isolation, wrong-password rejection, and history after reconnect.

## Modules and syllabus mapping

| Requirement | Implementation |
| --- | --- |
| Server accepts multiple clients | `chat.server.ChatServer` accepts sockets and starts one `ServerClientConnection` thread per client. |
| Client login and chat window | `chat.client.LoginWindow` and `ChatWindow` use Swing. Login supports account creation and sign-in. |
| Broadcast and private messages | Recipient selection in the chat window; the server routes broadcast messages or private messages to an online user. |
| Chat history storage | JDBC writes and reads the `messages` table in SQLite. Each user receives broadcasts and private messages addressed to them. |
| Online users list | The server broadcasts the connected usernames after logins and disconnects. |
| `Message`, `User`, `String`, `StringBuffer`, and file logs | `chat.common.Message`, `User`, message formatting, `ServerLogger`, and `ClientLogger`. |
| `MessageHandler`, abstract `Connection`, packages | `chat.common.MessageHandler`, `chat.common.Connection`, `chat.client`, and `chat.server`. |
| Threads, `Socket`, `ServerSocket`, connection errors | `ServerClientConnection`, `ChatServer`, `ChatClientConnection`, and `ConnectionException`. |
| JDBC and database tables | `ChatDatabase` creates `users` and `messages` on server startup. Passwords are stored as salted PBKDF2 hashes. |
| Swing widgets, layouts, and events | `ChatWindow` uses `JTextArea`, `JTextField`, `JButton`, `BorderLayout`, and action listeners; Enter sends a message. |

The SQLite database and logs are created on the server computer. Message history is limited to the latest 100 messages visible to the signed-in user when they connect.

## Project layout

```text
src/main/java/chat/common/  Shared models, protocol, connection, and handler
src/main/java/chat/server/ Server, per-client threads, JDBC storage, and logs
src/main/java/chat/client/ Login screen, chat window, client connection, and logs
```

## Notes

- Usernames must be 3–20 letters, numbers, or underscores. Passwords must be at least 8 characters.
- Run one server process. Each client is a separate application process.
- This classroom project sends credentials and messages over plain TCP. Use it on a trusted LAN.
- The core Java implementation is focused on the listed project modules. The optional browser client is documented below.

## Optional browser client

The separate mobile-friendly browser edition and Vercel deployment instructions are in [web/README.md](web/README.md). It uses PostgreSQL and has its own accounts and chat history; the Java Swing app remains the syllabus-aligned LAN version.
