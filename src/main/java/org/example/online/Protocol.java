package org.example.online;

import org.json.JSONObject;

import java.io.IOException;
import java.io.Reader;

/**
 * Protocolo del VS Online (Fase 1 del documento de diseño: esqueleto de red).
 *
 * Transporte: TCP, UN mensaje JSON por línea (UTF-8, termina en '\n').
 * Campo "t" = tipo de mensaje. El servidor es la única fuente de verdad:
 * el cliente solo PIDE cosas (moverse a un punto, decir algo en el chat);
 * el servidor decide las posiciones reales y reparte los mensajes.
 *
 * Cliente → servidor:
 *   {"t":"hello","v":5,"name":"Fernando"}     primer mensaje, obligatorio
 *   {"t":"digimon","species":"MagnaKidmon","attribute":1,"stage":5,
 *    "powerTrophies":35,"frames":[{"w":64,"h":56,"px":"<base64>"} x6],
 *    "nameSprite":{"w":160,"h":15,"px":"..."},
 *    "stats":{"dp":..,"hp":..,"ap":..,"small":..,"big":..,"activity":..}}
 *                                              tu Digimon (una vez); frames = IDLE_1,
 *                                              IDLE_2, WALK_1, WALK_2, ATTACK, DODGE como
 *                                              píxeles RGB565 CRUDOS (nunca archivos de
 *                                              imagen). "stats" SOLO los ve el servidor.
 *   {"t":"available","on":true}               (NPC Batalla oficial) disponible o no
 *   {"t":"listAvailable"}                     pedir la lista de disponibles
 *   {"t":"challenge","target":4,"mode":"libre"}  retar; mode = "libre" (con bono de
 *                                              puntos) u "original" (stats de la DIM)
 *   {"t":"challengeReply","from":3,"accept":true}
 *   {"t":"battleDone"}                        terminé de ver la pelea
 *   {"t":"move","x":120,"y":300}               quiero ir a este punto
 *   {"t":"chat","text":"hola"}
 *   {"t":"ping"}                               "sigo aquí" (cada 30 s)
 *
 * Servidor → cliente:
 *   {"t":"welcome","id":3,"room":"sala-1","map":"torneo-1f","w":800,"h":496}
 *   {"t":"snapshot","players":[{"id":3,"name":"...","x":..,"y":..}, ...]}
 *   {"t":"joined","id":4,"name":"..."}   {"t":"left","id":4,"name":"..."}
 *   {"t":"digimon","id":3,"species":"...","attribute":1,"stage":5,"rank":"B",
 *    "frames":[...],"nameSprite":{...}}      el rango lo calcula el SERVIDOR; sin stats
 *   {"t":"availableList","players":[{"id":4,"name":"...","species":"...","rank":"A"}]}
 *   {"t":"availability","on":true}            confirma tu estado de disponible
 *   {"t":"challenged","from":3,"name":"...","species":"...","rank":"B",
 *    "mode":"libre","seconds":15}
 *   {"t":"challengePending","target":4,"name":"...","mode":"libre","seconds":15}
 *   {"t":"challengeEnded","reason":"..."}      rechazado, sin respuesta, cancelado...
 *   {"t":"battle","a":{id,attribute,maxHp,small},"b":{...},"rounds":[...],"winner":id|0,
 *    "mode":"libre"}
 *                                              la pelea calculada UNA vez por el servidor
 *                                              (winner 0 = empate real)
 *   {"t":"chat","id":3,"name":"...","text":"..."}
 *   {"t":"error","msg":"..."}               (el servidor puede cerrar después)
 */
public final class Protocol {

    /** 5: modo de Batalla Oficial (Libre / Original). Un cliente v4 ya no sirve. */
    public static final int VERSION = 5;
    public static final int DEFAULT_PORT = 7777;

    /** Tamaño de la sala en unidades del mundo (el cliente lo escala a su ventana). */
    public static final int ROOM_WIDTH = LobbyMap.WIDTH;
    public static final int ROOM_HEIGHT = LobbyMap.HEIGHT;

