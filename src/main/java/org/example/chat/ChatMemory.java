package org.example.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ChatMemory {

    private static final int MAX_MESSAGES_BEFORE_SUMMARY = 20;
    private static final String[] NAME_KEYWORDS = {"nombre", "llama", "llamo", "llamas", "llamarte", "apodo"};

    private final String instanceId;
    private String contextBase = "Eres un Digimon. Todavía no se ha definido tu identidad.";
    private String profileText = "";
    private String runningSummary;
    private final List<ChatMessage> messages = new ArrayList<>();
    private final DigimonNameIdentity nameIdentity = new DigimonNameIdentity();
    private int dimStage = 0;

    public ChatMemory(String instanceId) {
        this.instanceId = instanceId;
    }

    public String getInstanceId() { return instanceId; }

    /** 1. CONTEXTO BASE -- identidad general, reglas generales, apoyo emocional. Cambia poco. */
    public void setContextBase(String contextBase) { this.contextBase = contextBase; }

    /** 2. PERFIL -- Activity Type/Attribute, nombre actual, historial de nombres. Cambia por slot. */
    public void setProfileText(String profileText) { this.profileText = profileText; }

    /** Etapa DIM cruda (0=Baby I ... 5=Ultimate): decide la forma de hablar (StageVoice). */
    public void setDimStage(int dimStage) { this.dimStage = dimStage; }
    public int getDimStage() { return dimStage; }

    /** Señales de malestar/riesgo en los mensajes recientes del usuario. */
    public EmotionalSignals.Level currentEmotionalLevel() { return EmotionalSignals.detect(messages); }

    public DigimonNameIdentity getNameIdentity() { return nameIdentity; }

    public void addUserMessage(String text) { messages.add(new ChatMessage("user", text)); }
    public void addAssistantMessage(String text) { messages.add(new ChatMessage("assistant", text)); }
    public List<ChatMessage> getMessages() { return messages; }

    /** Chat directo: permite que el escaneo de palabras clave detecte si ESTE mensaje habla de nombres. */
    public List<ChatMessage> buildPromptMessages() {
        return buildPromptMessagesInternal(true);
    }

    /** Eventos/espontáneo/entre Digimons: sin escaneo de palabras clave (no hay mensaje del usuario que escanear). */
    public List<ChatMessage> buildPromptMessagesForEvent() {
        return buildPromptMessagesInternal(false);
    }

    private List<ChatMessage> buildPromptMessagesInternal(boolean allowKeywordScan) {
        List<ChatMessage> prompt = new ArrayList<>();
        prompt.add(new ChatMessage("system", buildSystemPrompt(allowKeywordScan)));
        int start = Math.max(0, messages.size() - MAX_MESSAGES_BEFORE_SUMMARY);
        for (int i = start; i < messages.size(); i++) {
            prompt.add(messages.get(i));
        }
        return prompt;
    }

    private String buildSystemPrompt(boolean allowKeywordScan) {
        StringBuilder sb = new StringBuilder();
        sb.append(contextBase); // 1. CONTEXTO BASE

        if (!profileText.isEmpty()) {
            sb.append("\n\n--- Perfil ---\n").append(profileText); // 2. PERFIL
        }

        sb.append(buildMemorySection()); // 3. MEMORIA
        sb.append(buildDynamicSection(allowKeywordScan)); // 4. CONTEXTO DINÁMICO
        // Apoyo condicional (decisión del usuario): solo si sus mensajes recientes
        // muestran malestar o riesgo. También aplica a eventos/espontáneos, para
        // no cambiar de tono de golpe si acaba de contar que tuvo un mal día.
        EmotionalSignals.Level level = EmotionalSignals.detect(messages);
        if (level == EmotionalSignals.Level.CRISIS) {
            // En crisis la instrucción de pedir ayuda va AL FINAL, después de la
            // voz: probado, si la voz de la etapa iba última el modelo la ignoraba.
            sb.append(StageVoice.section(dimStage));
            sb.append("\n\n").append(EmotionalSupportPolicy.CRISIS_TEXT);
        } else {
            sb.append(StageVoice.section(dimStage)); // 5. VOZ DE LA ETAPA -- casi al final, a propósito
            if (level == EmotionalSignals.Level.DISTRESS) {
                // 6. APOYO -- solo con señales, y DESPUÉS de la voz: probado, con la
                // voz última un Baby I respondió a "tuve un día horrible" con
                // "¿te gusta el azul?".
                sb.append(EmotionalSupportPolicy.supportText(dimStage));
            }
        }

        return sb.toString();
    }

    private String buildMemorySection() {
        StringBuilder sb = new StringBuilder();
        if (runningSummary != null) {
            sb.append("\n\nResumen de la conversación hasta ahora: ").append(runningSummary);
        }
        return sb.toString();
    }

    /**
     * 4. CONTEXTO DINÁMICO -- solo se agrega cuando realmente aplica, con
     * disparadores baratos y determinísticos (nunca otra llamada a la IA
     * solo para decidir si incluirlo):
     *  - hay una confirmación de nombre pendiente;
     *  - el Digimon acaba de llegar a un slot sin nombre conocido (una sola vez);
     *  - el mensaje actual del usuario menciona el tema del nombre (palabras clave).
     */
    private String buildDynamicSection(boolean allowKeywordScan) {
        boolean includeNameSection = false;

        if (nameIdentity.hasPendingConfirmation()) {
            includeNameSection = true;
        } else if (nameIdentity.isJustReachedUnnamedSlot()) {
            includeNameSection = true;
            nameIdentity.clearJustReachedUnnamedSlot();
        } else if (allowKeywordScan && lastUserMessageMentionsNameTopic()) {
            includeNameSection = true;
        }

        if (!includeNameSection) return "";

        StringBuilder sb = new StringBuilder("\n\n--- Sobre tu nombre (aplica ahora) ---\n");

        if (nameIdentity.hasPendingConfirmation()) {
            sb.append("El usuario propuso el nombre \"").append(nameIdentity.getPendingNameCandidate())
                    .append("\", pero no termina en \"mon\", así que le preguntaste si será tu nombre para toda tu "
                            + "vida o solo para tu forma/etapa actual. Interpreta su respuesta en ESTE mensaje: si "
                            + "indica que es permanente/para siempre/toda la vida, incluye la etiqueta exacta "
                            + "##CONFIRM_UNIQUE## en tu respuesta. Si indica que es solo para esta forma/etapa, "
                            + "incluye la etiqueta exacta ##CONFIRM_SLOT##. Si su respuesta no es clara al respecto, "
                            + "no incluyas ninguna etiqueta.\n");
        } else {
            sb.append("Si en ESTE mensaje el usuario menciona un nombre dirigido a ti -- ya sea porque te llama así, "
                    + "te dice que así te llamas, o usa una palabra como si fuera tu nombre -- trátalo como un "
                    + "candidato, sin importar si termina en \"mon\" o no, usando la etiqueta ##NAME_CANDIDATE:<nombre>## "
                    + "ya indicada. Por ejemplo, si el usuario dice \"te llamas Cupimon\", tu respuesta DEBE incluir "
                    + "##NAME_CANDIDATE:Cupimon## en alguna parte del texto, junto con tu respuesta normal -- esto es "
                    + "obligatorio cuando el usuario te da un nombre, no opcional.\n");
        }

        return sb.toString();
    }

    private boolean lastUserMessageMentionsNameTopic() {
        if (messages.isEmpty()) return false;
        ChatMessage last = messages.get(messages.size() - 1);
        if (!"user".equals(last.getRole()) || last.getContent() == null) return false;
        String lower = last.getContent().toLowerCase();
        for (String kw : NAME_KEYWORDS) {
            if (lower.contains(kw)) return true;
        }
        return false;
    }

    public boolean needsSummarization() {
        return messages.size() > MAX_MESSAGES_BEFORE_SUMMARY;
    }

    public CompletableFuture<Void> summarizeOldMessages(OllamaClient client) {
        int keepFrom = messages.size() - MAX_MESSAGES_BEFORE_SUMMARY;
        List<ChatMessage> toSummarize = new ArrayList<>(messages.subList(0, keepFrom));

        List<ChatMessage> request = new ArrayList<>();
        request.add(new ChatMessage("system",
                "Resume la siguiente conversación en 2-3 frases breves, en español, conservando solo lo importante."));
        request.addAll(toSummarize);

        return client.chatAsync(request, GenerationProfile.CHAT).thenAccept(summary -> {
            runningSummary = (runningSummary == null ? "" : runningSummary + " ") + summary;
            messages.subList(0, keepFrom).clear();
        });
    }
}