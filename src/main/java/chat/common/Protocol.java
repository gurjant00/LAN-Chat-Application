package chat.common;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;

public final class Protocol {
    private Protocol() {}

    public static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    public static String decode(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    public static String messageFrame(Message message) {
        return String.join("\t", "MESSAGE", Long.toString(message.getId()), encode(message.getSender()),
                encode(message.getRecipient() == null ? "" : message.getRecipient()), encode(message.getContent()),
                message.getSentAt().toString());
    }

    public static Message parseMessage(String[] fields) {
        String recipient = decode(fields[3]);
        return new Message(Long.parseLong(fields[1]), decode(fields[2]), recipient,
                decode(fields[4]), LocalDateTime.parse(fields[5]));
    }
}
