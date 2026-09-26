package org.example.chat;

import java.text.Normalizer;
import java.util.List;

/**
 * Detecta, sin otra llamada a la IA (mismo criterio que las palabras clave
 * de nombre en ChatMemory), si el usuario dio señales de malestar o de
 * riesgo en sus mensajes RECIENTES. La ventana hace que el modo de apoyo no
 * se apague a mitad de la conversación, pero tampoco se quede pegado para
 * siempre.
 *
 * Las listas son deliberadamente amplias: un falso positivo solo hace que
 * el Digimon esté más atento un par de mensajes; un falso negativo en
 * riesgo lo sigue cubriendo EmotionalSupportPolicy.SAFETY_TEXT.
 */
public final class EmotionalSignals {

    public enum Level { NONE, DISTRESS, CRISIS }

    /** Cuántos mensajes del usuario hacia atrás se revisan. */
    private static final int USER_MESSAGE_WINDOW = 3;

    // Sin tildes y en minúscula: el texto se normaliza igual antes de comparar.
    private static final String[] CRISIS = {
            "suicid", "matarme", "quitarme la vida", "hacerme dano", "lastimarme", "cortarme",
            "no quiero vivir", "no quiero seguir", "quiero morir", "morirme", "quiero desaparecer",
            "ya no vale la pena", "no tiene sentido vivir", "sin salida"
    };

    private static final String[] DISTRESS = {
            "triste", "mal dia", "dia horrible", "dia pesimo", "pesimo dia", "horrible dia",
            "me siento mal", "estoy mal", "no estoy bien", "me va mal", "todo mal",
            "cansad", "agotad", "estresad", "estres", "agobiad", "ansiedad", "ansios",
            "deprimid", "depre", "bajon", "desanimad", "sin ganas", "harto", "harta",
            "frustrad", "enojad", "me molesta", "preocupad", "fatal", "no puedo mas",
            "me siento sol", "llor", "decepcion", "me duele", "extrano a", "lo extrano", "la extrano",
            "me dejo", "terminamos", "me despidieron", "reprobe", "murio", "fallecio"
    };

    private EmotionalSignals() {}

    public static Level detect(List<ChatMessage> messages) {
        Level result = Level.NONE;
        int seen = 0;
        for (int i = messages.size() - 1; i >= 0 && seen < USER_MESSAGE_WINDOW; i--) {
            ChatMessage m = messages.get(i);
            if (!"user".equals(m.getRole()) || m.getContent() == null) continue;
            seen++;
            String text = normalize(m.getContent());
            if (containsAny(text, CRISIS)) return Level.CRISIS;
            if (containsAny(text, DISTRESS)) result = Level.DISTRESS;
        }
        return result;
    }

    /** true si el ÚLTIMO mensaje del usuario (no la ventana) trae señales de riesgo. */
    public static boolean latestUserMessageIsCrisis(List<ChatMessage> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage m = messages.get(i);
            if (!"user".equals(m.getRole()) || m.getContent() == null) continue;
            return containsAny(normalize(m.getContent()), CRISIS);
        }
        return false;
    }

    private static boolean containsAny(String text, String[] needles) {
        for (String n : needles) {
            if (text.contains(n)) return true;
        }
        return false;
    }

    private static String normalize(String s) {
        String noAccents = Normalizer.normalize(s.toLowerCase(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return noAccents.replaceAll("\\s+", " ");
    }
}
