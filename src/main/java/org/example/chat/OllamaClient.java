package org.example.chat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class OllamaClient {

    // Primer experimento -- NO son valores probados como óptimos todavía.
    // 2048: con 1024 el prompt del Digimon (~700 tokens) + titulares de noticias
    // + la respuesta (hasta 200) no cabían y Ollama recortaba el contexto.
    private static final int NUM_CTX = 2048;
    private static final int NUM_THREAD = 4;
    private static final double TEMPERATURE_DEFAULT = 0.7;
    private static final String KEEP_ALIVE = "30m";

    // Generoso a propósito: si la carga en frío real ronda los 5 min como
    // reportaste, un timeout de 120s cortaría la petición antes de que
    // termine. Se ajusta hacia abajo una vez que sepamos, con datos
    // reales, cuánto tarda de verdad cada fase.
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(300);

    private final String baseUrl;
    private final String model;
    private final HttpClient httpClient;
    private final ExecutorService executor;

    public OllamaClient(String baseUrl, String model) {
        this.baseUrl = baseUrl;
        this.model = model;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        // Un solo hilo A PROPÓSITO: toda petición (chat directo, eventos
        // espontáneos, entre Digimons, resúmenes, de los dos Digimon si
        // hay dos activos) pasa por esta misma cola, una a la vez -- nunca
        // se disparan varias contra el mismo modelo en paralelo.
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "ollama-client");
            t.setDaemon(true);
            return t;
        });
    }

    public CompletableFuture<String> chatAsync(List<ChatMessage> messages, GenerationProfile profile) {
        CompletableFuture<String> future = new CompletableFuture<>();
        long submittedAtNanos = System.nanoTime();
        executor.submit(() -> {
            try {
                future.complete(chatSync(messages, profile, submittedAtNanos));
            } catch (Exception ex) {
                future.completeExceptionally(ex);
            }
        });
        return future;
    }

    private String chatSync(List<ChatMessage> messages, GenerationProfile profile, long submittedAtNanos)
            throws IOException, InterruptedException {

        JSONArray messagesJson = new JSONArray();
        int promptChars = 0;
        for (ChatMessage m : messages) {
            JSONObject obj = new JSONObject();
            obj.put("role", m.getRole());
            obj.put("content", m.getContent());
            messagesJson.put(obj);
            promptChars += m.getContent() != null ? m.getContent().length() : 0;
        }

        JSONObject options = new JSONObject();
        options.put("num_ctx", NUM_CTX);
        options.put("num_thread", NUM_THREAD);
        options.put("temperature", profile != null ? profile.temperature : TEMPERATURE_DEFAULT);
        options.put("num_predict", profile != null ? profile.maxTokens : 200);

        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("messages", messagesJson);
        body.put("stream", false);
        body.put("keep_alive", KEEP_ALIVE);
        body.put("options", options);
        if (hasThinkingMode(model)) {
            // qwen3/qwen3.5 "piensan" antes de responder por defecto: en una CPU
            // sin AVX2 eso multiplica el tiempo, y para chat no aporta.
            body.put("think", false);
        }

        long promptTokenEstimate = Math.round(promptChars / 8.2); // calibrado con tu medición real (514 tok / 4212 chars)
        System.out.println("[CHAT START] model=" + model + " profile=" + profile
                + " promptChars=" + promptChars + " promptTokenEstimate=" + promptTokenEstimate
                + " promptMessages=" + messages.size());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/chat"))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException te) {
            System.out.println("[CHAT TIMEOUT] model=" + model + " sin respuesta tras " + REQUEST_TIMEOUT.getSeconds() + "s.");
            throw te;
        }

        if (response.statusCode() != 200) {
            System.out.println("[CHAT ERROR] status=" + response.statusCode() + " body=" + response.body());
            throw new IOException("Ollama respondió " + response.statusCode() + ": " + response.body());
        }

        JSONObject responseJson = new JSONObject(response.body());
        logMetrics(responseJson);

        long totalJavaMillis = (System.nanoTime() - submittedAtNanos) / 1_000_000;
        System.out.println("[CHAT END] totalJavaTime=" + totalJavaMillis + "ms");

        return responseJson.getJSONObject("message").getString("content");
    }

    /**
     * Pregunta sobre una imagen (PNG en base64) al mismo modelo, que también
     * tiene visión (ministral-3:3b). Misma cola de un solo hilo que el chat:
     * nunca corre en paralelo con otra petición. Temperatura 0: se pide una
     * transcripción, no creatividad.
     */
    public CompletableFuture<String> readImageAsync(String prompt, String pngBase64) {
        CompletableFuture<String> future = new CompletableFuture<>();
        executor.submit(() -> {
            try {
                JSONObject message = new JSONObject().put("role", "user").put("content", prompt)
                        .put("images", new JSONArray().put(pngBase64));
                JSONObject options = new JSONObject().put("num_ctx", NUM_CTX).put("num_thread", NUM_THREAD)
                        .put("temperature", 0).put("num_predict", 24);
                JSONObject body = new JSONObject().put("model", model).put("messages", new JSONArray().put(message))
                        .put("stream", false).put("keep_alive", KEEP_ALIVE).put("options", options);
                if (hasThinkingMode(model)) body.put("think", false);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/api/chat"))
                        .timeout(REQUEST_TIMEOUT)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    throw new IOException("Ollama respondió " + response.statusCode() + ": " + response.body());
                }
                JSONObject json = new JSONObject(response.body());
                logMetrics(json);
                future.complete(json.getJSONObject("message").getString("content"));
            } catch (Exception ex) {
                future.completeExceptionally(ex);
            }
        });
        return future;
    }

    /** Modelos con modo "pensar" que se puede apagar con think:false (los demás rechazan el campo). */
    public static boolean hasThinkingMode(String model) {
        return model != null && model.startsWith("qwen3");
    }

    private void logMetrics(JSONObject responseJson) {
        StringBuilder sb = new StringBuilder("[OLLAMA RESPONSE] ");
        appendNanoAsMs(sb, responseJson, "total_duration");
        appendNanoAsMs(sb, responseJson, "load_duration");
        appendNanoAsMs(sb, responseJson, "prompt_eval_duration");
        appendNanoAsMs(sb, responseJson, "eval_duration");
        appendCount(sb, responseJson, "prompt_eval_count");
        appendCount(sb, responseJson, "eval_count");
        System.out.println(sb.toString());
    }

    private void appendNanoAsMs(StringBuilder sb, JSONObject json, String key) {
        if (json.has(key) && !json.isNull(key)) {
            long nanos = json.getLong(key);
            sb.append(key).append("=").append(nanos / 1_000_000).append("ms ");
        } else {
            sb.append(key).append("=N/A ");
        }
    }

    private void appendCount(StringBuilder sb, JSONObject json, String key) {
        if (json.has(key) && !json.isNull(key)) {
            sb.append(key).append("=").append(json.getInt(key)).append(" ");
        } else {
            sb.append(key).append("=N/A ");
        }
    }

    public void warmUp() {
        List<ChatMessage> warmupMessages = List.of(new ChatMessage("system", "Responde solo con: OK"));
        System.out.println("[WARMUP] iniciando precalentamiento del modelo...");
        chatAsync(warmupMessages, GenerationProfile.SPONTANEOUS)
                .thenAccept(reply -> System.out.println("[WARMUP] completado."))
                .exceptionally(ex -> {
                    System.out.println("[WARMUP] falló (¿Ollama no está corriendo todavía?): " + ex.getMessage());
                    return null;
                });
    }

    public boolean isAvailable() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/tags"))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception ex) {
            return false;
        }
    }
}