package chat.client;

import chat.common.ConnectionException;
import chat.common.Message;
import chat.common.Protocol;

import javax.swing.*;
import java.awt.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class ChatWindow extends JFrame {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private final String username;
    private final ChatClientConnection connection;
    private final ClientLogger logger;
    private final JTextArea transcript = new JTextArea();
    private final JTextField messageField = new JTextField();
    private final JButton sendButton = new JButton("Send");
    private final JComboBox<String> recipientBox = new JComboBox<>(new String[]{"Everyone"});
    private final DefaultListModel<String> usersModel = new DefaultListModel<>();
    private final JList<String> usersList = new JList<>(usersModel);
    private final JLabel status = new JLabel("Connected");
    private volatile boolean intentionalClose;
    private static final Color INK = new Color(35, 51, 39);
    private static final Color GREEN = new Color(49, 105, 76);
    private static final Color PAPER = new Color(246, 248, 245);

    public ChatWindow(String username, ChatClientConnection connection, ClientLogger logger) {
        super("LAN Chat - " + username);
        this.username = username;
        this.connection = connection;
        this.logger = logger;
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        getContentPane().setBackground(PAPER);
        setLayout(new BorderLayout(0, 0));
        transcript.setEditable(false);
        transcript.setLineWrap(true);
        transcript.setWrapStyleWord(true);
        transcript.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        transcript.setForeground(new Color(53, 64, 55));
        transcript.setBackground(Color.WHITE);
        transcript.setBorder(new EmptyBorder(14, 16, 14, 16));
        JScrollPane transcriptScroll = new JScrollPane(transcript);
        transcriptScroll.setPreferredSize(new Dimension(620, 430));
        transcriptScroll.setBorder(new LineBorder(new Color(226, 232, 225)));
        JPanel top = new JPanel(new BorderLayout(12, 0));
        top.setBackground(Color.WHITE);
        top.setBorder(new EmptyBorder(15, 18, 15, 18));
        JLabel title = new JLabel("LAN Chat");
        title.setFont(new Font("Segoe UI", Font.BOLD, 19));
        title.setForeground(INK);
        JLabel signedIn = new JLabel("Signed in as  " + username);
        signedIn.setForeground(new Color(112, 125, 113));
        JPanel titleBlock = new JPanel(new GridLayout(2, 1, 0, 3));
        titleBlock.setOpaque(false);
        titleBlock.add(title);
        titleBlock.add(signedIn);
        status.setForeground(GREEN);
        status.setFont(new Font("Segoe UI", Font.BOLD, 12));
        top.add(titleBlock, BorderLayout.WEST);
        top.add(status, BorderLayout.EAST);
        JPanel composer = new JPanel(new BorderLayout(8, 0));
        composer.setBackground(PAPER);
        composer.setBorder(new EmptyBorder(10, 0, 0, 0));
        styleField(messageField);
        styleButton(sendButton, true);
        recipientBox.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        recipientBox.setBackground(Color.WHITE);
        recipientBox.setPreferredSize(new Dimension(145, 38));
        composer.add(recipientBox, BorderLayout.WEST);
        composer.add(messageField, BorderLayout.CENTER);
        composer.add(sendButton, BorderLayout.EAST);
        JPanel chat = new JPanel(new BorderLayout(0, 6));
        chat.setBackground(PAPER);
        chat.setBorder(new EmptyBorder(14, 18, 16, 14));
        chat.add(transcriptScroll, BorderLayout.CENTER);
        chat.add(composer, BorderLayout.SOUTH);
        usersList.setPrototypeCellValue("long_username_example");
        usersList.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        usersList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        usersList.setBackground(new Color(250, 251, 249));
        usersList.setBorder(new EmptyBorder(8, 8, 8, 8));
        JPanel users = new JPanel(new BorderLayout());
        users.setBackground(new Color(250, 251, 249));
        users.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 1, 0, 0, new Color(226, 232, 225)),
                new EmptyBorder(14, 12, 14, 12)));
        JLabel usersTitle = new JLabel("ONLINE USERS");
        usersTitle.setFont(new Font("Segoe UI", Font.BOLD, 11));
        usersTitle.setForeground(new Color(119, 132, 120));
        usersTitle.setBorder(new EmptyBorder(2, 3, 12, 3));
        users.add(usersTitle, BorderLayout.NORTH);
        JScrollPane usersScroll = new JScrollPane(usersList);
        usersScroll.setBorder(new LineBorder(new Color(232, 236, 231)));
        users.add(usersScroll, BorderLayout.CENTER);
        users.setPreferredSize(new Dimension(190, 0));
        add(top, BorderLayout.NORTH);
        add(chat, BorderLayout.CENTER);
        add(users, BorderLayout.EAST);
        sendButton.addActionListener(event -> sendMessage());
        messageField.addActionListener(event -> sendMessage());
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent event) { disconnect(); }
        });
        setSize(new Dimension(960, 620));
        setMinimumSize(new Dimension(720, 480));
        setLocationRelativeTo(null);
    }

    private static void styleField(JTextField field) {
        field.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        field.setPreferredSize(new Dimension(100, 38));
        field.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(221, 229, 220)), new EmptyBorder(7, 10, 7, 10)));
    }

    private static void styleButton(JButton button, boolean primary) {
        button.setFont(new Font("Segoe UI", Font.BOLD, 13));
        button.setForeground(primary ? Color.WHITE : INK);
        button.setBackground(primary ? GREEN : Color.WHITE);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(primary ? GREEN : new Color(221, 229, 220)),
                new EmptyBorder(8, 16, 8, 16)));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    public void onMessage(Message message) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> onMessage(message));
            return;
        }
        String route = message.isBroadcast() ? "" : (message.getSender().equalsIgnoreCase(username)
                ? " to @" + message.getRecipient() : " (private)");
        StringBuffer line = new StringBuffer()
                .append('[').append(TIME.format(message.getSentAt())).append("] ")
                .append(message.getSender()).append(route).append(": ").append(message.getContent());
        transcript.append(line.toString() + "\n");
        transcript.setCaretPosition(transcript.getDocument().getLength());
        logger.log("Message received from " + message.getSender());
    }

    public void onUsersChanged(List<String> usernames) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> onUsersChanged(usernames));
            return;
        }
        String selected = (String) recipientBox.getSelectedItem();
        usersModel.clear();
        recipientBox.removeAllItems();
        recipientBox.addItem("Everyone");
        for (String name : usernames) {
            usersModel.addElement(name);
            if (!name.equalsIgnoreCase(username)) recipientBox.addItem(name);
        }
        if (selected != null) recipientBox.setSelectedItem(selected);
    }

    public void onError(String message) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> onError(message));
            return;
        }
        status.setForeground(new Color(174, 87, 67));
        status.setText(message);
    }

    public void onDisconnected() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::onDisconnected);
            return;
        }
        status.setText("Disconnected");
        status.setForeground(new Color(174, 87, 67));
        sendButton.setEnabled(false);
        messageField.setEnabled(false);
        recipientBox.setEnabled(false);
        if (!intentionalClose && isDisplayable()) {
            JOptionPane.showMessageDialog(this, "The connection to the chat server has ended.",
                    "Disconnected", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void sendMessage() {
        String text = messageField.getText().trim();
        if (text.isEmpty()) return;
        String selection = (String) recipientBox.getSelectedItem();
        String recipient = selection == null || selection.equals("Everyone") ? "" : selection;
        try {
            connection.sendChat(recipient, text);
            messageField.setText("");
            status.setText("Connected");
            status.setForeground(GREEN);
        } catch (ConnectionException exception) {
            status.setText(exception.getMessage());
        }
    }

    private void disconnect() {
        intentionalClose = true;
        try { connection.close(); }
        catch (IOException exception) { logger.log("Error closing connection: " + exception.getMessage()); }
        dispose();
    }
}
