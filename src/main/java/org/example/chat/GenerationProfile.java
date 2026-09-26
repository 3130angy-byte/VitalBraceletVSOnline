package org.example.chat;

/** Primer experimento de límites por tipo de llamada -- valores sin probar todavía, ajustables con datos reales. */
public enum GenerationProfile {
    CHAT(200, 0.7),
    SPONTANEOUS(50, 0.7);

    public final int maxTokens;
    public final double temperature;

    GenerationProfile(int maxTokens, double temperature) {
        this.maxTokens = maxTokens;
        this.temperature = temperature;
    }
}