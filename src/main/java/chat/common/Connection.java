package chat.common;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/** Shared UTF-8 line transport used by both sides of the client-server connection. */
public abstract class Connection implements Runnable, AutoCloseable {
    protected final Socket socket;
    protected final BufferedReader input;
    protected final BufferedWriter output;

    protected Connection(Socket socket) throws IOException {
        this.socket = socket;
        this.input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.output = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
    }

    protected synchronized void sendLine(String line) throws IOException {
        output.write(line);
        output.newLine();
        output.flush();
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
