package org.example.online.client;

import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

import java.util.EnumMap;
import java.util.Map;

/**
 * Sprites del avatar humano, en pixel art definido con PLANTILLAS de texto
 * (16x24 px por cuadro) + una PALETA. La forma y los colores van separados
 * a propósito: el editor de personajes futuro (cabello, polo, pantalón,
 * zapatillas; boceto del usuario) solo tendrá que cambiar la paleta.
 *
 * Direcciones: DOWN (de frente), UP (de espaldas) y SIDE, que mira a la
 * IZQUIERDA como los sprites DIM. Para caminar a la derecha el programa lo
 * espeja con scaleX = -1 -- misma convención de orientación de todo el
 * proyecto (derecha = -1, izquierda = 1). Decisión del usuario.
 *
 * Cuadros: 0 = quieto, 1 y 2 = caminar (se alternan con el 0).
 *
 * Símbolos de las plantillas:
 *   .  transparente     o  contorno
 *   h  cabello          H  brillo del cabello
 *   s  piel             e  ojos
 *   t  polo             T  sombra del polo
 *   p  pantalón         P  sombra del pantalón
 *   w  zapatilla        W  sombra de zapatilla
 */
public final class AvatarSprites {

    public static final int WIDTH = 16;
    public static final int HEIGHT = 24;
    public static final int FRAMES = 3;

    public enum Direction { DOWN, UP, SIDE }

    /** Colores del avatar (lo que el editor futuro dejará elegir). */
    public record Palette(Color hair, Color shirt, Color pants, Color shoes) {
        /** Avatar masculino del boceto: cabello castaño, polo gris, jean azul, zapatillas blancas. */
        public static Palette defaultMale() {
            return new Palette(Color.rgb(107, 68, 40), Color.rgb(92, 95, 106),
                    Color.rgb(46, 58, 92), Color.rgb(232, 232, 236));
        }
    }

    // ---------------------------------------------------------------- plantillas

    private static final String[] DOWN_TOP = {
            "....oo.oo.oo....",
            "...ohhohhohho...",
            "..ohhhhhhhhhho..",
            "..ohHhhhhhhHho..",
            ".ohhhhhhhhhhhho.",
            ".ohhhsssssshhho.",
            ".ohssssssssssho.",
            ".ohssessssessho.",
            ".ohssessssessho.",
            "..osssssssssso..",
            "......osso......",
            "....otttttto....",
            "...otttttttto...",
            "..ostTttttTtso..",
            "..ostTttttTtso..",
            "..ostTttttTtso..",
            "...osppppppso...",
    };

    private static final String[] UP_TOP = {
            "....oo.oo.oo....",
            "...ohhohhohho...",
            "..ohhhhhhhhhho..",
            "..ohHhhhhhhHho..",
            ".ohhhhhhhhhhhho.",
            ".ohhhhhhhhhhhho.",
            ".ohhhhhhhhhhhho.",
            ".ohHhhhhhhhhHho.",
            ".ohhhhhhhhhhhho.",
            "..ohhhhhhhhhho..",
            "......osso......",
            "....otttttto....",
            "...otttttttto...",
            "..osttttttttso..",
            "..osttttttttso..",
            "..osTttttttTso..",
            "...osppppppso...",
    };

    /** Piernas de frente/espaldas (las dos direcciones usan las mismas). */
    private static final String[] FRONT_LEGS = {
            "....oppppppo....",
            "....oppooppo....",
            "....oppooppo....",
            "....oPpooPpo....",
            "....oppooppo....",
            "...owwwoowwwo...",
            "...oooooooooo...",
    };

    private static final String[] SIDE_TOP = {
            ".....oo.oo......",
            "....ohhohho.....",
            "...ohhhhhhhho...",
            "..ohhhhhhhhhho..",
            "..ohhHhhhhhhho..",
            ".osshhhhhhhhhho.",
            ".ossshhhhhhhhho.",
            ".osesshhhhhhhho.",
            ".osesssshhhhho..",
            "..ossssssssso...",
            ".....osso.......",
            "....ottttto.....",
            "...otttttto.....",
            "...ottsttto.....",
            "...ottstTto.....",
            "...otTsTtto.....",
            "....opsppo......",
    };

