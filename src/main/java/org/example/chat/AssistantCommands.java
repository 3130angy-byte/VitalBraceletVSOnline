package org.example.chat;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Comandos de asistente detectados por el PROGRAMA, no por la IA.
 *
 * Probado en la comparación de modelos: en este hardware ningún modelo de
 * 3-4B usa las herramientas de forma confiable (ministral-3:3b y qwen3.5:4b
 * inventaron la hora; qwen3.5 prometió un recordatorio sin crearlo). Aquí el
 * programa reconoce la frase, ejecuta la acción de verdad y le pasa el
 * resultado a la IA solo para que lo cuente con su voz. Mismo principio que
 * la respuesta fija en crisis: lo que debe ser exacto no lo decide el modelo.
 *
 * Aplicaciones: SOLO una lista blanca fija. Nunca se ejecuta texto del
 * usuario como comando.
 */
public final class AssistantCommands {

    /**
     * Resultado de un comando ya ejecutado. mustMention = dato exacto que la
     * respuesta debe contener; si falta, se agrega fallbackSentence entera.
     */
    public record Result(String factForModel, String mustMention, String fallbackSentence) {
        Result(String factForModel) { this(factForModel, null, null); }
    }

    private static final Locale ES = Locale.forLanguageTag("es");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", ES);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", ES);

    private static final Pattern ASK_TIME = Pattern.compile(
            "\\b(qu[eé] hora es|qu[eé] horas son|dime la hora|me dices la hora)\\b");
    private static final Pattern ASK_DATE = Pattern.compile(
            "\\b(qu[eé] d[ií]a es( hoy)?|qu[eé] fecha es( hoy)?|a qu[eé] estamos)\\b");

    private static final String AMOUNT = "(\\d{1,3}|un|una|media)";
    private static final String UNIT = "(minutos?|mins?|horas?)";
    // "recuérdame tomar agua en 20 minutos"
    private static final Pattern REMIND_TEXT_FIRST = Pattern.compile(
            "recu[eé]rdame\\s+(?:que\\s+)?(.+?)\\s+en\\s+" + AMOUNT + "\\s*" + UNIT);
    // "en 20 minutos recuérdame tomar agua"
    private static final Pattern REMIND_TIME_FIRST = Pattern.compile(
            "en\\s+" + AMOUNT + "\\s*" + UNIT + "\\s*,?\\s*recu[eé]rdame\\s+(?:que\\s+)?(.+)");

    private static final Pattern OPEN = Pattern.compile(
            "^(?:por favor\\s*,?\\s*)?(?:[aá]breme|abre|abrir|inicia|ejecuta)\\s+(?:el|la|los|las|mi)?\\s*(.+?)[.!?]*$");

    /** Lista blanca: palabras clave (sin tildes) -> comando fijo de Windows. */
    private static final Map<String, List<String>> APPS = new LinkedHashMap<>();
    private static final Map<String, String> APP_NAMES = new LinkedHashMap<>();
    static {
        app(List.of("bloc de notas", "notepad", "notas"), "el Bloc de notas", List.of("notepad.exe"));
        app(List.of("calculadora"), "la Calculadora", List.of("calc.exe"));
        app(List.of("paint"), "Paint", List.of("mspaint.exe"));
        app(List.of("explorador", "mis archivos", "carpetas"), "el Explorador de archivos", List.of("explorer.exe"));
        app(List.of("word"), "Word", List.of("cmd", "/c", "start", "", "winword"));
        app(List.of("excel"), "Excel", List.of("cmd", "/c", "start", "", "excel"));
        app(List.of("navegador", "internet", "chrome", "google"), "el navegador",
                List.of("cmd", "/c", "start", "", "https://www.google.com"));
    }

    /** Aplicaciones que el menú muestra como botones (etiqueta visible, clave de la lista blanca). */
    public record AppEntry(String label, String key) {}

    public static final List<AppEntry> MENU_APPS = List.of(
            new AppEntry("Bloc de notas", "bloc de notas"),
            new AppEntry("Calculadora", "calculadora"),
            new AppEntry("Paint", "paint"),
            new AppEntry("Explorador de archivos", "explorador"),
            new AppEntry("Word", "word"),
            new AppEntry("Excel", "excel"),
            new AppEntry("Navegador", "navegador"));

    /** Abre una app de la lista blanca por su clave. Devuelve su nombre visible ("la Calculadora"). */
    public static String launchApp(String key) throws IOException {
        List<String> command = APPS.get(key);
        if (command == null) throw new IOException("No está en la lista blanca: " + key);
        new ProcessBuilder(command).start();
        return APP_NAMES.get(key);
    }

    private static void app(List<String> keys, String displayName, List<String> command) {
        for (String k : keys) {
            APPS.put(k, command);
            APP_NAMES.put(k, displayName);
        }
    }

    private AssistantCommands() {}

    // ---------- Noticias (internet) ----------
    // Pide noticias: "dame/dime/cuéntame/qué/hay ... noticias", "noticias de X",
    // "titulares". No se activa con "tengo una noticia" (el usuario contando algo).
    private static final Pattern NEWS_REQUEST = Pattern.compile(
            "\\b(dame|dime|cu[eé]ntame|hay|qu[eé]|ver|mu[eé]strame|busca|l[eé]eme|ponme)\\b[^.?!]{0,25}\\bnoticias?\\b"
                    + "|^noticias?\\b|\\btitulares\\b");
    private static final Pattern NEWS_TOPIC = Pattern.compile(
            "noticias?\\s+(?:de|sobre|del|acerca de)\\s+(?:la\\s+|el\\s+|los\\s+|las\\s+)?(.+?)[.!?]*$");

    // ---------- Música (reproductor del usuario) ----------
    private static final Pattern MUSIC_SEARCH = Pattern.compile(
            "\\b(?:pon|ponme|busca|reproduce)\\s+(.+?)\\s+en\\s+(youtube|spotify)\\b");
    private static final Pattern MUSIC_PLAY = Pattern.compile(
            "^(?:por favor\\s*,?\\s*)?(?:pon|ponme|reproduce|toca)\\s+(?:algo de\\s+)?(?:m[uú]sica|una canci[oó]n|canciones)"
                    + "(?:\\s+de\\s+(.+?))?[.!?]*$");
    private static final Pattern MUSIC_PAUSE = Pattern.compile(
            "^(?:por favor\\s*,?\\s*)?(?:pausa|pon pausa|det[eé]n|reanuda|contin[uú]a|quita la pausa)"
                    + "(?:\\s+(?:la|esta)?\\s*(?:m[uú]sica|canci[oó]n))?[.!?]*$");
    private static final Pattern MUSIC_NEXT = Pattern.compile(
            "\\b(siguiente canci[oó]n|otra canci[oó]n|pasa (?:la|de) canci[oó]n|cambia (?:la|de) canci[oó]n)\\b");
    private static final Pattern MUSIC_PREV = Pattern.compile("\\b(canci[oó]n anterior|regresa la canci[oó]n)\\b");

    /**
     * Versión asíncrona: noticias necesitan internet (no se bloquea la
     * interfaz); el resto se resuelve al instante con tryHandle.
     */
    public static Optional<CompletableFuture<Result>> tryHandleAsync(String userText,
                                                                     BiConsumer<Integer, String> scheduleReminder) {
        if (userText == null) return Optional.empty();
        String text = userText.toLowerCase(ES).trim();

        if (NEWS_REQUEST.matcher(text).find()) {
            Matcher t = NEWS_TOPIC.matcher(text);
            String topic = t.find() ? t.group(1).trim() : null;
            return Optional.of(newsResult(topic));
        }

        Optional<Result> music = tryMusic(text);
        if (music.isPresent()) return Optional.of(CompletableFuture.completedFuture(music.get()));

        return tryHandle(userText, scheduleReminder).map(CompletableFuture::completedFuture);
    }

    private static CompletableFuture<Result> newsResult(String topic) {
        // 3 titulares y sin el nombre del medio: probado, con 5 titulares
        // completos el prompt de un Ultimate no terminó en 400s en esta CPU.
        return NewsService.headlines(topic, 3).handle((titles, error) -> {
            if (error != null) {
                System.out.println("[NOTICIAS] Error: " + error.getMessage());
                return new Result("Intentaste ver las noticias en internet pero no pudiste conectarte. "
                        + "Díselo con honestidad y brevemente; NO inventes noticias.");
            }
            if (titles.isEmpty()) {
                return new Result("Buscaste noticias" + (topic != null ? " sobre \"" + topic + "\"" : "")
                        + " pero no encontraste ninguna. Díselo brevemente; NO inventes noticias.");
            }
            StringBuilder sb = new StringBuilder("Acabas de ver en internet estos titulares REALES de hoy");
            if (topic != null) sb.append(" sobre \"").append(topic).append("\"");
            sb.append(":\n");
            for (int i = 0; i < titles.size(); i++) {
                // "Titular - El Comercio Perú" -> "Titular" (el medio no aporta y cuesta tokens)
                String title = titles.get(i).replaceFirst("\\s+-\\s+[^-]{2,60}$", "");
                sb.append(i + 1).append(") ").append(title).append("\n");
            }
            // Probado: un Ultimate agregó "Europa y EE.UU. siguen liderando", que no
            // estaba en ningún titular. Se pide una frase por titular y nada más.
            sb.append("Cuéntale 2 o 3 con tu voz, UNA frase corta por titular, en texto corrido (sin listas). "
                    + "Solo lo que está escrito en cada titular: NO agregues comparaciones, países, cifras, "
                    + "causas ni opiniones que no aparezcan en él.");
            return new Result(sb.toString());
        });
    }

    private static Optional<Result> tryMusic(String text) {
        try {
            Matcher m = MUSIC_SEARCH.matcher(text);
            if (m.find()) {
                String query = m.group(1).replaceFirst("^(?:m[uú]sica de|canciones de|algo de)\\s+", "").trim();
                boolean spotify = m.group(2).equals("spotify");
                if (spotify) MusicControl.searchSpotify(query); else MusicControl.searchYouTube(query);
                return Optional.of(new Result("Acabas de abrir DE VERDAD la búsqueda de \"" + query + "\" en "
                        + (spotify ? "Spotify" : "YouTube") + ". Dile que elija la canción y le dé play (tú no "
                        + "puedes darle play ahí). Breve."));
            }
            m = MUSIC_PLAY.matcher(text);
            if (m.find()) {
                String query = m.group(1);
                Optional<String> song = MusicControl.playFromFolder(query);
                if (song.isPresent()) {
                    return Optional.of(new Result("Acabas de poner DE VERDAD la canción \"" + song.get()
                            + "\" de su carpeta de música. Cuéntaselo brevemente.", song.get(),
                            "Puse \"" + song.get() + "\"."));
                }
                return Optional.of(new Result("Quisiste poner música" + (query != null ? " de \"" + query + "\"" : "")
                        + " pero no encontraste canciones en su carpeta de música (" + AssistantSettings.musicFolder()
                        + "). Díselo brevemente; NO digas que pusiste música."));
            }
            if (MUSIC_PAUSE.matcher(text).find()) {
                MusicControl.playPause();
                return Optional.of(new Result("Acabas de pausar/reanudar DE VERDAD la música. Díselo en pocas palabras."));
            }
            if (MUSIC_NEXT.matcher(text).find()) {
                MusicControl.next();
                return Optional.of(new Result("Acabas de pasar DE VERDAD a la siguiente canción. Díselo en pocas palabras."));
            }
            if (MUSIC_PREV.matcher(text).find()) {
                MusicControl.previous();
                return Optional.of(new Result("Acabas de volver DE VERDAD a la canción anterior. Díselo en pocas palabras."));
            }
        } catch (IOException e) {
            System.out.println("[MUSICA] Error: " + e.getMessage());
            return Optional.of(new Result("Intentaste controlar la música pero falló. Díselo con honestidad y brevemente."));
        }
        return Optional.empty();
    }

    /**
     * @param scheduleReminder (minutos, texto): lo agenda quien tiene acceso a
     *                         la interfaz (AiConversationController).
     */
    public static Optional<Result> tryHandle(String userText, BiConsumer<Integer, String> scheduleReminder) {
        if (userText == null) return Optional.empty();
        String text = userText.toLowerCase(ES).trim();

        Matcher m = REMIND_TEXT_FIRST.matcher(text);
        if (m.find()) return Optional.of(reminder(m.group(1), m.group(2), m.group(3), scheduleReminder));
        m = REMIND_TIME_FIRST.matcher(text);
        if (m.find()) return Optional.of(reminder(m.group(3), m.group(1), m.group(2), scheduleReminder));

        if (ASK_TIME.matcher(text).find()) {
            String now = LocalDateTime.now().format(TIME);
            return Optional.of(new Result("El usuario preguntó la hora. Son EXACTAMENTE las " + now
                    + ". Díselo con esa hora exacta.", now, "Son las " + now + "."));
        }
        if (ASK_DATE.matcher(text).find()) {
            String today = LocalDateTime.now().format(DATE);
            return Optional.of(new Result("El usuario preguntó la fecha. Hoy es EXACTAMENTE " + today
                    + ". Díselo con esa fecha exacta.", today, "Hoy es " + today + "."));
        }

        m = OPEN.matcher(text);
        if (m.find() && looksLikeAppRequest(m.group(1))) return Optional.of(openApp(m.group(1)));

        return Optional.empty();
    }

    /**
     * Evita tomar como comando frases como "abre tu corazón conmigo": solo
     * cuenta si el objeto es corto y no es "tu/te/me...". Si está en la lista
     * blanca, siempre cuenta.
     */
    static boolean looksLikeAppRequest(String target) {
        if (resolveApp(target).isPresent()) return true;
        String t = target.trim();
        if (t.matches("^(tu|tus|te|me|mi|mis|nos|su|sus)\\b.*")) return false;
        return t.split("\\s+").length <= 3;
    }

    /** Nombre visible de la aplicación de la lista blanca que coincide, si alguna. Sin ejecutar nada. */
    public static Optional<String> resolveApp(String requested) {
        String key = stripAccents(requested.toLowerCase(ES));
        for (String k : APPS.keySet()) {
            if (key.contains(k)) return Optional.of(APP_NAMES.get(k));
        }
        return Optional.empty();
    }

    private static Result reminder(String what, String amount, String unit, BiConsumer<Integer, String> schedule) {
        int n = switch (amount) {
            case "un", "una" -> 1;
            case "media" -> 30;
            default -> Integer.parseInt(amount);
        };
        boolean hours = unit.startsWith("hora") && !amount.equals("media");
        int minutes = Math.min(24 * 60, Math.max(1, hours ? n * 60 : n));
        String task = what.replaceAll("[.!?]+$", "").trim();
        schedule.accept(minutes, task);
        String when = minutes % 60 == 0 && minutes >= 60
                ? (minutes / 60) + (minutes == 60 ? " hora" : " horas")
                : minutes + (minutes == 1 ? " minuto" : " minutos");
        // mustMention = "20 minutos": probado, un Baby I respondió "¡yo ayudé!" sin
        // decir qué ni cuándo; si falta, se agrega la confirmación.
        return new Result("Acabas de crear DE VERDAD un recordatorio: en " + when + " le recordarás al usuario \""
                + task + "\". Confírmaselo brevemente, diciendo cuándo.", when,
                "Te aviso en " + when + ": " + task + ".");
    }

    private static Result openApp(String requested) {
        String key = stripAccents(requested);
        for (Map.Entry<String, List<String>> e : APPS.entrySet()) {
            if (key.contains(e.getKey())) {
                String name = APP_NAMES.get(e.getKey());
                try {
                    new ProcessBuilder(e.getValue()).start();
                    return new Result("Acabas de abrir DE VERDAD " + name + " para el usuario. Cuéntaselo brevemente.");
                } catch (IOException ex) {
                    System.out.println("[ASISTENTE] No se pudo abrir " + name + ": " + ex.getMessage());
                    return new Result("Intentaste abrir " + name + " pero no se pudo (quizá no está instalado). "
                            + "Díselo con honestidad y brevemente.");
                }
            }
        }
        return new Result("El usuario te pidió abrir \"" + requested + "\", pero todavía no sabes abrir esa "
                + "aplicación. Díselo con honestidad y brevemente; NO digas que la abriste.");
    }

    private static String stripAccents(String s) {
        return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }
}
