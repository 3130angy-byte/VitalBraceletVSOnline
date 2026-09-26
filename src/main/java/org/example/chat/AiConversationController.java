package org.example.chat;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.util.Duration;

import org.example.behavior.DigimonInstance;
import org.example.behavior.DigimonRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AiConversationController {

    private static final long SPONTANEOUS_COOLDOWN_MILLIS = 15 * 60 * 1000;
    private static final long INTERDIGIMON_COOLDOWN_MILLIS = 20 * 60 * 1000;
    private static final String SILENCE_TOKEN = "SILENCIO";

    // Tolerante a 0, 1 o 2 simbolos '#' a cada lado -- el modelo no siempre
    // cierra con '##' exacto (visto: cierre con un solo '#', o con '<>'
    // alrededor del nombre). Se captura hasta el primer separador natural.
    private static final Pattern NAME_CANDIDATE_PATTERN =
            Pattern.compile("#{0,2}NAME_CANDIDATE\\s*:\\s*<?([^#,.\\n<>]+)>?#{0,2}", Pattern.CASE_INSENSITIVE);
    private static final Pattern CONFIRM_UNIQUE_PATTERN =
            Pattern.compile("#{0,2}CONFIRM_UNIQUE#{0,2}", Pattern.CASE_INSENSITIVE);
    private static final Pattern CONFIRM_SLOT_PATTERN =
            Pattern.compile("#{0,2}CONFIRM_SLOT#{0,2}", Pattern.CASE_INSENSITIVE);
    // Red de seguridad final: cualquier resto de '##...' que sobreviva a los
    // patrones específicos se elimina igual, para que nunca se vea crudo.
    private static final Pattern LEFTOVER_TAG_PATTERN = Pattern.compile("#{2,}[^#\\n]{0,80}#{0,2}");

    private final String instanceId;
    private final ChatMemory chatMemory;
    private final OllamaClient ollamaClient;
    private final DigimonRegistry registry;

    private long lastSpontaneousAttemptMillis = 0;
    private long lastInterDigimonExchangeMillis = 0;

    private final List<Consumer<String>> bubbleListeners = new ArrayList<>();
    private final List<Consumer<String>> userChatReplyListeners = new ArrayList<>();
    private Consumer<String> onNameChanged;

    /** Tolerante a puntuación sobrante -- antes una comparación exacta dejaba pasar "SILENCIO." como mensaje real. */
    private boolean isSilence(String text) {
        if (text == null) return true;
        String normalized = text.trim().replaceAll("[.,!?¡¿\"']+$", "").trim();
        return normalized.equalsIgnoreCase(SILENCE_TOKEN);
    }

    public AiConversationController(String instanceId, ChatMemory chatMemory, OllamaClient ollamaClient, DigimonRegistry registry) {
        this.instanceId = instanceId;
        this.chatMemory = chatMemory;
        this.ollamaClient = ollamaClient;
        this.registry = registry;
    }

    public void addOnBubbleMessageListener(Consumer<String> listener) {
        bubbleListeners.add(listener);
    }

    public void addOnUserChatReplyListener(Consumer<String> listener) {
        userChatReplyListeners.add(listener);
    }

    public void setOnNameChanged(Consumer<String> callback) {
        this.onNameChanged = callback;
    }

    public ChatMemory getChatMemory() { return chatMemory; }

    public void userSays(String text) {
        chatMemory.addUserMessage(text);

        // Riesgo en ESTE mensaje: respuesta fija, sin pasar por el modelo
        // (ver EmotionalSupportPolicy.CRISIS_REPLY).
        if (EmotionalSignals.latestUserMessageIsCrisis(chatMemory.getMessages())) {
            String reply = EmotionalSupportPolicy.CRISIS_REPLY;
            runOnFxThread(() -> {
                chatMemory.addAssistantMessage(reply);
                notifyUserChatReply(reply);
                notifyBubble(reply);
            });
            return;
        }

        // Comando de asistente (hora, recordatorio, apps, música, noticias): lo
        // ejecuta el PROGRAMA y la IA solo lo cuenta con su voz (ver
        // AssistantCommands). Asíncrono: las noticias esperan a internet sin
        // bloquear la interfaz.
        List<ChatMessage> prompt = new ArrayList<>(chatMemory.buildPromptMessages());
        CompletableFuture<Optional<AssistantCommands.Result>> commandFuture =
                AssistantCommands.tryHandleAsync(text, this::scheduleReminder)
                        .map(f -> f.thenApply(Optional::of))
                        .orElseGet(() -> CompletableFuture.completedFuture(Optional.empty()));

        java.util.concurrent.atomic.AtomicReference<AssistantCommands.Result> executed = new java.util.concurrent.atomic.AtomicReference<>();
        commandFuture.thenCompose(command -> {
            command.ifPresent(r -> {
                executed.set(r);
                prompt.add(new ChatMessage("system",
                        r.factForModel() + " No inventes ningún otro dato ni digas que hiciste otra cosa."));
            });
            return ollamaClient.chatAsync(prompt, GenerationProfile.CHAT)
                    .thenAccept(reply -> runOnFxThread(() -> deliverChatReply(reply, command)));
        }).exceptionally(ex -> {
            // Probado: con prompts largos (noticias en Ultimate) la IA puede no
            // terminar a tiempo en esta CPU. El usuario no se queda sin respuesta:
            // si el comando ya se ejecutó, se confirma con su frase fija.
            System.out.println("Ollama no respondió (" + instanceId + "): " + ex.getMessage());
            AssistantCommands.Result r = executed.get();
            String fallback = r != null && r.fallbackSentence() != null
                    ? r.fallbackSentence()
                    : "Uy... me quedé pensando demasiado y se me fue la idea. ¿Me lo repites?";
            runOnFxThread(() -> {
                chatMemory.addAssistantMessage(fallback);
                notifyUserChatReply(fallback);
                notifyBubble(fallback);
            });
            return null;
        });
    }

    private void deliverChatReply(String reply, Optional<AssistantCommands.Result> command) {
        {
            String cleaned = fitToStage(processNameTags(reply));
            // Garantía: si el dato exacto (hora, fecha, cuándo avisa) no aparece,
            // se agrega la frase completa de respaldo del comando.
            String mustMention = command.map(AssistantCommands.Result::mustMention).orElse(null);
            if (mustMention != null && (cleaned == null || !cleaned.toLowerCase().contains(mustMention.toLowerCase()))) {
                cleaned = ((cleaned == null ? "" : cleaned + " ") + command.get().fallbackSentence()).trim();
            }
            if (cleaned.isEmpty()) return;
            chatMemory.addAssistantMessage(cleaned);
            maybeSummarize();
            notifyUserChatReply(cleaned);
            notifyBubble(cleaned);
        }
    }

    /**
     * Mensaje fijo e instantáneo del Digimon (botones del menú: "¡Listo! Abrí
     * la Calculadora."). No pasa por el modelo: un botón debe responder ya,
     * no en 30-80s.
     */
    public void announce(String text) {
        runOnFxThread(() -> {
            chatMemory.addAssistantMessage(text);
            notifyBubble(text);
        });
    }

    // ---------- Noticias por iniciativa propia ----------

    private long lastProactiveNewsMillis = System.currentTimeMillis();

    /**
     * Decisión del usuario: puede revisar noticias por su cuenta y comentar
     * alguna. Nunca si el usuario dio señales de malestar/crisis (no es
     * momento), respeta el intervalo de AssistantSettings y se puede apagar
     * con internet.iniciativaPropia=false.
     */
    public void maybeShareNews() {
        if (!AssistantSettings.proactiveInternet()) return;
        if (chatMemory.currentEmotionalLevel() != EmotionalSignals.Level.NONE) return;
        long now = System.currentTimeMillis();
        if (now - lastProactiveNewsMillis < AssistantSettings.proactiveNewsMinutes() * 60_000L) return;
        lastProactiveNewsMillis = now;

        NewsService.headlines(null, 8).thenAccept(titles -> {
            if (titles.isEmpty()) return;
            String headline = titles.get(new java.util.Random().nextInt(titles.size()));
            List<ChatMessage> request = new ArrayList<>(chatMemory.buildPromptMessagesForEvent());
            request.add(new ChatMessage("system",
                    "Viste en internet esta noticia REAL de hoy: \"" + headline + "\". Si crees que a tu amigo le "
                            + "puede interesar, coméntasela en una o dos frases con tu voz, sin inventar detalles que no "
                            + "estén en el titular. Si no, responde EXACTAMENTE: " + SILENCE_TOKEN));
            ollamaClient.chatAsync(request, GenerationProfile.SPONTANEOUS).thenAccept(reply -> {
                String trimmed = reply == null ? "" : reply.trim();
                if (isSilence(trimmed) || trimmed.isEmpty()) return;
                runOnFxThread(() -> {
                    String cleaned = fitToStage(processNameTags(trimmed));
                    if (cleaned == null || cleaned.isEmpty()) return;
                    chatMemory.addAssistantMessage(cleaned);
                    maybeSummarize();
                    notifyBubble(cleaned);
                });
            });
        }).exceptionally(ex -> {
            System.out.println("[NOTICIAS] Sin internet para la noticia espontánea: " + ex.getMessage());
            return null;
        });
    }

    private String processNameTags(String rawReply) {
        if (rawReply == null) return "";
        DigimonNameIdentity identity = chatMemory.getNameIdentity();
        String workingText = rawReply;
        boolean nameChanged = false;

        if (identity.hasPendingConfirmation()) {
            if (CONFIRM_UNIQUE_PATTERN.matcher(workingText).find()) {
                identity.setUniqueName(identity.getPendingNameCandidate());
                identity.clearPendingConfirmation();
                nameChanged = true;
            } else if (CONFIRM_SLOT_PATTERN.matcher(workingText).find()) {
                identity.learnSlotName(identity.getPendingNameSlot(), identity.getPendingNameCandidate());
                identity.clearPendingConfirmation();
                nameChanged = true;
            }
        } else {
            Matcher m = NAME_CANDIDATE_PATTERN.matcher(workingText);
            if (m.find()) {
                String candidate = m.group(1).trim();
                if (!candidate.isEmpty()) {
                    if (candidate.toLowerCase().endsWith("mon")) {
                        identity.learnSlotName(identity.getCurrentSlot(), candidate);
                        nameChanged = true;
                    } else {
                        identity.startPendingConfirmation(identity.getCurrentSlot(), candidate);
                    }
                }
            }
        }

        // Limpieza: se aplican todos los patrones conocidos, y al final el
        // de respaldo -- así el texto que se muestra nunca lleva restos de
        // etiquetas, sin importar cuál de las variantes usó el modelo.
        String cleaned = NAME_CANDIDATE_PATTERN.matcher(workingText).replaceAll("");
        cleaned = CONFIRM_UNIQUE_PATTERN.matcher(cleaned).replaceAll("");
        cleaned = CONFIRM_SLOT_PATTERN.matcher(cleaned).replaceAll("");
        cleaned = LEFTOVER_TAG_PATTERN.matcher(cleaned).replaceAll("");
        // Quitar una etiqueta puede dejar puntuación huérfana (visto: "Oh, , eso...").
        cleaned = cleaned.replaceAll(",\\s*,", ",").replaceAll("\\s+([,.!?])", "$1").replaceAll("\\s{2,}", " ");
        cleaned = cleaned.trim();

        if (nameChanged && onNameChanged != null) {
            onNameChanged.accept(identity.effectiveName());
        }

        return cleaned;
    }

    /**
     * Ajustes finales en código (el modelo de 3B no siempre obedece el prompt):
     * máx. 1 pregunta y tope de largo de Baby I/II. En crisis NO se recorta
     * nada y se garantiza la frase de buscar ayuda.
     */
    private String fitToStage(String cleaned) {
        cleaned = ReplyCleaner.clean(cleaned);
        if (cleaned == null || cleaned.isEmpty()) return cleaned;
        if (chatMemory.currentEmotionalLevel() == EmotionalSignals.Level.CRISIS) {
            return EmotionalSupportPolicy.ensureCrisisGuidance(cleaned);
        }
        String oneQuestion = StageVoice.limitQuestions(cleaned);
        return StageVoice.limitLength(oneQuestion, chatMemory.getDimStage());
    }

    public void onGameEvent(VPetEvent event) {
        long now = System.currentTimeMillis();
        if (!event.isImportant()) {
            if (now - lastSpontaneousAttemptMillis < SPONTANEOUS_COOLDOWN_MILLIS) {
                return;
            }
            lastSpontaneousAttemptMillis = now;
        }

        List<ChatMessage> request = new ArrayList<>(chatMemory.buildPromptMessagesForEvent());
        request.add(new ChatMessage("system",
                "Evento del juego: " + event.getType()
                        + (event.getDetail() != null ? " (" + event.getDetail() + ")" : "")
                        + ". Si quieres comentar algo natural al respecto, hazlo brevemente en español. "
                        + "Si no, responde EXACTAMENTE: " + SILENCE_TOKEN));

        ollamaClient.chatAsync(request, GenerationProfile.SPONTANEOUS).thenAccept(reply -> {
            String trimmed = reply == null ? "" : reply.trim();
            if (isSilence(trimmed) || trimmed.isEmpty()) return;

            runOnFxThread(() -> {
                String cleaned = fitToStage(processNameTags(trimmed));
                if (cleaned.isEmpty()) return;
                chatMemory.addAssistantMessage(cleaned);
                maybeSummarize();
                notifyBubble(cleaned);
            });
        }).exceptionally(ex -> {
            System.out.println("Ollama no respondió a un evento (" + instanceId + "): " + ex.getMessage());
            return null;
        });
    }

    public void maybeInitiateInterDigimonChat() {
        Optional<DigimonInstance> otherOpt = registry.getOther(instanceId);
        if (otherOpt.isEmpty() || otherOpt.get().getAiController() == null) return;
        AiConversationController other = otherOpt.get().getAiController();

        long now = System.currentTimeMillis();
        if (now - lastInterDigimonExchangeMillis < INTERDIGIMON_COOLDOWN_MILLIS) return;
        lastInterDigimonExchangeMillis = now;

        List<ChatMessage> request = new ArrayList<>(chatMemory.buildPromptMessagesForEvent());
        request.add(new ChatMessage("system",
                "Hay otro Digimon conviviendo contigo en la misma computadora. Si quieres decirle algo "
                        + "espontáneamente ahora, hazlo brevemente. Si no, responde EXACTAMENTE: " + SILENCE_TOKEN));

        ollamaClient.chatAsync(request, GenerationProfile.SPONTANEOUS).thenAccept(reply -> {
            String trimmed = reply == null ? "" : reply.trim();
            if (isSilence(trimmed) || trimmed.isEmpty()) return;

            runOnFxThread(() -> {
                String cleaned = fitToStage(processNameTags(trimmed));
                if (cleaned.isEmpty()) return;
                chatMemory.addAssistantMessage(cleaned);
                maybeSummarize();
                notifyBubble(cleaned);
                other.receiveDigimonMessage(new DigimonMessage(instanceId, "Digimon", cleaned), 1);
            });
        }).exceptionally(ex -> {
            System.out.println("Ollama no respondió (inter-Digimon, " + instanceId + "): " + ex.getMessage());
            return null;
        });
    }

    public void receiveDigimonMessage(DigimonMessage message, int turnsRemaining) {
        lastInterDigimonExchangeMillis = System.currentTimeMillis();

        List<ChatMessage> request = new ArrayList<>(chatMemory.buildPromptMessagesForEvent());
        request.add(new ChatMessage("system",
                "Otro Digimon te dijo: \"" + message.getContent() + "\". Puedes responder brevemente, "
                        + "agregar algo, o quedarte en silencio. Si no respondes, di EXACTAMENTE: " + SILENCE_TOKEN));

        ollamaClient.chatAsync(request, GenerationProfile.SPONTANEOUS).thenAccept(reply -> {
            String trimmed = reply == null ? "" : reply.trim();
            if (isSilence(trimmed) || trimmed.isEmpty()) return;

            runOnFxThread(() -> {
                String cleaned = fitToStage(processNameTags(trimmed));
                if (cleaned.isEmpty()) return;
                chatMemory.addAssistantMessage(cleaned);
                maybeSummarize();
                notifyBubble(cleaned);
                if (turnsRemaining > 0) {
                    Optional<DigimonInstance> otherOpt = registry.getOther(instanceId);
                    otherOpt.ifPresent(other -> {
                        if (other.getAiController() != null) {
                            other.getAiController().receiveDigimonMessage(
                                    new DigimonMessage(instanceId, "Digimon", cleaned), turnsRemaining - 1);
                        }
                    });
                }
            });
        }).exceptionally(ex -> {
            System.out.println("Ollama no respondió (recibiendo mensaje, " + instanceId + "): " + ex.getMessage());
            return null;
        });
    }

    /**
     * Recordatorio real: el aviso es un texto fijo (no pasa por el modelo),
     * porque en este hardware el modelo tarda 30-80s y el aviso llegaría tarde.
     */
    private void scheduleReminder(int minutes, String task) {
        runOnFxThread(() -> {
            PauseTransition wait = new PauseTransition(Duration.minutes(minutes));
            wait.setOnFinished(e -> {
                String msg = "¡Oye! Me pediste que te recordara: " + task + ".";
                chatMemory.addAssistantMessage(msg);
                notifyBubble(msg);
            });
            wait.play();
            System.out.println("[ASISTENTE] Recordatorio en " + minutes + " min: " + task);
        });
    }

    private void notifyBubble(String text) {
        for (Consumer<String> listener : bubbleListeners) listener.accept(text);
    }

    private void notifyUserChatReply(String text) {
        for (Consumer<String> listener : userChatReplyListeners) listener.accept(text);
    }

    private void maybeSummarize() {
        if (chatMemory.needsSummarization()) {
            chatMemory.summarizeOldMessages(ollamaClient);
        }
    }

    private void runOnFxThread(Runnable r) {
        if (Platform.isFxApplicationThread()) r.run();
        else Platform.runLater(r);
    }
}