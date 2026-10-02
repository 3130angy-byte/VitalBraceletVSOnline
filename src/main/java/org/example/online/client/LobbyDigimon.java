package org.example.online.client;

import com.github.cfogrady.vb.dim.sprite.SpriteData;
import javafx.scene.image.Image;

import org.example.dim.DimSpriteImageFactory;
import org.example.dim.VsDimData;
import org.example.online.Protocol;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Base64;
import java.util.List;

/**
 * El Digimon de un jugador dentro de la sala: sus 4 cuadros (IDLE_1,
 * IDLE_2, WALK_1, WALK_2), su rango (calculado por el servidor) y cómo
 * sigue al avatar.
 *
 * Los cuadros viajan como píxeles RGB565 CRUDOS, los mismos bytes de la VS
 * DIM, y se decodifican con DimSpriteImageFactory (código propio). Nunca se
 * reciben archivos de imagen: así ningún jugador puede mandar un PNG/JPG
 * malicioso al decodificador de imágenes de los demás.
 *
 * Tamaño: los sprites DIM (hasta 64x56) son enormes frente al avatar de
 * 16x24 en casillas de 16: en el MUNDO miden la mitad, pero la cámara de la
 * sala acerca x2, así que en pantalla se ven con sus píxeles originales 1:1.
 *
 * Seguimiento: recorre el mismo camino del avatar (ver follow). Es solo
 * visual y lo calcula cada cliente con las posiciones que ya manda el
 * servidor (sin tráfico extra).
 */
public final class LobbyDigimon {

    /** Distancia DE CAMINO detrás del avatar (el Digimon mide hasta 32 de ancho en el mundo). */
    private static final double FOLLOW_DISTANCE = 26;
    /** Velocidad normal = la del avatar en el servidor; si se queda atrás, un poco más. */
    private static final double FOLLOW_SPEED = Protocol.WALK_SPEED;
    private static final double CATCH_UP_SPEED = Protocol.WALK_SPEED * 1.35;
    private static final double CRUMB_SPACING = 3;
    private static final double SNAP_DISTANCE = 140;
    private static final long WALK_STEP_NANOS = 220_000_000L;
    private static final long IDLE_STEP_NANOS = 600_000_000L;

    /** "Migas" del camino del avatar que el Digimon todavía no recorre. */
    private final java.util.ArrayDeque<double[]> trail = new java.util.ArrayDeque<>();

    final String species;
    final String rank;
    final int attribute;
    /** Etapa cruda de la DIM (2 Child .. 5 Ultimate): fija los Vital Values que da o quita una batalla. */
    final int stage;
    /** Protocol.FRAME_* a tamaño COMPLETO (para el coliseo de las Batallas Oficiales). */
    final Image[] fullFrames;
    /** Sprite NAME en su tamaño nativo (pantallas del coliseo). */
    final Image nameImage;
    /** Huella de su aspecto (especie + cuadros): si cambia, el jugador cambió de Digimon y la sala lo anima con un portal. */
    final int look;
    /** Compañero del equipo (puesto 2), solo para el 2 vs 2; null si no trajo. No camina en la sala. */
    LobbyDigimon partner;

    double x = Double.NaN, y = Double.NaN;
    /** Los sprites DIM miran a la izquierda; a la derecha se espejan (convención del proyecto). */
    boolean facingRight = false;
    int frame = 0;

    private LobbyDigimon(String species, String rank, int attribute, int stage, Image[] fullFrames, Image nameImage, int look) {
        this.look = look;
        this.species = species;
        this.rank = rank;
        this.attribute = attribute;
        this.stage = stage;
        this.fullFrames = fullFrames;
        this.nameImage = nameImage;
    }

    // ---------------------------------------------------------------- enviar

