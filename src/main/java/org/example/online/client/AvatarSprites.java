package org.example.online.client;

import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

/**
 * Avatar humano de la sala, de PERFIL y con proporciones realistas
 * (referencias del usuario, 2026-10-01): cabeza chica, cabello negro
 * despeinado, chaqueta azul abierta sobre polera blanca, pantalón negro y
 * zapatillas oscuras con suela blanca. Caminata de 6 cuadros.
 *
 * Solo DE LADO (decisión del usuario, como los Digimon): el dibujo mira a la
 * IZQUIERDA y a la derecha se espeja con scaleX = -1 (convención del
 * proyecto). No hay vistas de frente ni de espaldas.
 *
 * Se dibuja POR CAPAS con figuras simples (elipses, polígonos, segmentos
 * gruesos) y cada capa recibe su propio CONTORNO automático antes de
 * apilarse: así la pierna y el brazo de adelante quedan separados del
 * cuerpo por una línea, como en el pixel art hecho a mano. Las
 * extremidades de atrás van más oscuras. La caminata sale de los ángulos
 * de cadera, rodilla y hombro de cada cuadro (y el cuerpo sube y baja
 * solo: siempre apoya el pie más bajo en el suelo).
 *
 * Tamaño: 36x60 píxeles, que en el mundo de la sala miden la mitad
 * (18x30): con la cámara x2 se ven 1:1, igual que los Digimon. Los colores
 * van en la Palette (el editor de personajes futuro solo la cambiará).
 */
public final class AvatarSprites {

    /** Tamaño en el MUNDO (la imagen mide el doble). */
    public static final int WIDTH = 18;
    public static final int HEIGHT = 30;
    /** Píxeles de la imagen. */
    public static final int PX_W = 36, PX_H = 60;
    /** Cuadro 0 = quieto; 1..6 = caminata. */
    public static final int WALK_FRAMES = 6;
    public static final int FRAMES = 1 + WALK_FRAMES;

    /** Colores del avatar (lo que el editor futuro dejará elegir). */
    public record Palette(Color hair, Color jacket, Color shirt, Color pants, Color shoes) {
        /** Urbano: cabello negro, chaqueta azul, polera blanca, pantalón negro, zapatillas oscuras. */
        public static Palette defaultUrban() {
            return new Palette(Color.rgb(30, 28, 36), Color.rgb(40, 74, 138), Color.rgb(236, 238, 242),
                    Color.rgb(28, 28, 34), Color.rgb(44, 44, 54));
        }
    }

    private static final int OUTLINE = argb(16, 14, 22);
    private static final int SKIN = argb(240, 196, 160), SKIN_SHADE = argb(210, 156, 124);
    private static final int EYE = argb(30, 24, 34), MOUTH = argb(168, 104, 92), SOLE = argb(232, 232, 238);

    /** Caminata (cuadros 1..6) de UNA pierna: {ángulo del muslo, flexión de rodilla} en grados; + = hacia adelante. La otra va 3 cuadros desfasada. */
    private static final double[][] LEG_CYCLE = {
            {24, 2}, {13, 14}, {0, 6}, {-20, 16}, {-12, 44}, {11, 48},
    };
    private static final double THIGH = 12, SHIN = 11;

    private AvatarSprites() {}

    /** Los cuadros del avatar mirando a la IZQUIERDA (0 quieto, 1..6 caminando). */
    public static Image[] build(Palette palette) {
        Image[] frames = new Image[FRAMES];
        frames[0] = render(palette, new double[]{4, 2}, new double[]{-3, 3});
        for (int i = 0; i < WALK_FRAMES; i++) {
            frames[i + 1] = render(palette, LEG_CYCLE[i], LEG_CYCLE[(i + 3) % WALK_FRAMES]);
        }
        return frames;
    }

    // ---------------------------------------------------------------- un cuadro

