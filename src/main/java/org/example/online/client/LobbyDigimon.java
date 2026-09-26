package org.example.online.client;

import com.github.cfogrady.vb.dim.sprite.SpriteData;
import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;

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
 * 16x24 en casillas de 16; se dibujan a la MITAD, reducidos promediando
 * cada bloque de 2x2 (mantiene la forma mejor que saltarse píxeles).
 *
 * Seguimiento: una "correa" corta -- el Digimon avanza hacia el avatar
 * hasta quedar a FOLLOW_DISTANCE. Es solo visual y lo calcula cada cliente
 * con las posiciones que ya manda el servidor (sin tráfico extra).
 */
public final class LobbyDigimon {

    /** El Digimon (hasta 32 de ancho a la mitad) queda al lado del avatar sin encimarse. */
    private static final double FOLLOW_DISTANCE = 24;
    private static final double FOLLOW_EASE = 0.18;
    private static final long WALK_STEP_NANOS = 180_000_000L;
    private static final long IDLE_STEP_NANOS = 600_000_000L;

    final String species;
    final String rank;
    final int attribute;
    /** Etapa cruda de la DIM (2 Child .. 5 Ultimate): fija los Vital Values que da o quita una batalla. */
    final int stage;
    /** 0 IDLE_1, 1 IDLE_2, 2 WALK_1, 3 WALK_2 -- ya reducidos a la mitad (para la sala). */
    final Image[] frames;
    /** Protocol.FRAME_* a tamaño COMPLETO (para el coliseo de las Batallas Oficiales). */
    final Image[] fullFrames;
    /** Sprite NAME en su tamaño nativo (pantallas del coliseo). */
    final Image nameImage;

    double x = Double.NaN, y = Double.NaN;
    /** Los sprites DIM miran a la izquierda; a la derecha se espejan (convención del proyecto). */
    boolean facingRight = false;
    int frame = 0;

    private LobbyDigimon(String species, String rank, int attribute, int stage, Image[] frames, Image[] fullFrames, Image nameImage) {
        this.species = species;
        this.rank = rank;
        this.attribute = attribute;
        this.stage = stage;
        this.frames = frames;
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
        Image[] lobby = new Image[4]; // IDLE_1, IDLE_2, WALK_1, WALK_2 a la mitad
        for (int i = 0; i < lobby.length; i++) lobby[i] = halfSize(full[i]);
        SpriteData.Sprite name = decode(m.getJSONObject("nameSprite"),
                Protocol.MAX_NAME_SPRITE_WIDTH, Protocol.MAX_NAME_SPRITE_HEIGHT);
        return new LobbyDigimon(m.optString("species", "Digimon"), m.optString("rank", "-"),
                m.optInt("attribute", 0), m.optInt("stage", 2), lobby, full, DimSpriteImageFactory.toNativeImage(name));
    }

    /** Píxeles RGB565 crudos -> sprite, revisando tamaño y largo (el servidor ya lo validó; aquí, otra vez). */
    private static SpriteData.Sprite decode(JSONObject f, int maxW, int maxH) {
        int w = f.getInt("w"), h = f.getInt("h");
        if (w < 1 || h < 1 || w > maxW || h > maxH) throw new IllegalArgumentException("sprite fuera de tamaño");
        byte[] px = Base64.getDecoder().decode(f.getString("px"));
        if (px.length != w * h * 2) throw new IllegalArgumentException("sprite incompleto");
        return SpriteData.Sprite.builder().width(w).height(h).pixelData(px).build();
    }

    /** Reduce a la mitad promediando cada bloque 2x2; transparente si el bloque es mayormente vacío. */
    private static Image halfSize(Image src) {
        int w = (int) src.getWidth() / 2, h = (int) src.getHeight() / 2;
        WritableImage out = new WritableImage(Math.max(1, w), Math.max(1, h));
        PixelReader r = src.getPixelReader();
        PixelWriter pw = out.getPixelWriter();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int opaque = 0, rs = 0, gs = 0, bs = 0;
                for (int dy = 0; dy < 2; dy++)
                    for (int dx = 0; dx < 2; dx++) {
                        int argb = r.getArgb(x * 2 + dx, y * 2 + dy);
                        if ((argb >>> 24) == 0) continue;
                        opaque++;
                        rs += (argb >> 16) & 0xFF;
                        gs += (argb >> 8) & 0xFF;
                        bs += argb & 0xFF;
                    }
                pw.setArgb(x, y, opaque < 2 ? 0 : (0xFF << 24) | ((rs / opaque) << 16) | ((gs / opaque) << 8) | (bs / opaque));
            }
        }
        return out;
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

    /** Avanza hacia el avatar (en sus pies) y elige el cuadro. */
    void follow(double avatarX, double avatarY, long now) {
        if (Double.isNaN(x)) {
            x = avatarX - FOLLOW_DISTANCE;
            y = avatarY;
        }
        double dx = avatarX - x, dy = avatarY - y;
        double dist = Math.hypot(dx, dy);
        double stepX = 0, stepY = 0;
        if (dist > FOLLOW_DISTANCE) {
            double move = (dist - FOLLOW_DISTANCE) * FOLLOW_EASE;
            stepX = dx / dist * move;
            stepY = dy / dist * move;
            x += stepX;
            y += stepY;
        }
        boolean moving = Math.hypot(stepX, stepY) > 0.1;
        if (Math.abs(stepX) > 0.05) facingRight = stepX > 0;
        frame = moving
                ? 2 + (int) ((now / WALK_STEP_NANOS) % 2)
                : (int) ((now / IDLE_STEP_NANOS) % 2);
    }

    Image currentFrame() {
        return frames[frame];
    }
}
