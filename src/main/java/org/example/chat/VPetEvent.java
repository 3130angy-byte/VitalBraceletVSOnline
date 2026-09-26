package org.example.chat;

public class VPetEvent {

    public enum Type {
        // 0.0.3: los eventos de crianza (hambre, suciedad, malestar, evolución, muerte...) se quitaron.
        VICTORIA, DERROTA
    }

    private final Type type;
    private final String detail;
    private final boolean important; // salta el cooldown del comentario espontáneo

    public VPetEvent(Type type, String detail, boolean important) {
        this.type = type;
        this.detail = detail;
        this.important = important;
    }

    public Type getType() { return type; }
    public String getDetail() { return detail; }
    public boolean isImportant() { return important; }
}