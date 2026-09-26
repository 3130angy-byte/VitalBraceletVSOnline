package org.example.chat;

public class DigimonMessage {
    private final String fromInstanceId;
    private final String fromDisplayName;
    private final String content;

    public DigimonMessage(String fromInstanceId, String fromDisplayName, String content) {
        this.fromInstanceId = fromInstanceId;
        this.fromDisplayName = fromDisplayName;
        this.content = content;
    }

    public String getFromInstanceId() { return fromInstanceId; }
    public String getFromDisplayName() { return fromDisplayName; }
    public String getContent() { return content; }
}