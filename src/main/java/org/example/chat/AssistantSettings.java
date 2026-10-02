package org.example.chat;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Ajustes del asistente, en D:\DigimonProjectData\config\assistant.properties
 * (editable a mano mientras no exista la pantalla "Funciones"). Se relee en
 * cada uso, igual que personality_base.txt: cambiar el archivo aplica sin
 * reiniciar.
 */
public final class AssistantSettings {

    private static final Path FILE = AppPaths.config().resolve("assistant.properties");

    private static final String COUNTRY = "noticias.pais";
    private static final String PROACTIVE = "internet.iniciativaPropia";
    private static final String PROACTIVE_MINUTES = "noticias.iniciativaCadaMinutos";
    private static final String MUSIC_FOLDER = "musica.carpeta";
    private static final String RIVAL_FOLDER = "batalla.carpetaRivales";
    private static final String ONLINE_HOST = "online.servidor";
    private static final String ONLINE_PORT = "online.puerto";
    private static final String PLAYER_NAME = "online.nombreJugador";
    private static final int DEFAULT_ONLINE_PORT = 7777;
    /**
     * 127.0.0.1 = esta PC. El paquete de probadores (0.0.3.1) arranca con
     * -Ddigimon.servidor=<IP de Tailscale del anfitrión> para que no tengan
     * que escribirla; igual se puede cambiar en la pantalla de inicio.
     */
    private static final String DEFAULT_ONLINE_HOST = System.getProperty("digimon.servidor", "127.0.0.1");

    private AssistantSettings() {}

    /** Código de país de NewsService (PE, MX, CO, AR, CL, ES...). */
    public static String newsCountry() { return load().getProperty(COUNTRY, "PE").trim().toUpperCase(); }

    public static boolean proactiveInternet() { return Boolean.parseBoolean(load().getProperty(PROACTIVE, "true").trim()); }

    public static int proactiveNewsMinutes() {
        try {
            return Math.max(15, Integer.parseInt(load().getProperty(PROACTIVE_MINUTES, "90").trim()));
        } catch (NumberFormatException e) {
            return 90;
        }
    }

    public static Path musicFolder() {
        String configured = load().getProperty(MUSIC_FOLDER, "").trim();
        return configured.isEmpty() ? Paths.get(System.getProperty("user.home"), "Music") : Paths.get(configured);
    }

    /** Carpeta con DIM cards normales para los rivales de Batalla aleatoria (ver RivalDimPool). */
    public static java.util.Optional<Path> rivalDimFolder() {
        String configured = load().getProperty(RIVAL_FOLDER, "").trim();
        return configured.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(Paths.get(configured));
    }

    /** Servidor del VS Online: 127.0.0.1 si corre en esta PC, o la IP de Tailscale (100.x.x.x) del anfitrión. */
    public static String onlineHost() {
        if (sessionHost != null) return sessionHost;
        String configured = load().getProperty(ONLINE_HOST, "").trim();
        return configured.isEmpty() ? DEFAULT_ONLINE_HOST : configured;
    }

    public static int onlinePort() {
        try {
            return Integer.parseInt(load().getProperty(ONLINE_PORT, String.valueOf(DEFAULT_ONLINE_PORT)).trim());
        } catch (NumberFormatException e) {
            return DEFAULT_ONLINE_PORT;
        }
    }

    /** Nombre del jugador en la sala (hasta que existan las cuentas). Por defecto, el usuario de Windows. */
    public static String playerName() {
        if (sessionName != null) return sessionName;
        String configured = load().getProperty(PLAYER_NAME, "").trim();
        return configured.isEmpty() ? System.getProperty("user.name", "Jugador") : configured;
    }

    /** Nombre escrito en la pantalla de inicio la vez anterior; vacío si nunca se escribió. */
    public static String savedPlayerName() { return load().getProperty(PLAYER_NAME, "").trim(); }

    /**
     * Nombre y servidor escritos en la pantalla de inicio de ESTE programa (en memoria).
     * Bug real (2026-10-02): con dos programas abiertos en la misma PC, el archivo de
     * ajustes es uno solo y la sala leía el nombre que había guardado el OTRO programa.
     * Ahora cada programa usa el suyo; el archivo solo sirve para precargarlos la próxima vez.
     */
    private static volatile String sessionName, sessionHost;

