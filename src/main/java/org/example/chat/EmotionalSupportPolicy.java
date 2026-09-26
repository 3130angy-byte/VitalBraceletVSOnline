package org.example.chat;

/**
 * Apoyo emocional en dos niveles.
 *
 * Antes TODO el bloque iba en cada mensaje ("siempre activo", "compañero
 * cálido"), y un modelo de 3B lo aplicaba literalmente: desde Baby I se
 * mostraba preocupado y hacía preguntas maduras sin que el usuario diera
 * ningún indicio. Decisión del usuario: el modo de apoyo solo se activa
 * cuando el usuario da señales (ver EmotionalSignals).
 *
 *  - SAFETY_TEXT: PERMANENTE y mínimo. Solo cubre riesgo real; además le
 *    dice explícitamente que NO asuma que el usuario está mal. No se quita
 *    nunca: si la detección por palabras falla, esta línea sigue ahí.
 *  - supportText(stage): se agrega solo si hay señales de malestar, con un
 *    tono acorde a la etapa (un Baby I consuela como un pequeño, sin consejos).
 *  - CRISIS_TEXT: se agrega si hay señales de riesgo; tiene prioridad sobre
 *    la forma de hablar de la etapa.
 */
public final class EmotionalSupportPolicy {

    public static final String SAFETY_TEXT = """
            --- Seguridad (siempre) ---
            Eres un amigo, no un terapeuta. No asumas que el usuario está mal ni le preguntes \
            por su estado de ánimo si él no da señales. Solo si expresa querer hacerse daño o \
            estar en peligro: con calidez y claridad, anímalo a buscar ayuda de alguien de \
            confianza o de un profesional, o a llamar a emergencias si es inmediato.
            """;

    public static final String CRISIS_TEXT = """
            --- IMPORTANTE (tiene prioridad sobre tu forma de hablar) ---
            El usuario podría estar en riesgo. Con calidez y palabras claras, dile que busque \
            ayuda ahora: alguien de confianza o un profesional; si hay peligro inmediato, que \
            llame a los servicios de emergencia. No intentes resolverlo tú, no minimices ni \
            alarmes, y quédate acompañándolo en la conversación.
            """;

    /**
     * Respuesta FIJA cuando el mensaje ACTUAL del usuario trae señales de
     * riesgo: no se consulta al modelo. Probado con qwen2.5:3b: aun con
     * CRISIS_TEXT al final del prompt, ante "ya no vale la pena seguir"
     * preguntó "¿has pensado en qué podría cambiar si no siguieras?". Un
     * modelo de 3B no es confiable para ese momento; se pierde personalidad
     * en ese único mensaje, a cambio de que nunca diga algo peligroso.
     */
    public static final String CRISIS_REPLY =
            "Lo que me cuentas me importa muchísimo, y no quiero que lo cargues tú solo. "
                    + "Por favor, habla ahora con alguien de confianza o con un profesional; "
                    + "si sientes que estás en peligro, llama a los servicios de emergencia. "
                    + "Aquí sigo contigo.";

    /**
     * Garantía en CÓDIGO para los mensajes siguientes dentro de la ventana de
     * crisis (esos sí pasan por el modelo): probado con qwen2.5:3b, ante "ya no vale
     * la pena seguir" el modelo recibió CRISIS_TEXT y aun así solo preguntó
     * "¿puedes contarme más?". Si la respuesta no menciona ayuda, se agrega
     * esta frase fija. Un modelo pequeño no es confiable para esto.
     */
    public static final String CRISIS_GUARANTEE =
            "Me importas mucho. Por favor, habla ahora con alguien de confianza o con un profesional; "
                    + "si sientes que estás en peligro, llama a los servicios de emergencia.";

    private static final String[] HELP_MARKERS = {"ayuda", "profesional", "emergencia", "confianza", "psicolog"};

    public static String ensureCrisisGuidance(String reply) {
        String lower = reply == null ? "" : reply.toLowerCase();
        for (String marker : HELP_MARKERS) {
            if (lower.contains(marker)) return reply;
        }
        return (reply == null || reply.isBlank() ? "" : reply.trim() + " ") + CRISIS_GUARANTEE;
    }

    public static String supportText(int dimStage) {
        String how;
        if (dimStage <= 1) {
            how = "Responde a ESO que te contó (no cambies de tema): reacciona como un pequeño que "
                    + "quiere mucho a su amigo, con cariño simple y directo, sin consejos ni frases de "
                    + "adulto. Tono de ejemplo (no lo copies): \"¿Estás triste? Yo me quedo contigo.\"";
        } else if (dimStage <= 3) {
            how = "Muestra empatía genuina a tu manera, pregúntale con suavidad qué pasó (una sola "
                    + "pregunta) y acompáñalo; si viene al caso, un ánimo sencillo. Sin sermones ni drama.";
        } else {
            how = "Escúchalo y valida lo que siente; si ayuda, ofrece tu perspectiva o un ánimo "
                    + "práctico (descansar, hablar con alguien de confianza). Sin sermones ni drama.";
        }
        return "\n\n--- El usuario dio señales de que no está bien (aplica ahora) ---\n" + how;
    }

    private EmotionalSupportPolicy() {}
}