    /**
     * Mensaje "digimon" a partir de la VS DIM. La especie la pone quien llama
     * (nunca el nombre del archivo; ver regla en CLAUDE.md). Stats NO se
     * envían: el rango lo calcula el servidor con los Power Trophies.
     */
    public static JSONObject payloadFrom(VsDimData vs, String species) {
        VsDimData.StatsRow c = vs.character();
        List<SpriteData.Sprite> sprites = vs.sprites();
        // Orden: IDLE_1, IDLE_2, WALK_1, WALK_2, ATTACK, DODGE (Protocol.FRAME_*).
        // Child+: NAME 0, IDLE_1 1, IDLE_2 2, WALK_1 3, WALK_2 4 ... ATTACK 11, DODGE 12.
        // Baby: NAME, IDLE_1, IDLE_2, WALK_1 (sin WALK_2 ni ataque: no pelean, se repiten cuadros).
        int[] indices = c.stage() <= 1 ? new int[]{1, 2, 3, 1, 1, 1} : new int[]{1, 2, 3, 4, 11, 12};
        JSONArray frames = new JSONArray();
        for (int i : indices) frames.put(pixels(sprites.get(i)));
        return Protocol.msg("digimon")
                .put("species", species)
                .put("attribute", c.attribute())
                .put("stage", c.stage())
                .put("powerTrophies", vs.powerTrophies())
                .put("frames", frames)
                .put("nameSprite", pixels(sprites.get(0)))
                // Stats: SOLO los usa el servidor para calcular Batallas Oficiales; nunca se reparten.
                .put("stats", new JSONObject().put("dp", c.dp()).put("hp", c.hp()).put("ap", c.ap())
                        .put("small", c.smallAttackId()).put("big", c.bigAttackId()).put("activity", c.activityType()));
    }

    /**
     * Mensaje "digimon" con TU EQUIPO: el puesto 1 (el que te sigue y pelea
     * las batallas tipo VB) y, si hay, el puesto 2 como "partner" (solo para
     * el 2 vs 2). Se reenvía cuando cambias el equipo en la PC.
     */
    public static JSONObject teamPayload(VsDimData primary, String primarySpecies,
                                         VsDimData secondary, String secondarySpecies) {
        JSONObject message = payloadFrom(primary, primarySpecies);
        if (secondary != null) {
            JSONObject partner = payloadFrom(secondary, secondarySpecies);
            partner.remove("t");
            message.put("partner", partner);
        }
        return message;
    }

    private static JSONObject pixels(SpriteData.Sprite s) {
        return new JSONObject().put("w", s.getWidth()).put("h", s.getHeight())
                .put("px", Base64.getEncoder().encodeToString(s.getPixelData()));
    }

    // ---------------------------------------------------------------- recibir

    /** Desde el mensaje que reparte el servidor (ya validado allá; aquí se revisa de nuevo lo mínimo). */
    static LobbyDigimon fromMessage(JSONObject m) {
        JSONArray arr = m.getJSONArray("frames");
        if (arr.length() != Protocol.DIGIMON_FRAMES) throw new IllegalArgumentException("cuadros incorrectos");
        Image[] full = new Image[Protocol.DIGIMON_FRAMES];
        for (int i = 0; i < full.length; i++) {
            SpriteData.Sprite sprite = decode(arr.getJSONObject(i), Protocol.MAX_FRAME_WIDTH, Protocol.MAX_FRAME_HEIGHT);
            full[i] = DimSpriteImageFactory.toImage(sprite, sprite.getWidth(), sprite.getHeight());
        }
        SpriteData.Sprite name = decode(m.getJSONObject("nameSprite"),
                Protocol.MAX_NAME_SPRITE_WIDTH, Protocol.MAX_NAME_SPRITE_HEIGHT);
        LobbyDigimon d = new LobbyDigimon(m.optString("species", "Digimon"), m.optString("rank", "-"),
                m.optInt("attribute", 0), m.optInt("stage", 2), full, DimSpriteImageFactory.toNativeImage(name),
                (m.optString("species", "") + arr).hashCode());
        JSONObject p = m.optJSONObject("partner");
        if (p != null) d.partner = fromMessage(p);
        return d;
    }

    /** Píxeles RGB565 crudos -> sprite, revisando tamaño y largo (el servidor ya lo validó; aquí, otra vez). */
    private static SpriteData.Sprite decode(JSONObject f, int maxW, int maxH) {
        int w = f.getInt("w"), h = f.getInt("h");
        if (w < 1 || h < 1 || w > maxW || h > maxH) throw new IllegalArgumentException("sprite fuera de tamaño");
        byte[] px = Base64.getDecoder().decode(f.getString("px"));
        if (px.length != w * h * 2) throw new IllegalArgumentException("sprite incompleto");
        return SpriteData.Sprite.builder().width(w).height(h).pixelData(px).build();
    }

