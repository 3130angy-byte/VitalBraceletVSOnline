package org.example.online;

import org.example.chat.AppPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Lista de ACCESO al servidor VS Online (pedido del usuario, 0.0.3.z): solo
 * entran a la sala los nombres de esta lista (máx. 20) y el anfitrión. Quien no
 * está, se queda con las funciones locales (Batalla aleatoria, ARENA local,
 * Laboratorio...). La edita el anfitrión en el Laboratorio (pestaña ACCESO);
 * el servidor la vuelve a leer sola cada pocos segundos y, si alguien fue
 * RETIRADO de la lista, lo saca de la sala (no es un baneo: se lo puede volver
 * a agregar).
 *
 * Archivo: ...\DigimonProjectData\config\lista-acceso.txt (texto, una persona
 * por línea; "anfitrion=Nombre" para el anfitrión, que no cuenta en las 20).
 * El servidor de ESA PC es el que la usa: el anfitrión debe tener el servidor
 * en su propia PC (servidor del programa o "Servidor VS Online.exe").
 * Los nombres se comparan sin importar mayúsculas ni espacios de los bordes.
 */
public final class AccessList {

    public static final int MAX = 20;
    private static final String HOST_KEY = "anfitrion=";

    private final String host;
    private final List<String> names;

    private AccessList(String host, List<String> names) {
        this.host = host;
        this.names = names;
    }

    public static Path file() {
        return AppPaths.config().resolve("lista-acceso.txt");
    }

    /** Quiénes están en la sala ahora (lo escribe el servidor de esta PC; lo muestra el Laboratorio). */
    public static Path onlineFile() {
        return AppPaths.config().resolve("conectados.txt");
    }

    /** Lee la lista (la crea vacía si no existe: así nadie entra hasta que el anfitrión agregue nombres). */
    public static synchronized AccessList load() {
        Path f = file();
        String host = "";
        List<String> names = new ArrayList<>();
        try {
            if (!Files.exists(f)) write("", List.of());
            for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                String t = line.trim();
                if (t.isEmpty() || t.startsWith("#")) continue;
                if (t.toLowerCase(Locale.ROOT).startsWith(HOST_KEY)) {
                    host = clean(t.substring(HOST_KEY.length()));
                } else if (names.size() < MAX) {
                    String n = clean(t);
                    if (!n.isEmpty() && names.stream().noneMatch(x -> same(x, n))) names.add(n);
                }
            }
        } catch (IOException e) {
            System.out.println("[ACCESO] No se pudo leer " + f + ": " + e.getMessage());
        }
        return new AccessList(host, names);
    }

    public String host() { return host; }

    public List<String> names() { return List.copyOf(names); }

    /** ¿Puede entrar a la sala? (el anfitrión siempre). */
    public boolean isAllowed(String name) {
        String n = clean(name);
        if (n.isEmpty()) return false;
        return (!host.isEmpty() && same(host, n)) || names.stream().anyMatch(x -> same(x, n));
    }

    /** Agrega un nombre. Devuelve un error, o null si salió bien. */
    public static synchronized String add(String name) {
        String n = clean(name);
        if (n.isEmpty()) return "Escribe un nombre.";
        AccessList current = load();
        if (current.isAllowed(n)) return n + " ya puede entrar.";
        if (current.names.size() >= MAX) return "La lista está llena (máx. " + MAX + "). Retira a alguien primero.";
        List<String> names = new ArrayList<>(current.names);
        names.add(n);
        return write(current.host, names) ? null : "No se pudo guardar la lista.";
    }

    /** Retira un nombre de la lista (el servidor lo saca de la sala en unos segundos). No es un baneo. */
    public static synchronized void remove(String name) {
        AccessList current = load();
        List<String> names = new ArrayList<>(current.names);
        names.removeIf(x -> same(x, name));
        write(current.host, names);
    }

    /** El anfitrión (tu nombre de jugador): siempre puede entrar y no cuenta en las 20. */
    public static synchronized void setHost(String name) {
        AccessList current = load();
        write(clean(name), current.names);
    }

    public static long lastModified() {
        try {
            return Files.exists(file()) ? Files.getLastModifiedTime(file()).toMillis() : 0;
        } catch (IOException e) {
            return 0;
        }
    }

    /** El servidor anota quiénes están en la sala. */
    public static void writeOnline(Collection<String> online) {
        try {
            Files.write(onlineFile(), online, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    /** Quiénes están en la sala según el servidor de esta PC (vacío si no hay servidor). */
    public static Set<String> online() {
        Set<String> out = new LinkedHashSet<>();
        try {
            if (Files.exists(onlineFile())) {
                for (String line : Files.readAllLines(onlineFile(), StandardCharsets.UTF_8)) {
                    if (!line.isBlank()) out.add(line.trim().toLowerCase(Locale.ROOT));
                }
            }
        } catch (IOException ignored) {
        }
        return out;
    }

    private static boolean write(String host, List<String> names) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Lista de acceso al servidor VS Online (máx. ").append(MAX).append(" personas, una por línea).\n");
        sb.append("# Solo estos nombres (y el anfitrión) entran a la sala. Se edita desde el Laboratorio > ACCESO.\n");
        sb.append(HOST_KEY).append(host == null ? "" : host).append('\n');
        for (String n : names) sb.append(n).append('\n');
        try {
            Files.writeString(file(), sb.toString(), StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) {
            System.out.println("[ACCESO] No se pudo guardar " + file() + ": " + e.getMessage());
            return false;
        }
    }

    /** Mismo limpiado que el servidor aplica al nombre del jugador. */
    private static String clean(String name) {
        return Protocol.cleanText(name, Protocol.MAX_NAME_LENGTH);
    }

    private static boolean same(String a, String b) {
        return a.trim().equalsIgnoreCase(b.trim());
    }
}
