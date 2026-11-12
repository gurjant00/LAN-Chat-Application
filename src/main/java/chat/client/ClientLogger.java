package chat.client;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;

public final class ClientLogger {
    private final File file;

    public ClientLogger(String path) throws IOException {
        file = new File(path);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IOException("Cannot create log directory: " + parent);
        if (!file.exists() && !file.createNewFile()) throw new IOException("Cannot create log file: " + file);
    }

    public synchronized void log(String event) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(file, true))) {
            writer.println(LocalDateTime.now() + " " + event);
        } catch (IOException exception) {
            System.err.println("Could not write client log: " + exception.getMessage());
        }
    }
}
