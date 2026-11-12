package chat.client;

import javax.swing.*;

public final class ClientMain {
    private ClientMain() {}

    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : "127.0.0.1";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 5050;
        String logPath = args.length > 2 ? args[2] : "logs/client.log";
        SwingUtilities.invokeLater(() -> {
            try {
                ClientLogger logger = new ClientLogger(logPath);
                new LoginWindow(logger, host, port).setVisible(true);
            } catch (java.io.IOException exception) {
                JOptionPane.showMessageDialog(null, "Cannot create client log: " + exception.getMessage(),
                        "Startup error", JOptionPane.ERROR_MESSAGE);
            }
        });
    }
}