    private static Image render(Palette p, double[] front, double[] back) {
        int jacket = argb(p.jacket()), jacketShade = argb(p.jacket().darker()), jacketLight = argb(p.jacket().brighter());
        int shirt = argb(p.shirt()), shirtShade = argb(p.shirt().deriveColor(0, 1, 0.82, 1));
        int pants = argb(p.pants()), pantsLight = argb(p.pants().brighter());
        int shoes = argb(p.shoes()), shoesLight = argb(p.shoes().brighter());
        int hair = argb(p.hair()), hairLight = argb(p.hair().brighter().brighter());

        // El cuerpo baja hasta que el pie más bajo toca el suelo (y a píxel entero: la cara no "tiembla").
        double[] fa = legPoints(front), fb = legPoints(back);
        double ankleGround = PX_H - 4;
        double hipY = Math.round(ankleGround - Math.max(fa[3], fb[3]));
        double hipX = 18;

        int[] canvas = new int[PX_W * PX_H];

        // 1) brazo de atrás
        stack(canvas, arm(hipX + 2, hipY - 15, -back[0] * 1.2, dim(jacket), dim(jacketShade), dim(SKIN)));
        // 2) pierna de atrás
        stack(canvas, leg(hipX + 1.5, hipY + 1, back, dim(pants), dim(pantsLight), dim(shoes), dim(shoesLight), dim(SOLE)));
        // 3) torso: chaqueta abierta sobre polera blanca
        int[] body = layer();
        rect(body, hipX - 5, hipY - 1, 10, 4, pants);                                   // caderas
        poly(body, new double[]{hipX - 5, hipX + 4, hipX + 6, hipX + 6, hipX + 5, hipX - 5, hipX - 6, hipX - 6},
                new double[]{hipY - 17, hipY - 17, hipY - 13, hipY - 1, hipY + 1, hipY + 1, hipY - 4, hipY - 13}, jacketShade);
        poly(body, new double[]{hipX - 5, hipX + 3, hipX + 4.5, hipX + 4.5, hipX - 5, hipX - 6, hipX - 6},
                new double[]{hipY - 17, hipY - 17, hipY - 13, hipY - 1, hipY - 1, hipY - 4, hipY - 13}, jacket);
        poly(body, new double[]{hipX - 5, hipX - 2, hipX - 2.5, hipX - 6, hipX - 6},
                new double[]{hipY - 16, hipY - 16, hipY - 2, hipY - 2, hipY - 12}, shirt);    // polera
        rect(body, hipX - 6, hipY - 7, 1, 5, shirtShade);
        line(body, hipX - 2, hipY - 16, hipX - 2.5, hipY - 2, 0.7, jacketLight);       // borde de la chaqueta abierta
        rect(body, hipX - 5, hipY - 2, 10, 2, jacketShade);                            // pretina (bomber)
        rect(body, hipX - 6, hipY - 2, 4, 2, shirt);                                   // la polera asoma
        poly(body, new double[]{hipX - 1, hipX + 4, hipX + 5, hipX + 1}, new double[]{hipY - 19, hipY - 18.5, hipY - 16, hipY - 16}, jacketLight); // cuello
        rect(body, hipX - 2.5, hipY - 20, 3, 4, SKIN_SHADE);                           // cuello (piel)
        stack(canvas, body);
        // 4) pierna de adelante
        stack(canvas, leg(hipX - 1, hipY + 1, front, pants, pantsLight, shoes, shoesLight, SOLE));
        // 5) cabeza
        stack(canvas, head(hipX - 1.5, hipY - 26, hair, hairLight));
        // 6) brazo de adelante (encima de todo)
        stack(canvas, arm(hipX - 0.5, hipY - 15, -front[0] * 1.2, jacket, jacketShade, SKIN));

        WritableImage img = new WritableImage(PX_W, PX_H);
        PixelWriter w = img.getPixelWriter();
        for (int y = 0; y < PX_H; y++)
            for (int x = 0; x < PX_W; x++) w.setArgb(x, y, canvas[y * PX_W + x]);
        return img;
    }

    /** {kneeX, kneeY, ankleX, ankleY} relativos a la cadera. */
    private static double[] legPoints(double[] angles) {
        double a = Math.toRadians(angles[0]), s = Math.toRadians(angles[0] - angles[1]);
        double kx = -Math.sin(a) * THIGH, ky = Math.cos(a) * THIGH;
        double ax = kx - Math.sin(s) * SHIN, ay = ky + Math.cos(s) * SHIN;
        return new double[]{kx, ky, ax, ay};
    }

