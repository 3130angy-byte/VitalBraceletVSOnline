package org.example.chat;

import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

/**
 * Lee la ESPECIE del Digimon desde su sprite NAME real con el modelo de
 * visión local (ministral-3:3b). Reabre la decisión "sin OCR" de CLAUDE.md
 * con evidencia nueva: probado con 9 sprites reales, leyó perfecto los
 * nombres en alfabeto latino (MagnaKidmon, Dynasmon) y falló TODOS los de
 * katakana. El usuario confirmó que sus Digimon usan letras latinas.
 *
 * Tarda 1.5-4.5 min en esta CPU, así que:
 *  - se hace UNA sola vez por sprite y se guarda en
 *    D:\DigimonProjectData\config\especies_leidas.properties (clave = hash
 *    de los píxeles del sprite, así sirve para cualquier archivo);
 *  - lo que no pasa la validación se guarda como ilegible y no se reintenta.
 * Validación: letras latinas y termina en "mon"; se corrige contra
 * KnownDigimonNames si está a 1-2 letras de un nombre conocido.
 */
public final class SpeciesNameReader {

    private static final Path CACHE_FILE = AppPaths.config().resolve("especies_leidas.properties");
    private static final String UNREADABLE = "__ILEGIBLE__";
    private static final String PROMPT = "Esta imagen contiene el nombre de un Digimon escrito en letras pixeladas. "
            + "Transcribe EXACTAMENTE el texto que ves. Responde SOLO con el nombre, sin nada más.";

    private SpeciesNameReader() {}

    public static CompletableFuture<Optional<String>> read(Image nameSprite, OllamaClient client) {
        if (nameSprite == null) return CompletableFuture.completedFuture(Optional.empty());
        String key = hash(nameSprite);
        String cached = loadCache().getProperty(key);
        if (cached != null) {
            return CompletableFuture.completedFuture(UNREADABLE.equals(cached) ? Optional.empty() : Optional.of(cached));
        }

        String png;
        try {
            png = toPngBase64(nameSprite);
        } catch (IOException e) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        System.out.println("[ESPECIE] Leyendo el sprite NAME con el modelo de visión (una sola vez)...");
        return client.readImageAsync(PROMPT, png).handle((answer, error) -> {
            if (error != null) {
                // Error de conexión: NO se guarda como ilegible, se reintenta la próxima vez.
                System.out.println("[ESPECIE] No se pudo leer: " + error.getMessage());
                return Optional.empty();
            }
            Optional<String> result = validate(answer);
            saveToCache(key, result.orElse(UNREADABLE));
            System.out.println("[ESPECIE] Modelo: \"" + answer.trim() + "\" -> " + result.orElse("(ilegible)"));
            return result;
        });
    }

    /** Solo letras latinas y termina en "mon"; corrige contra la lista de nombres conocidos. */
    static Optional<String> validate(String answer) {
        if (answer == null) return Optional.empty();
        String line = answer.strip().lines().findFirst().orElse("")
                .replaceAll("^[\"'«“`*]+|[\"'»”`*.!,;:]+$", "").strip();
        if (!line.matches("[A-Za-z][A-Za-z0-9\\-.]{1,24}")) return Optional.empty();
        if (line.equals(line.toUpperCase())) { // "DYNASMON" -> "Dynasmon"
            line = line.charAt(0) + line.substring(1).toLowerCase();
        }
        // Primero corregir, DESPUÉS exigir "mon": así "Dynasnon" -> "Dynasmon" se acepta.
        String corrected = KnownDigimonNames.correct(line);
        return corrected.toLowerCase().endsWith("mon") ? Optional.of(corrected) : Optional.empty();
    }

    // ---------------------------------------------------------------------

    /** Sprite escalado x4 sobre fondo negro (así se probó y así lee bien). */
    private static String toPngBase64(Image sprite) throws IOException {
        int scale = 4;
        int w = (int) sprite.getWidth();
        int h = (int) sprite.getHeight();
        PixelReader reader = sprite.getPixelReader();
        BufferedImage out = new BufferedImage(w * scale + 16, h * scale + 16, BufferedImage.TYPE_INT_RGB);
        var g = out.createGraphics();
        g.setColor(java.awt.Color.BLACK);
        g.fillRect(0, 0, out.getWidth(), out.getHeight());
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = reader.getArgb(x, y);
                if ((argb >>> 24) == 0) continue;
                g.setColor(new java.awt.Color(argb, true));
                g.fillRect(8 + x * scale, 8 + y * scale, scale, scale);
            }
        }
        g.dispose();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(out, "png", bytes);
        return Base64.getEncoder().encodeToString(bytes.toByteArray());
    }

    private static String hash(Image sprite) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            PixelReader reader = sprite.getPixelReader();
            int w = (int) sprite.getWidth();
            int h = (int) sprite.getHeight();
            md.update((w + "x" + h).getBytes(StandardCharsets.US_ASCII));
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int argb = reader.getArgb(x, y);
                    md.update(new byte[]{(byte) (argb >>> 24), (byte) (argb >>> 16), (byte) (argb >>> 8), (byte) argb});
                }
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static synchronized Properties loadCache() {
        Properties p = new Properties();
        if (Files.exists(CACHE_FILE)) {
            try (Reader r = Files.newBufferedReader(CACHE_FILE, StandardCharsets.UTF_8)) {
                p.load(r);
            } catch (IOException e) {
                System.out.println("[ESPECIE] No se pudo leer la caché: " + e.getMessage());
            }
        }
        return p;
    }

    private static synchronized void saveToCache(String key, String value) {
        Properties p = loadCache();
        p.setProperty(key, value);
        try (Writer w = Files.newBufferedWriter(CACHE_FILE, StandardCharsets.UTF_8)) {
            p.store(w, "Especies leídas del sprite NAME (hash de píxeles = especie). Borra una línea para volver a leerla.");
        } catch (IOException e) {
            System.out.println("[ESPECIE] No se pudo guardar la caché: " + e.getMessage());
        }
    }
}
