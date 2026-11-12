package chat.common;

import java.time.LocalDateTime;

public final class Message {
    private final long id;
    private final String sender;
    private final String recipient;
    private final String content;
    private final LocalDateTime sentAt;

    public Message(long id, String sender, String recipient, String content, LocalDateTime sentAt) {
        this.id = id;
        this.sender = sender;
        this.recipient = recipient;
        this.content = content;
        this.sentAt = sentAt;
    }

    public long getId() { return id; }
    public String getSender() { return sender; }
    public String getRecipient() { return recipient; }
    public String getContent() { return content; }
    public LocalDateTime getSentAt() { return sentAt; }
    public boolean isBroadcast() { return recipient == null || recipient.isBlank(); }
}