    private static int[] leg(double hx, double hy, double[] angles, int pants, int light, int shoe, int shoeLight, int sole) {
        int[] l = layer();
        double[] k = legPoints(angles);
        double kx = hx + k[0], ky = hy + k[1], ax = hx + k[2], ay = hy + k[3] - 1;
        line(l, hx, hy, kx, ky, 2.8, pants);
        line(l, kx, ky, ax, ay, 2.3, pants);
        line(l, hx - 1.4, hy, kx - 1.4, ky, 0.5, light);           // brillo del pantalón (lado de adelante)
        // zapatilla oscura con suela blanca (mira a la izquierda)
        rect(l, ax - 5.5, ay - 1, 8.5, 3, shoe);
        rect(l, ax - 6.5, ay, 1, 2, shoe);
        rect(l, ax - 4.5, ay - 1, 3, 1, shoeLight);
        rect(l, ax - 6.5, ay + 2, 10, 1.5, sole);
        return l;
    }

    private static int[] arm(double sx, double sy, double angleDeg, int sleeve, int shade, int skin) {
        int[] l = layer();
        double a = Math.toRadians(angleDeg), f = Math.toRadians(angleDeg + 26);
        double ex = sx - Math.sin(a) * 8, ey = sy + Math.cos(a) * 8;
        double hx = ex - Math.sin(f) * 6.5, hy = ey + Math.cos(f) * 6.5;
        line(l, sx, sy, ex, ey, 2.4, sleeve);
        line(l, ex, ey, hx, hy, 2.1, sleeve);
        line(l, sx + 1.2, sy + 0.5, ex + 1.2, ey, 0.6, shade);      // sombra de la manga
        double cx = ex + (hx - ex) * 0.85, cy = ey + (hy - ey) * 0.85;
        line(l, cx, cy, hx, hy, 2.2, shade);                          // puño de la manga
        ellipse(l, hx - Math.sin(f) * 1.5, hy + Math.cos(f) * 1.5, 2.1, 2.1, skin); // mano
        return l;
    }

    /** Cabeza de perfil, proporción realista (mira a la izquierda): cabello negro despeinado, nariz, ojo pequeño, oreja. */
    private static int[] head(double hx, double hy, int hair, int light) {
        int[] l = layer();
        double k = HEAD_SCALE;
        // cráneo y cara
        ellipse(l, hx + 1 * k, hy - 0.5 * k, 5.4 * k, 5.4 * k, hair);
        ellipse(l, hx - 1 * k, hy + 1 * k, 4.4 * k, 4.9 * k, SKIN);
        poly(l, xs(hx, k, -4.8, -4.2, -1, 2.5, 2.5), ys(hy, k, 1, 4.8, 6.2, 5, 0), SKIN);
        rect(l, hx - 6 * k, hy + 1 * k, 1.5, 2, SKIN);                         // nariz
        rect(l, hx, hy + 4 * k, 2.5, 1.5, SKIN_SHADE);                       // sombra bajo la mandíbula
        // cabello despeinado (sin puntas largas): arriba y atrás, con mechones cortos
        poly(l, xs(hx, k, -5.5, -4.5, -2, 1, 4, 6.5, 7, 6, 4.5, 3, 2.2, 0.5, -2, -3.5, -5.5),
                ys(hy, k, -2, -5, -6.5, -7, -6, -4, -1, 2, 4, 5, 1, -1, -2.2, -0.8, -0.2), hair);
        double[][] tufts = {
                {-2, -6.2, -0.9, -8.4, 1, -6.8}, {2, -6.8, 4.3, -8, 4.8, -5.8}, {5.5, -4.5, 8, -3.5, 6.8, -1},
                {6.5, 0, 8.2, 1.8, 6, 2.5}, {4.5, 3.5, 5.8, 6, 3, 5}, {-5.8, -2.5, -6.8, 0.3, -4.2, -1.2},
        };
        for (double[] t : tufts) poly(l, xs(hx, k, t[0], t[2], t[4]), ys(hy, k, t[1], t[3], t[5]), hair);
        line(l, hx - 2 * k, hy - 5.5 * k, hx + 3 * k, hy - 6.2 * k, 0.45, light);   // brillo del cabello
        line(l, hx + 4.5 * k, hy - 3.5 * k, hx + 6 * k, hy - 1 * k, 0.45, light);
        // oreja, ojo, ceja y boca
        ellipse(l, hx + 2 * k, hy + 1.5 * k, 1.1 * k, 1.6 * k, SKIN);
        rect(l, hx + 2 * k, hy + 1 * k, 1, 1, SKIN_SHADE);
        rect(l, hx - 4 * k, hy + 0.5 * k, 1, 2, EYE);
        rect(l, hx - 4.5 * k, hy - 0.5 * k, 2, 1, hair);
        rect(l, hx - 4.5 * k, hy + 3.5 * k, 1.5, 1, MOUTH);
        return l;
    }

