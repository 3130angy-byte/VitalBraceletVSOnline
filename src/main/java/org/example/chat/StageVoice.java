package org.example.chat;

/**
 * Cómo habla el Digimon según su etapa, y cómo intenta conocer al usuario.
 *
 * Va al FINAL del prompt de sistema a propósito: un modelo de 3B le hace
 * más caso a lo último que lee. Antes la madurez era una sola línea
 * mezclada con temperamento y atributo, y se perdía -- un Baby I sonaba
 * como adulto. Aquí son reglas concretas (largo de frase, qué NO hace,
 * qué tipo de preguntas hace), no adjetivos.
 *
 * Base de la relación (decisión del usuario): el Digimon es un AMIGO que
 * está conociéndote, con una madurez que crece con la etapa.
 */
public final class StageVoice {

    private StageVoice() {}

    public static String section(int dimStage) {
        return "\n\n--- Cómo hablas ahora (sigue esto siempre) ---\n"
                + voice(dimStage) + "\n"
                + "Amistad: eres su amigo y estás conociéndolo, no un asistente (nunca digas que estás "
                + "\"para ayudar\"). Haz como mucho UNA pregunta por mensaje, no lo interrogues, y a veces "
                + "cuenta algo tuyo en vez de preguntar. Retoma solo lo que de verdad te contó en esta "
                + "conversación o en tu resumen; NUNCA inventes gustos o recuerdos suyos. Si habla de algo "
                + "cotidiano, respóndele en ese mismo tono.";
    }

    private static String voice(int dimStage) {
        return switch (dimStage) {
            case 0 -> "Eres un bebé recién nacido. Frases de 2 a 6 palabras, máximo 2 frases, palabras "
                    + "muy simples; todo te sorprende. Siempre dices algo con sentido sobre lo que te dijo, "
                    + "nunca respondes solo con un sonido. NO das consejos, NO reflexionas, NO preguntas por "
                    + "sus sentimientos ni sus problemas por tu cuenta. Para conocerlo preguntas cosas simples "
                    + "y concretas: su color favorito, qué come, qué es algo.";
            case 1 -> "Eres muy pequeño. Frases cortas y sencillas, máximo 2 frases. Empiezas a decir qué te "
                    + "gusta y qué no. Sin consejos ni reflexiones. Preguntas simples sobre sus gustos o qué "
                    + "hizo hoy (\"¿jugaste hoy?\").";
            case 2 -> "Hablas como un niño con personalidad propia: natural, curioso y juguetón, opinas y a "
                    + "veces no estás de acuerdo. Te interesan sus gustos, sus hobbies y su día, y lo comparas "
                    + "con lo tuyo. Todavía no das consejos de adulto.";
            case 3 -> "Hablas como un amigo joven: más vocabulario, compartes opiniones y lo que te pasa, "
                    + "reflexionas un poco. Te interesa qué le entusiasma, qué planes tiene, qué le gusta hacer.";
            case 4 -> "Hablas como un amigo maduro: entiendes matices, humor más fino, das tu punto de vista "
                    + "con criterio cuando viene al caso. Te interesa conocerlo a fondo: sus metas, qué valora, "
                    + "sus historias.";
            default -> "Tienes gran madurez y calma: hablas con criterio y cercanía, sin palabras rebuscadas. "
                    + "Ya lo conoces bien y te sigue interesando conocerlo; recuerdas lo que te contó y lo retomas.";
        };
    }

    /**
     * Tope duro de frases por etapa: el modelo de 3B no siempre respeta el
     * largo pedido en el prompt (ministral-3:3b en Ultimate llegó a 8 frases
     * y divagaba). Baby I/II: 2, Child/Adult: 3, Perfect/Ultimate: 4. Corta en
     * el límite de una frase, nunca a la mitad.
     */
    public static int maxSentences(int dimStage) {
        if (dimStage <= 1) return 2;
        if (dimStage <= 3) return 3;
        return 4;
    }

    public static String limitLength(String text, int dimStage) {
        if (text == null) return text;
        int maxSentences = maxSentences(dimStage);
        int count = 0;
        int sentenceStart = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '.' || c == '!' || c == '?' || c == '…') {
                // agrupa "!!", "?!", "..." como un solo final de frase
                while (i + 1 < text.length() && ".!?…".indexOf(text.charAt(i + 1)) >= 0) i++;
                // Una interjección suelta ("¡oh!") no cuenta como frase: si contara,
                // "¡oh! ¡Qué rico!" gastaría el cupo en el sonido.
                String sentence = text.substring(sentenceStart, i + 1).trim();
                sentenceStart = i + 1;
                if (sentence.split("\\s+").length < 2) continue;
                count++;
                if (count == maxSentences) return text.substring(0, i + 1).trim();
            }
        }
        return text;
    }

    /**
     * Máximo UNA pregunta por mensaje (pauta de amistad): el modelo de 3B
     * suele encadenar dos o tres. Si después de la primera "?" viene otra, se
     * corta justo tras la primera. Aplica a todas las etapas.
     */
    public static String limitQuestions(String text) {
        if (text == null) return null;
        int first = text.indexOf('?');
        if (first < 0) return text;
        int end = first;
        while (end + 1 < text.length() && text.charAt(end + 1) == '?') end++;
        if (text.indexOf('?', end + 1) < 0) return text;
        return text.substring(0, end + 1).trim();
    }
}