    private static final String[] SIDE_LEGS_STAND = {
            "....oppppo......",
            "....oppppo......",
            "....opPppo......",
            "....oppppo......",
            "....oppppo......",
            "...owwwwwo......",
            "...ooooooo......",
    };

    private static final String[] SIDE_LEGS_STEP = {
            "....oppppo......",
            "...oppoppo......",
            "..oppo.oppo.....",
            "..oPpo..oppo....",
            ".owwo...owwwo...",
            ".ooo.....oooo...",
            "................",
    };

    private AvatarSprites() {}

    /** Todos los cuadros de un avatar, ya coloreados con su paleta. */
    public static Map<Direction, Image[]> build(Palette palette) {
        Map<Direction, Image[]> frames = new EnumMap<>(Direction.class);
        frames.put(Direction.DOWN, frontOrBack(DOWN_TOP, palette));
        frames.put(Direction.UP, frontOrBack(UP_TOP, palette));
        frames.put(Direction.SIDE, new Image[]{
                render(join(SIDE_TOP, SIDE_LEGS_STAND), palette),
                render(join(SIDE_TOP, SIDE_LEGS_STEP), palette),
                render(join(SIDE_TOP, SIDE_LEGS_STEP), palette),
        });
        return frames;
    }

    private static Image[] frontOrBack(String[] top, Palette palette) {
        String[] stand = join(top, FRONT_LEGS);
        return new Image[]{
                render(stand, palette),
                render(raiseLeg(stand, true), palette),   // pierna izquierda (de la pantalla) arriba
                render(raiseLeg(stand, false), palette),  // pierna derecha arriba
        };
    }

    private static String[] join(String[] top, String[] legs) {
        String[] all = new String[top.length + legs.length];
        System.arraycopy(top, 0, all, 0, top.length);
        System.arraycopy(legs, 0, all, top.length, legs.length);
        if (all.length != HEIGHT) throw new IllegalStateException("La plantilla debe medir " + HEIGHT + " filas");
        return all;
    }

    /**
     * Paso al caminar de frente/espaldas: sube una pierna 1 píxel (la zona
     * de piernas, filas 17-23, de una mitad del sprite).
     */
    private static String[] raiseLeg(String[] rows, boolean leftHalf) {
        String[] out = rows.clone();
        int legStart = HEIGHT - FRONT_LEGS.length;
        for (int y = legStart; y < HEIGHT; y++) {
            char[] line = out[y].toCharArray();
            String below = y + 1 < HEIGHT ? rows[y + 1] : "................";
            for (int x = 0; x < WIDTH; x++) {
                boolean inHalf = leftHalf ? x < 8 : x >= 8;
                if (inHalf) line[x] = below.charAt(x);
            }
            out[y] = new String(line);
        }
        return out;
    }

    private static Image render(String[] rows, Palette palette) {
        WritableImage img = new WritableImage(WIDTH, HEIGHT);
        PixelWriter w = img.getPixelWriter();
        for (int y = 0; y < HEIGHT; y++) {
            if (rows[y].length() != WIDTH) throw new IllegalStateException("Fila " + y + " no mide " + WIDTH + ": " + rows[y]);
            for (int x = 0; x < WIDTH; x++) {
                Color c = colorFor(rows[y].charAt(x), palette);
                w.setColor(x, y, c == null ? Color.TRANSPARENT : c);
            }
        }
        return img;
    }

    private static Color colorFor(char symbol, Palette p) {
        return switch (symbol) {
            case 'o' -> Color.rgb(42, 30, 30);
            case 'h' -> p.hair();
            case 'H' -> p.hair().brighter();
            case 's' -> Color.rgb(242, 201, 160);
            case 'e' -> Color.rgb(43, 43, 58);
            case 't' -> p.shirt();
            case 'T' -> p.shirt().darker();
            case 'p' -> p.pants();
            case 'P' -> p.pants().darker();
            case 'w' -> p.shoes();
            case 'W' -> p.shoes().darker();
            default -> null;
        };
    }
}