    /** Cabeza un poco más grande que la medida "real": a este tamaño, si no, la cara casi no se lee. */
    private static final double HEAD_SCALE = 1.15;

    private static double[] xs(double base, double k, double... d) {
        double[] out = new double[d.length];
        for (int i = 0; i < d.length; i++) out[i] = base + d[i] * k;
        return out;
    }

    private static double[] ys(double base, double k, double... d) {
        return xs(base, k, d);
    }

    // ---------------------------------------------------------------- dibujo en píxeles

    private static int[] layer() {
        return new int[PX_W * PX_H];
    }

    /** Agrega el contorno de la capa (píxeles vacíos junto a uno lleno) y la apila encima. */
    private static void stack(int[] canvas, int[] layer) {
        int[] outlined = layer.clone();
        for (int y = 0; y < PX_H; y++) {
            for (int x = 0; x < PX_W; x++) {
                if (layer[y * PX_W + x] != 0) continue;
                if (filled(layer, x - 1, y) || filled(layer, x + 1, y) || filled(layer, x, y - 1) || filled(layer, x, y + 1)) {
                    outlined[y * PX_W + x] = OUTLINE;
                }
            }
        }
        for (int i = 0; i < canvas.length; i++) if (outlined[i] != 0) canvas[i] = outlined[i];
    }

    private static boolean filled(int[] l, int x, int y) {
        return x >= 0 && y >= 0 && x < PX_W && y < PX_H && l[y * PX_W + x] != 0;
    }

    private static void put(int[] l, int x, int y, int c) {
        if (x >= 0 && y >= 0 && x < PX_W && y < PX_H) l[y * PX_W + x] = c;
    }

    private static void rect(int[] l, double x, double y, double w, double h, int c) {
        for (int py = (int) Math.round(y); py < Math.round(y + h); py++)
            for (int px = (int) Math.round(x); px < Math.round(x + w); px++) put(l, px, py, c);
    }

    private static void ellipse(int[] l, double cx, double cy, double rx, double ry, int c) {
        for (int y = 0; y < PX_H; y++)
            for (int x = 0; x < PX_W; x++) {
                double dx = (x + 0.5 - cx) / rx, dy = (y + 0.5 - cy) / ry;
                if (dx * dx + dy * dy <= 1) put(l, x, y, c);
            }
    }

    /** Segmento grueso: todo píxel cuyo centro está a menos de r del segmento. */
    private static void line(int[] l, double x0, double y0, double x1, double y1, double r, int c) {
        double vx = x1 - x0, vy = y1 - y0, len2 = vx * vx + vy * vy;
        for (int y = 0; y < PX_H; y++)
            for (int x = 0; x < PX_W; x++) {
                double px = x + 0.5, py = y + 0.5;
                double t = len2 == 0 ? 0 : Math.max(0, Math.min(1, ((px - x0) * vx + (py - y0) * vy) / len2));
                double dx = px - (x0 + vx * t), dy = py - (y0 + vy * t);
                if (dx * dx + dy * dy <= r * r) put(l, x, y, c);
            }
    }

    /** Polígono relleno (regla par-impar sobre el centro de cada píxel). */
    private static void poly(int[] l, double[] xs, double[] ys, int c) {
        for (int y = 0; y < PX_H; y++)
            for (int x = 0; x < PX_W; x++) {
                double px = x + 0.5, py = y + 0.5;
                boolean in = false;
                for (int i = 0, j = xs.length - 1; i < xs.length; j = i++) {
                    if ((ys[i] > py) != (ys[j] > py) && px < (xs[j] - xs[i]) * (py - ys[i]) / (ys[j] - ys[i]) + xs[i]) in = !in;
                }
                if (in) put(l, x, y, c);
            }
    }

    /** Extremidades de atrás: más oscuras (están más lejos). */
    private static int dim(int c) {
        int r = (c >> 16) & 0xff, g = (c >> 8) & 0xff, b = c & 0xff;
        return argb((int) (r * 0.72), (int) (g * 0.72), (int) (b * 0.72));
    }

    private static int argb(int r, int g, int b) {
        return 0xff000000 | (r << 16) | (g << 8) | b;
    }

    private static int argb(Color c) {
        return argb((int) Math.round(c.getRed() * 255), (int) Math.round(c.getGreen() * 255), (int) Math.round(c.getBlue() * 255));
    }
}
