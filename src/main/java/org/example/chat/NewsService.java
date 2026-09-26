package org.example.chat;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Titulares REALES desde Google Noticias por RSS: gratis, sin cuenta ni
 * clave, en español según el país (AssistantSettings). La IA solo cuenta
 * estos titulares con su voz; nunca debe inventar noticias.
 *
 * Caché de 30 min por país+tema: evita pedir lo mismo una y otra vez.
 */
public final class NewsService {

    private static final Duration CACHE_TTL = Duration.ofMinutes(30);
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .sslContext(SystemTrust.sslContext()) // Java + almacén de Windows (Avast), ver SystemTrust
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /** país -> {hl, gl, ceid} de Google Noticias. */
    private static final Map<String, String[]> EDITIONS = Map.of(
            "PE", new String[]{"es-419", "PE", "PE:es-419"},
            "MX", new String[]{"es-419", "MX", "MX:es-419"},
            "CO", new String[]{"es-419", "CO", "CO:es-419"},
            "AR", new String[]{"es-419", "AR", "AR:es-419"},
            "CL", new String[]{"es-419", "CL", "CL:es-419"},
            "EC", new String[]{"es-419", "EC", "EC:es-419"},
            "VE", new String[]{"es-419", "VE", "VE:es-419"},
            "ES", new String[]{"es", "ES", "ES:es"},
            "US", new String[]{"es-419", "US", "US:es-419"});

    /** Países disponibles para la pantalla de Ajustes (código -> nombre), en orden. */
    public static final Map<String, String> COUNTRY_NAMES = new java.util.LinkedHashMap<>();
    static {
        COUNTRY_NAMES.put("PE", "Perú");
        COUNTRY_NAMES.put("MX", "México");
        COUNTRY_NAMES.put("CO", "Colombia");
        COUNTRY_NAMES.put("AR", "Argentina");
        COUNTRY_NAMES.put("CL", "Chile");
        COUNTRY_NAMES.put("EC", "Ecuador");
        COUNTRY_NAMES.put("VE", "Venezuela");
        COUNTRY_NAMES.put("ES", "España");
        COUNTRY_NAMES.put("US", "Estados Unidos");
    }

    private record Cached(long atMillis, List<String> titles) {}
    private static final Map<String, Cached> CACHE = new ConcurrentHashMap<>();

    private NewsService() {}

    /** topic null = titulares generales. Falla con excepción si no hay internet. */
    public static CompletableFuture<List<String>> headlines(String topic, int max) {
        String country = AssistantSettings.newsCountry();
        String[] ed = EDITIONS.getOrDefault(country, EDITIONS.get("PE"));
        String key = country + "|" + (topic == null ? "" : topic.toLowerCase());

        Cached cached = CACHE.get(key);
        if (cached != null && System.currentTimeMillis() - cached.atMillis() < CACHE_TTL.toMillis()) {
            return CompletableFuture.completedFuture(cached.titles().subList(0, Math.min(max, cached.titles().size())));
        }

        String params = "hl=" + ed[0] + "&gl=" + ed[1] + "&ceid=" + URLEncoder.encode(ed[2], StandardCharsets.UTF_8);
        String url = topic == null || topic.isBlank()
                ? "https://news.google.com/rss?" + params
                : "https://news.google.com/rss/search?q=" + URLEncoder.encode(topic, StandardCharsets.UTF_8) + "&" + params;

        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15)).GET().build();
        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).thenApply(resp -> {
            if (resp.statusCode() != 200) throw new IllegalStateException("Google Noticias respondió " + resp.statusCode());
            List<String> titles = parseTitles(resp.body());
            CACHE.put(key, new Cached(System.currentTimeMillis(), titles));
            System.out.println("[NOTICIAS] " + titles.size() + " titulares (" + key + ")");
            return titles.subList(0, Math.min(max, titles.size()));
        });
    }

    private static List<String> parseTitles(byte[] xml) {
        List<String> titles = new ArrayList<>();
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); // sin XXE
            Document doc = f.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
            NodeList items = doc.getElementsByTagName("item");
            for (int i = 0; i < items.getLength(); i++) {
                NodeList t = ((org.w3c.dom.Element) items.item(i)).getElementsByTagName("title");
                if (t.getLength() > 0) titles.add(t.item(0).getTextContent().trim());
            }
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo leer el RSS: " + e.getMessage(), e);
        }
        return titles;
    }
}
