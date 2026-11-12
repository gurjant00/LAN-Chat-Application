package chat.server;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;

public final class ServerLogger {
    private final File file;

    public ServerLogger(String path) throws IOException {
        file = new File(path);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Cannot create log directory: " + parent);
        }
        if (!file.exists() && !file.createNewFile()) throw new IOException("Cannot create log file: " + file);
    }

    public synchronized void log(String event) {
        String line = LocalDateTime.now() + " " + event;
        System.out.println(line);
        try (PrintWriter writer = new PrintWriter(new FileWriter(file, true))) {
            writer.println(line);
        } catch (IOException exception) {
            System.err.println("Could not write server log: " + exception.getMessage());
        }
    }
}
