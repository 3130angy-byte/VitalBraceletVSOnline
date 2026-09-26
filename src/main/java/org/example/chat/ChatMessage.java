package org.example.chat;

public class ChatMessage {
    private final String role; // "system", "user", "assistant"
    private final String content;
    private final long timestamp;

    public ChatMessage(String role, String content) {
        this.role = role;
        this.content = content;
        this.timestamp = System.currentTimeMillis();
    }

    public String getRole() { return role; }
    public String getContent() { return content; }
    public long getTimestamp() { return timestamp; }
}