    /** Guarda el nombre de usuario y el servidor de la pantalla de inicio sin tocar los demás ajustes. */
    public static void saveStartScreen(String name, String host) {
        if (name != null && !name.isBlank()) sessionName = name.trim();
        if (host != null && !host.isBlank()) sessionHost = host.trim();
        Properties p = load();
        p.setProperty(PLAYER_NAME, name == null ? "" : name.trim());
        if (host != null) p.setProperty(ONLINE_HOST, host.trim());
        rewrite(p);
    }

    /** Carpeta de rivales elegida desde la Batalla aleatoria (0.0.3.1 no tiene la pantalla de Ajustes). */
    public static void saveRivalFolder(String folder) {
        Properties p = load();
        p.setProperty(RIVAL_FOLDER, folder == null ? "" : folder.trim());
        rewrite(p);
    }

    private static void rewrite(Properties p) {
        writeFile(p.getProperty(COUNTRY, "PE").trim(),
                Boolean.parseBoolean(p.getProperty(PROACTIVE, "true").trim()),
                proactiveNewsMinutes(),
                p.getProperty(MUSIC_FOLDER, "").trim(),
                p.getProperty(RIVAL_FOLDER, "").trim(),
                p.getProperty(ONLINE_HOST, "").trim(),
                p.getProperty(ONLINE_PORT, String.valueOf(DEFAULT_ONLINE_PORT)).trim(),
                p.getProperty(PLAYER_NAME, "").trim());
    }

    /** Guarda desde la pantalla de Ajustes. Carpetas vacías/null = sin configurar (música: la de Windows). */
    public static void save(String country, boolean proactive, int proactiveMinutes, String musicFolder,
                            String rivalFolder) {
        // Los ajustes del VS Online aún no están en la pantalla: se conservan los que haya en el archivo.
        Properties current = load();
        writeFile(country, proactive, Math.max(15, proactiveMinutes), musicFolder == null ? "" : musicFolder,
                rivalFolder == null ? "" : rivalFolder,
                current.getProperty(ONLINE_HOST, "").trim(),
                current.getProperty(ONLINE_PORT, String.valueOf(DEFAULT_ONLINE_PORT)).trim(),
                current.getProperty(PLAYER_NAME, "").trim());
    }

    private static Properties load() {
        Properties p = new Properties();
        if (!Files.exists(FILE)) writeDefaults();
        try (Reader r = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
            p.load(r);
        } catch (IOException e) {
            System.out.println("[AJUSTES] No se pudo leer " + FILE + ": " + e.getMessage());
        }
        return p;
    }

    private static void writeDefaults() {
        writeFile("PE", true, 90, "", "", "", String.valueOf(DEFAULT_ONLINE_PORT), "");
    }

    private static void writeFile(String country, boolean proactive, int minutes, String musicFolder,
                                  String rivalFolder, String onlineHost, String onlinePort, String playerName) {
        // Properties escapa las barras invertidas: "D:\Música" se guarda como "D:\\Música".
        String folder = musicFolder.replace("\\", "\\\\");
        String rivals = rivalFolder.replace("\\", "\\\\");
        String text = """
                # Ajustes del asistente Digimon -- puedes editar este archivo (o usar Asistente > Funciones > Ajustes).
                # País de las noticias (Google Noticias): PE, MX, CO, AR, CL, EC, VE, ES, US
                noticias.pais=%s
                # true = el Digimon puede revisar noticias por su cuenta y comentarte alguna.
                internet.iniciativaPropia=%s
                # Cada cuántos minutos, como mínimo, puede comentar una noticia por su cuenta (mínimo 15).
                noticias.iniciativaCadaMinutos=%d
                # Carpeta de música. Vacío = tu carpeta "Música" de Windows.
                musica.carpeta=%s
                # Carpeta con DIM cards normales (.bin) que aportan los rivales de Batalla aleatoria.
                batalla.carpetaRivales=%s
                # VS Online: servidor (127.0.0.1 = esta PC; o la IP de Tailscale 100.x.x.x del anfitrión) y puerto.
                # Vacío = el de la edición (127.0.0.1, o el anfitrión en el paquete de probadores).
                online.servidor=%s
                online.puerto=%s
                # Tu nombre en la sala del VS Online. Vacío = tu usuario de Windows.
                online.nombreJugador=%s
                """.formatted(country, proactive, minutes, folder, rivals, onlineHost, onlinePort, playerName);
        try (Writer w = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8)) {
            w.write(text);
        } catch (IOException e) {
            System.out.println("[AJUSTES] No se pudo crear " + FILE + ": " + e.getMessage());
        }
    }
}
