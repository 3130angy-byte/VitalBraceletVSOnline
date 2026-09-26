package org.example.chat;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Música usando el reproductor del propio usuario (decisión del usuario):
 *  - elegir: el PROGRAMA escoge un archivo de su carpeta de música (al azar o
 *    por nombre) y lo abre con el reproductor predeterminado, que lo reproduce;
 *  - controlar: teclas multimedia de Windows (sirven con casi cualquier
 *    reproductor, incluido Spotify o YouTube en el navegador);
 *  - buscar: abre la búsqueda en YouTube o Spotify. Límite real: Spotify no
 *    deja reproducir una canción concreta sin su API de pago + cuenta, así
 *    que ahí el usuario pulsa play.
 */
public final class MusicControl {

    private static final List<String> EXTENSIONS = List.of(".mp3", ".m4a", ".wav", ".flac", ".ogg", ".wma", ".aac");
    private static final Random RANDOM = new Random();

    // Códigos de tecla virtual de Windows para multimedia (SendKeys los acepta como carácter).
    private static final int VK_MEDIA_NEXT = 176;
    private static final int VK_MEDIA_PREV = 177;
    private static final int VK_MEDIA_PLAY_PAUSE = 179;

    private MusicControl() {}

    /** Nombre de la canción abierta, o vacío si no hay música en la carpeta. query null = al azar. */
    public static Optional<String> playFromFolder(String query) throws IOException {
        Path folder = AssistantSettings.musicFolder();
        if (!Files.isDirectory(folder)) return Optional.empty();

        List<Path> songs;
        try (Stream<Path> walk = Files.walk(folder, 4)) {
            songs = walk.filter(Files::isRegularFile)
                    .filter(p -> EXTENSIONS.stream().anyMatch(ext -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(ext)))
                    .collect(Collectors.toList());
        }
        if (query != null && !query.isBlank()) {
            String q = query.toLowerCase(Locale.ROOT);
            List<Path> matches = songs.stream()
                    .filter(p -> p.toString().toLowerCase(Locale.ROOT).contains(q)).collect(Collectors.toList());
            if (!matches.isEmpty()) songs = matches;
            else return Optional.empty();
        }
        if (songs.isEmpty()) return Optional.empty();

        Path pick = songs.get(RANDOM.nextInt(songs.size()));
        new ProcessBuilder("cmd", "/c", "start", "", pick.toString()).start();
        String name = pick.getFileName().toString();
        return Optional.of(name.substring(0, name.lastIndexOf('.')));
    }

    public static void playPause() throws IOException { sendMediaKey(VK_MEDIA_PLAY_PAUSE); }
    public static void next() throws IOException { sendMediaKey(VK_MEDIA_NEXT); }
    public static void previous() throws IOException { sendMediaKey(VK_MEDIA_PREV); }

    public static void searchYouTube(String query) throws IOException {
        open("https://www.youtube.com/results?search_query=" + URLEncoder.encode(query, StandardCharsets.UTF_8));
    }

    public static void searchSpotify(String query) throws IOException {
        open("spotify:search:" + URLEncoder.encode(query, StandardCharsets.UTF_8).replace("+", "%20"));
    }

    private static void open(String target) throws IOException {
        new ProcessBuilder("cmd", "/c", "start", "", target).start();
    }

    private static void sendMediaKey(int virtualKey) throws IOException {
        new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command",
                "(New-Object -ComObject WScript.Shell).SendKeys([char]" + virtualKey + ")").start();
    }
}