    /** El servidor manda "-" cuando no llega al rango C (menos de 10 puntos). */
    static boolean hasRank(String rank) {
        return rank != null && rank.length() == 1 && "CBAS".contains(rank);
    }

    /** "rango B" o "sin rango", para los textos. */
    static String rankText(String rank) {
        return hasRank(rank) ? "rango " + rank : "sin rango";
    }

    // ---------------------------------------------------------------- seguir y animar

    /**
     * Sigue al avatar POR SU MISMO CAMINO (como los compañeros de los juegos
     * de Pokémon): el avatar va dejando "migas" y el Digimon las recorre en
     * orden, a velocidad constante, hasta quedar a FOLLOW_DISTANCE de camino
     * detrás. Así no corta esquinas ni atraviesa muros, y no tiene el tirón
     * de "látigo" del seguimiento anterior (acercarse un % por cuadro).
     *
     * @param dt segundos desde el cuadro anterior (el movimiento no depende de los FPS)
     */
    void follow(double avatarX, double avatarY, long now, double dt) {
        if (Double.isNaN(x) || Math.hypot(avatarX - x, avatarY - y) > SNAP_DISTANCE) {
            // Recién llegado, o el avatar "saltó" (p. ej. entró a la sala): aparece detrás.
            x = avatarX - FOLLOW_DISTANCE;
            y = avatarY;
            trail.clear();
        }
        double[] last = trail.isEmpty() ? null : trail.peekLast();
        if (last == null || Math.hypot(avatarX - last[0], avatarY - last[1]) >= CRUMB_SPACING) {
            trail.addLast(new double[]{avatarX, avatarY});
        }

        // Largo del camino que falta hasta el avatar.
        double remaining = 0, px = x, py = y;
        for (double[] c : trail) {
            remaining += Math.hypot(c[0] - px, c[1] - py);
            px = c[0];
            py = c[1];
        }
        remaining += Math.hypot(avatarX - px, avatarY - py);

        double movedX = 0;
        double moved = 0;
        if (remaining > FOLLOW_DISTANCE) {
            // Si se quedó muy atrás, apura un poco el paso (nunca teletransportándose).
            double speed = remaining > FOLLOW_DISTANCE * 2.5 ? CATCH_UP_SPEED : FOLLOW_SPEED;
            double budget = Math.min(speed * dt, remaining - FOLLOW_DISTANCE);
            while (budget > 0 && !trail.isEmpty()) {
                double[] c = trail.peekFirst();
                double d = Math.hypot(c[0] - x, c[1] - y);
                if (d <= budget) {
                    movedX += c[0] - x;
                    x = c[0];
                    y = c[1];
                    budget -= d;
                    moved += d;
                    trail.pollFirst();
                } else {
                    double fx = (c[0] - x) / d * budget, fy = (c[1] - y) / d * budget;
                    x += fx;
                    y += fy;
                    movedX += fx;
                    moved += budget;
                    budget = 0;
                }
            }
        }
        boolean moving = moved > 0.05;
        if (Math.abs(movedX) > 0.05) facingRight = movedX > 0;
        frame = moving
                ? 2 + (int) ((now / WALK_STEP_NANOS) % 2)
                : (int) ((now / IDLE_STEP_NANOS) % 2);
    }

    /** Mismo Digimon con datos nuevos (p. ej. solo cambió el compañero): sigue donde estaba, sin saltar. */
    void takePlaceOf(LobbyDigimon old) {
        x = old.x;
        y = old.y;
        facingRight = old.facingRight;
        frame = old.frame;
        trail.addAll(old.trail);
    }

    /** Lo deja quieto en un punto (al salir del portal) y vuelve a seguir al avatar desde ahí. */
    void placeAt(double x, double y, boolean facingRight) {
        this.x = x;
        this.y = y;
        this.facingRight = facingRight;
        trail.clear();
    }

    /** Cuadro actual a tamaño COMPLETO (la sala lo dibuja a la mitad del mundo = píxeles 1:1 en pantalla). */
    Image currentFrame() {
        return fullFrames[frame];
    }
}
