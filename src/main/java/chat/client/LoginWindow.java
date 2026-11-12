package chat.client;

import chat.common.ConnectionException;
import chat.common.Message;
import chat.common.MessageHandler;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.List;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;

public final class LoginWindow extends JFrame implements MessageHandler {
    private final JTextField hostField = new JTextField("127.0.0.1", 16);
    private final JTextField portField = new JTextField("5050", 6);
    private final JTextField usernameField = new JTextField(18);
    private final JPasswordField passwordField = new JPasswordField(18);
    private final JButton loginButton = new JButton("Login");
    private final JButton registerButton = new JButton("Register");
    private final JLabel statusLabel = new JLabel(" ");
    private final ClientLogger logger;
    private ChatClientConnection connection;
    private ChatWindow chatWindow;
    private static final Color INK = new Color(35, 51, 39);
    private static final Color GREEN = new Color(49, 105, 76);
    private static final Color PAPER = new Color(246, 248, 245);

    public LoginWindow(ClientLogger logger, String defaultHost, int defaultPort) {
        super("LAN Chat - Login");
        this.logger = logger;
        hostField.setText(defaultHost);
        portField.setText(Integer.toString(defaultPort));
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        getContentPane().setBackground(PAPER);
        setLayout(new BorderLayout(0, 0));
        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(Color.WHITE);
        form.setBorder(new EmptyBorder(18, 22, 18, 22));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(5, 6, 5, 6);
        c.anchor = GridBagConstraints.WEST;
        addRow(form, c, 0, "Server address", hostField);
        addRow(form, c, 1, "Port", portField);
        addRow(form, c, 2, "Username", usernameField);
        addRow(form, c, 3, "Password", passwordField);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setBackground(PAPER);
        styleButton(registerButton, false);
        styleButton(loginButton, true);
        buttons.add(registerButton);
        buttons.add(loginButton);
        JPanel south = new JPanel(new BorderLayout(10, 0));
        south.setBackground(PAPER);
        south.setBorder(new EmptyBorder(10, 18, 16, 18));
        statusLabel.setForeground(new Color(112, 125, 113));
        south.add(statusLabel, BorderLayout.CENTER);
        south.add(buttons, BorderLayout.EAST);
        JPanel heading = new JPanel(new BorderLayout(0, 5));
        heading.setBackground(Color.WHITE);
        heading.setBorder(new EmptyBorder(20, 24, 12, 24));
        JLabel title = new JLabel("LAN Chat");
        title.setFont(new Font("Segoe UI", Font.BOLD, 24));
        title.setForeground(INK);
        JLabel subtitle = new JLabel("Connect to your chat server and join the conversation.");
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        subtitle.setForeground(new Color(112, 125, 113));
        heading.add(title, BorderLayout.NORTH);
        heading.add(subtitle, BorderLayout.CENTER);
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(Color.WHITE);
        card.setBorder(new LineBorder(new Color(226, 232, 225)));
        card.add(heading, BorderLayout.NORTH);
        card.add(form, BorderLayout.CENTER);
        add(card, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);
        setBorderStyle(hostField);
        setBorderStyle(portField);
        setBorderStyle(usernameField);
        setBorderStyle(passwordField);
        loginButton.addActionListener(event -> connect(false));
        registerButton.addActionListener(event -> connect(true));
        passwordField.addActionListener(event -> connect(false));
        pack();
        getContentPane().setBackground(PAPER);
        ((JComponent) getContentPane()).setBorder(new EmptyBorder(18, 18, 0, 18));
        setMinimumSize(getSize());
        setLocationRelativeTo(null);
    }

    private static void setBorderStyle(JTextField field) {
        field.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        field.setPreferredSize(new Dimension(field.getPreferredSize().width, 34));
        field.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(221, 229, 220)), new EmptyBorder(6, 9, 6, 9)));
    }

    private static void styleButton(JButton button, boolean primary) {
        button.setFont(new Font("Segoe UI", Font.BOLD, 12));
        button.setForeground(primary ? Color.WHITE : INK);
        button.setBackground(primary ? GREEN : Color.WHITE);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(primary ? GREEN : new Color(221, 229, 220)),
                new EmptyBorder(7, 13, 7, 13)));
    }

    private static void addRow(JPanel panel, GridBagConstraints c, int row, String label, JComponent field) {
        c.gridx = 0; c.gridy = row; c.weightx = 0; c.fill = GridBagConstraints.NONE;
        panel.add(new JLabel(label), c);
        c.gridx = 1; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(field, c);
    }

    private void connect(boolean register) {
        String username = usernameField.getText().trim();
        char[] password = passwordField.getPassword();
        if (username.isEmpty() || password.length == 0) {
            statusLabel.setText("Enter a username and password.");
            java.util.Arrays.fill(password, '\0');
            return;
        }
        int port;
        try { port = Integer.parseInt(portField.getText().trim()); }
        catch (NumberFormatException exception) {
            statusLabel.setText("Port must be a number.");
            java.util.Arrays.fill(password, '\0');
            return;
        }
        loginButton.setEnabled(false);
        registerButton.setEnabled(false);
        statusLabel.setText(register ? "Creating account…" : "Connecting…");
        try {
            connection = ChatClientConnection.connect(hostField.getText().trim(), port, username, password,
                    register, this, logger);
        } catch (ConnectionException exception) {
            setBusy(false);
            statusLabel.setText(exception.getMessage());
        }
    }

    private void setBusy(boolean busy) {
        loginButton.setEnabled(!busy);
        registerButton.setEnabled(!busy);
    }

    @Override public void onAuthenticated(String username) {
        SwingUtilities.invokeLater(() -> {
            chatWindow = new ChatWindow(username, connection, logger);
            chatWindow.setVisible(true);
            dispose();
        });
    }
    @Override public void onMessage(Message message) {
        SwingUtilities.invokeLater(() -> { if (chatWindow != null) chatWindow.onMessage(message); });
    }
    @Override public void onUsersChanged(List<String> usernames) {
        SwingUtilities.invokeLater(() -> { if (chatWindow != null) chatWindow.onUsersChanged(usernames); });
    }
    @Override public void onError(String message) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText(message);
            if (chatWindow != null) chatWindow.onError(message);
        });
    }
    @Override public void onDisconnected() {
        SwingUtilities.invokeLater(() -> {
            if (chatWindow != null) chatWindow.onDisconnected();
            else setBusy(false);
        });
    }
}