    /** Decisión del documento de diseño: hasta 20 jugadores por sala. */
    public static final int MAX_PLAYERS_PER_ROOM = 20;

    public static final int MAX_NAME_LENGTH = 16;
    public static final int MAX_CHAT_LENGTH = 200;
    /**
     * Una línea más larga que esto se considera abuso y se corta la conexión.
     * 128 KB: el mensaje "digimon" (6 cuadros de hasta 64x56 + el sprite NAME
     * en RGB565 = ~50 KB, ~67 KB en base64) es el más grande; todo lo demás
     * mide menos de 2 KB.
     */
    public static final int MAX_LINE_LENGTH = 128 * 1024;

    /** Cuadros del Digimon que viajan: IDLE_1, IDLE_2, WALK_1, WALK_2, ATTACK, DODGE. */
    public static final int DIGIMON_FRAMES = 6;
    public static final int FRAME_IDLE_1 = 0, FRAME_IDLE_2 = 1, FRAME_WALK_1 = 2, FRAME_WALK_2 = 3,
            FRAME_ATTACK = 4, FRAME_DODGE = 5;
    /** Sprite NAME: 15 px de alto y ancho múltiplo de 80 (hasta 240 visto en DIM reales). */
    public static final int MAX_NAME_SPRITE_WIDTH = 320;
    public static final int MAX_NAME_SPRITE_HEIGHT = 16;
    /** Tiempo para aceptar un reto (decisión del usuario). */
    public static final int CHALLENGE_SECONDS = 15;
    /** Las Batallas Oficiales piden Child o superior (los Baby no tienen sprites de ataque). */
    public static final int MIN_BATTLE_STAGE = 2;
    /**
     * Modos de Batalla Oficial (decisión del usuario): LIBRE aplica el bono de
     * puntos (Power Trophies / trofeos, tope 120); ORIGINAL pelea con los
     * stats de la DIM, sin bono.
     */
    public static final String MODE_FREE = "libre", MODE_ORIGINAL = "original";

    public static boolean isValidMode(String mode) {
        return MODE_FREE.equals(mode) || MODE_ORIGINAL.equals(mode);
    }

    /** "Batalla Libre" / "Batalla Original". */
    public static String modeName(String mode) {
        return MODE_ORIGINAL.equals(mode) ? "Batalla Original" : "Batalla Libre";
    }
    /** Tamaño máximo de un cuadro (el lienzo de un sprite DIM). */
    public static final int MAX_FRAME_WIDTH = 64;
    public static final int MAX_FRAME_HEIGHT = 56;
    public static final int MAX_POWER_TROPHIES = 9999;

    /** El cliente manda ping cada 30 s; sin noticias en 90 s el servidor lo desconecta. */
    public static final int PING_INTERVAL_SECONDS = 30;
    public static final int IDLE_TIMEOUT_SECONDS = 90;

    private Protocol() {}

    public static String line(JSONObject message) {
        return message.toString() + "\n";
    }

    public static JSONObject msg(String type) {
        return new JSONObject().put("t", type);
    }

    /**
     * Lee una línea con tope de tamaño (readLine() de BufferedReader no tiene
     * tope: un cliente malicioso podría mandar una línea infinita y agotar la
     * memoria del servidor). Devuelve null al cerrar la conexión.
     */
    public static String readLine(Reader in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') return sb.toString();
            if (c == '\r') continue;
            if (sb.length() >= MAX_LINE_LENGTH) throw new IOException("Línea demasiado larga");
            sb.append((char) c);
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    /** Quita caracteres de control y recorta; nunca devuelve null. */
    public static String cleanText(String raw, int maxLength) {
        if (raw == null) return "";
        String cleaned = raw.replaceAll("\\p{Cntrl}", " ").trim().replaceAll("\\s{2,}", " ");
        return cleaned.length() > maxLength ? cleaned.substring(0, maxLength) : cleaned;
    }
}
