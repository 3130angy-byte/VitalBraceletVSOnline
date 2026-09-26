package org.example.chat;

import java.util.regex.Pattern;

/**
 * Limpia el "formato de chatbot" que ministral-3:3b mete en sus respuestas
 * (visto en la comparación de modelos): emojis, **negritas**, acciones de
 * rol entre *asteriscos*, notas "(Nota: ...)" y saltos de línea. Un Digimon
 * en una burbuja de diálogo no debe verse así. No toca el contenido.
 */
public final class ReplyCleaner {

    private static final Pattern BOLD = Pattern.compile("\\*\\*(.+?)\\*\\*|__(.+?)__");
    // *Señalo con mi pincel* -> se quita entero (acción de rol, no diálogo).
    private static final Pattern ROLEPLAY_ACTION = Pattern.compile("\\*[^*\\n]{1,160}\\*");
    private static final Pattern NOTE_ASIDE = Pattern.compile("\\(\\s*(?:nota|note)\\b[^)]*\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern HEADING = Pattern.compile("(?m)^\\s*#+\\s*");
    // Separadores "---" y viñetas "- ", "• ", "1. " al inicio de línea (visto en
    // un Ultimate contando noticias: "--- Es una pregunta... - Patentes y números:").
    private static final Pattern RULE = Pattern.compile("(?m)^\\s*(?:-{3,}|\\*{3,}|_{3,})\\s*");
    private static final Pattern BULLET = Pattern.compile("(?m)^\\s*(?:[-•*]|\\d{1,2}[.)])\\s+");
    // Emojis y símbolos pictográficos. No toca ¡ ¿ ni letras con tilde.
    private static final Pattern EMOJI = Pattern.compile(
            "[\\x{1F000}-\\x{1FAFF}\\x{2600}-\\x{27BF}\\x{2B00}-\\x{2BFF}\\x{FE0F}\\x{200D}\\x{20E3}]");

    private ReplyCleaner() {}

    public static String clean(String text) {
        if (text == null) return null;
        String t = RULE.matcher(text).replaceAll("");
        t = BULLET.matcher(t).replaceAll("");
        t = BOLD.matcher(t).replaceAll(m -> m.group(1) != null ? m.group(1) : m.group(2));
        t = ROLEPLAY_ACTION.matcher(t).replaceAll("");
        t = NOTE_ASIDE.matcher(t).replaceAll("");
        t = HEADING.matcher(t).replaceAll("");
        t = EMOJI.matcher(t).replaceAll("");
        t = t.replaceAll("[\\r\\n]+", " ")
                .replaceAll("\\(\\s*\\)", "")
                // quitar una acción entre dos puntos deja ".." (los "..." se respetan)
                .replaceAll("(?<!\\.)\\.\\s*\\.(?!\\.)", ".")
                // "¿Qué haces hoy?." -> "¿Qué haces hoy?" (visto con ministral-3:3b)
                .replaceAll("([!?])\\.(?!\\.)", "$1")
                .replaceAll("\\s+([,.!?])", "$1")
                .replaceAll("\\s{2,}", " ");
        return t.trim();
    }
}
