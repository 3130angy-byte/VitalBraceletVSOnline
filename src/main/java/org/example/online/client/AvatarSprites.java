package org.example.online.client;

import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;

/**
 * Avatar humano de la sala: adolescente en pixel art 16 bits (referencia y
 * especificación del usuario, 2026-10-02): cabello oscuro despeinado, casaca
 * azul abierta sobre polera blanca, pantalón gris y zapatillas grises con
 * suela blanca.
 *
 * Se CARGA de la hoja {@code /avatar/adolescente.png}, que genera
 * {@code tools/GenerarAvatar.java}: una fila de celdas de 64x64, fondo
 * transparente, solo colores de la paleta de la especificación. Cuadro 0 =
 * quieto (el único sprite bien dibujado de la referencia: el de perfil,
 * decisión del usuario); 1..8 = caminata generada a partir de él.
 *
 * Solo DE LADO (como los Digimon): el dibujo mira a la IZQUIERDA y a la
 * derecha se espeja con scaleX = -1 (convención del proyecto).
 *
 * Tamaño: la celda de 64x64 mide la mitad en el mundo de la sala (32x32):
 * con la cámara x2 se ve 1:1, igual que los Digimon.
 */
public final class AvatarSprites {

    private static final String SHEET = "/avatar/adolescente.png";
    private static final int CELL = 64;
    /** Fila de la celda donde apoyan las suelas (la hoja se generó así). */
    private static final int FEET_ROW = 61;

    /** Tamaño del cuadro en el MUNDO (la imagen mide el doble). */
    public static final double WIDTH = CELL / 2.0, HEIGHT = CELL / 2.0;
    /** Desde arriba del cuadro hasta el borde de abajo de las suelas, en el mundo. */
    public static final double FEET = (FEET_ROW + 1) / 2.0;
    /** Cuadros de la caminata (la hoja trae el quieto y estos). */
    public static final int WALK_FRAMES = 8;

    /** Cuadros mirando a la izquierda: 0 = quieto, 1.. = caminata. */
    private final Image[] frames;
    /** Desde arriba del cuadro hasta el píxel más alto del cabello (en el mundo), para ubicar el nombre. */
    private final double headTop;

    private AvatarSprites(Image[] frames, double headTop) {
        this.frames = frames;
        this.headTop = headTop;
    }

    public static AvatarSprites load() {
        Image sheet = new Image(AvatarSprites.class.getResourceAsStream(SHEET));
        if (sheet.isError()) throw new IllegalStateException("No se pudo leer " + SHEET, sheet.getException());
        PixelReader reader = sheet.getPixelReader();
        if (sheet.getWidth() != (1 + WALK_FRAMES) * CELL) throw new IllegalStateException(SHEET + ": se esperaban " + (1 + WALK_FRAMES) + " cuadros");
        Image[] frames = new Image[1 + WALK_FRAMES];
        int top = CELL;
        for (int i = 0; i < frames.length; i++) {
            frames[i] = new WritableImage(reader, i * CELL, 0, CELL, CELL);
            top = Math.min(top, firstOpaqueRow(frames[i]));
        }
        return new AvatarSprites(frames, top / 2.0);
    }

    /** Cuadro 0 = quieto; 1..WALK_FRAMES = caminata. Mira a la izquierda. */
    public Image frame(int frame) {
        return frames[frame];
    }

    public double headTop() {
        return headTop;
    }

    private static int firstOpaqueRow(Image frame) {
        PixelReader r = frame.getPixelReader();
        for (int y = 0; y < CELL; y++)
            for (int x = 0; x < CELL; x++) if ((r.getArgb(x, y) >>> 24) != 0) return y;
        return CELL;
    }
}
