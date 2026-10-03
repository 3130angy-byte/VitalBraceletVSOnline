// Uso (desde la carpeta del proyecto):
//   java tools/GenerarAvatar.java referencia.png src/main/resources/avatar/adolescente.png [vista.png]
// La referencia guardada es tools/avatar/referencia-adolescente.webp y Java no lee .webp: primero
// pasarla a PNG CON transparencia (Paint la pierde). En PowerShell, con el códec de Windows:
//   Add-Type -AssemblyName PresentationCore; $d = [Windows.Media.Imaging.BitmapDecoder]::Create([IO.File]::OpenRead("$PWD\tools\avatar\referencia-adolescente.webp"), 'PreservePixelFormat', 'OnLoad'); $e = New-Object Windows.Media.Imaging.PngBitmapEncoder; $e.Frames.Add($d.Frames[0]); $f = [IO.File]::Create("$PWD\referencia.png"); $e.Save($f); $f.Close()
//
// La referencia trae 4 filas x 7 cuadros, pero solo UNO está bien dibujado (decisión del usuario,
// 2026-10-02): el QUIETO DE PERFIL (2.ª fila, 1.º de la izquierda; mira a la izquierda). De ese se
// saca el avatar y la caminata se GENERA aquí: cabeza y casaca quedan idénticas (bajan 1 px al
// apoyar el pie) y se redibujan piernas, zapatillas y mano. Resultado: una fila de 64x64, cuadro
// 0 = quieto (el original tal cual) y 1..8 = caminata; fondo transparente y solo colores de la
// paleta de la especificación. "vista.png" (opcional) = la hoja agrandada x4 para revisarla.
import java.awt.image.*; import javax.imageio.*; import java.io.*; import java.util.*;
/** Saca el avatar quieto de la referencia (a 64x64, con la paleta de la especificación) y le genera la caminata. */
public class GenerarAvatar {
  static final int CELL = 64, FEET_ROW = 61, ROWS = 4, COLS = 7;
  /** El sprite de la referencia que se usa: 2.ª fila (perfil izquierdo), 1.º cuadro (quieto). */
  static final int SOURCE_ROW = 1, SOURCE_COL = 0;
  /** Paleta de la especificación (punto 18): cabello, piel, polera, casaca, pantalón, zapatillas, contorno. */
  static final int[] PALETTE = {
      0x151826, 0x222638, 0x303548,               // cabello
      0xE7A776, 0xC97E5D, 0xF0BB8B,               // piel
      0xFFFFFF, 0xD9D9D9,                         // polera (y suela)
      0x142C68, 0x1D3F8A, 0x2855A5, 0x3769BA,     // casaca
      0x30343D, 0x464B55, 0x5B616B,               // pantalón
      0x454A53, 0x656B74,                         // zapatillas
      0x10131C,                                   // contorno
  };
  /** Tamaño aproximado del píxel del dibujo en la referencia (solo guía: los cortes se buscan). */
  static final double PITCH_X = 5.0;

  public static void main(String[] a) throws Exception {
    int[] idle = extract(ImageIO.read(new File(a[0])));
    int n = Walk.CYCLE.length;
    BufferedImage sheet = new BufferedImage((1 + n) * CELL, CELL, BufferedImage.TYPE_INT_ARGB);
    put(sheet, 0, idle);
    for (int k = 0; k < n; k++) put(sheet, k + 1, Walk.frame(idle, Walk.CYCLE[k], Walk.CYCLE[(k + n / 2) % n]));
    new File(a[1]).getAbsoluteFile().getParentFile().mkdirs();
    ImageIO.write(sheet, "png", new File(a[1]));
    if (a.length > 2) ImageIO.write(preview(sheet, 4), "png", new File(a[2]));
  }

  static void put(BufferedImage sheet, int i, int[] px) {
    for (int y = 0; y < CELL; y++) for (int x = 0; x < CELL; x++) sheet.setRGB(i * CELL + x, y, px[y * CELL + x]);
  }

  // ================================================================ caminata

  /**
   * Caminata de perfil (mira a la izquierda) a partir del quieto. Las medidas (cadera, última fila de
   * la casaca, mano) son las de ESE sprite tal como lo ubica extract(): si cambia la referencia,
   * revisarlas con la vista.
   */
  static final class Walk {
    static final int O = argb(0x10131C);
    static final int SKIN = argb(0xE7A776), SKIN_SH = argb(0xC97E5D), SKIN_LT = argb(0xF0BB8B);
    static final int J_DARK = argb(0x142C68);
    static final int P_BASE = argb(0x464B55), P_LIGHT = argb(0x5B616B);
    static final int SH_DARK = argb(0x454A53), SH_BASE = argb(0x656B74), SOLE = argb(0xFFFFFF), SOLE_SH = argb(0xD9D9D9);
    /** La pierna de atrás va más oscura (los mismos tonos que tiene en el quieto). */
    static final int FAR_MAIN = argb(0x303548), FAR_EDGE = argb(0x222638);

    /** Fila de la suela del pie apoyado (el contorno queda en FEET_ROW). */
    static final int GROUND = FEET_ROW - 1;
    /** Última fila de la casaca: de ahí para arriba el quieto se copia tal cual. */
    static final int TORSO_LAST = 45;
    /** Cadera con la pierna recta; mano (esquina de arriba a la izquierda) en el quieto. */
    static final double HIP_X = 31.5, HIP_Y = 44;
    static final int HAND_X = 33, HAND_Y = 46;
    static final double THIGH = 7, SHIN = 7.5, R_THIGH = 2.3, R_SHIN = 2.1;

    /**
     * Por cuadro: {muslo, flexión de rodilla, inclinación del pie} en grados. Muslo + = hacia adelante
     * (izquierda); pie + = punta hacia abajo. La pierna de atrás va medio ciclo desfasada. Orden:
     * contacto, apoyo, paso, impulso, despegue, pierna arriba, cruce, estirar hacia adelante.
     */
    static final double[][] CYCLE = {
        {26, 2, 0}, {16, 8, 0}, {4, 4, 0}, {-10, 2, 0}, {-22, 14, 22}, {-16, 40, 50}, {6, 54, 40}, {22, 26, 8},
    };

    static int[] frame(int[] idle, double[] near, double[] far) {
      double[] pn = legPoints(near), pf = legPoints(far);
      // El cuerpo baja hasta que el pie más bajo toca el suelo (con las piernas abiertas, 1 px).
      int hipY = (int) Math.round(GROUND - 2 - Math.max(pn[3], pf[3]));
      int dy = hipY - (int) HIP_Y;
      int[] canvas = new int[CELL * CELL];
      stack(canvas, onGround(leg(HIP_X + 1, hipY, pf, far[2], FAR_MAIN, FAR_EDGE, true)));
      stack(canvas, onGround(leg(HIP_X - 0.5, hipY, pn, near[2], P_LIGHT, P_BASE, false)));
      // cabeza y casaca: el quieto tal cual (sin piernas ni mano), con el contorno del borde de la casaca
      for (int y = 0; y <= TORSO_LAST; y++) for (int x = 0; x < CELL; x++)
        if (idle[y * CELL + x] != 0) canvas[(y + dy) * CELL + x] = idle[y * CELL + x];
      for (int x = 0; x < CELL; x++) if (idle[TORSO_LAST * CELL + x] != 0) canvas[(TORSO_LAST + 1 + dy) * CELL + x] = O;
      // la mano se balancea al revés que la pierna de adelante
      int handDx = (int) Math.round(2.2 * near[0] / 26.0);
      hand(canvas, HAND_X + handDx, HAND_Y + dy - (Math.abs(handDx) >= 2 ? 1 : 0));
      return canvas;
    }

    /** {rodilla x, rodilla y, tobillo x, tobillo y} relativos a la cadera. */
    static double[] legPoints(double[] ang) {
      double t = Math.toRadians(ang[0]), s = Math.toRadians(ang[0] - ang[1]);
      double kx = -Math.sin(t) * THIGH, ky = Math.cos(t) * THIGH;
      return new double[]{kx, ky, kx - Math.sin(s) * SHIN, ky + Math.cos(s) * SHIN};
    }

    static int[] leg(double hx, double hy, double[] p, double footDeg, int main, int edge, boolean far) {
      int[] l = new int[CELL * CELL];
      double kx = hx + p[0], ky = hy + p[1], ax = hx + p[2], ay = hy + p[3];
      shoe(l, ax, ay, footDeg, far);
      segment(l, hx, hy, kx, ky, R_THIGH, main, edge);
      segment(l, kx, ky, ax, ay, R_SHIN, main, edge);
      return l;
    }

    /** Si la punta del pie quedó bajo el suelo, sube la pierna (lo de arriba del muslo lo tapa la casaca). */
    static int[] onGround(int[] l) {
      int low = -1;
      for (int i = 0; i < l.length; i++) if (l[i] != 0) low = i / CELL;
      int up = low - GROUND;
      if (up <= 0) return l;
      int[] out = new int[CELL * CELL];
      System.arraycopy(l, up * CELL, out, 0, (CELL - up) * CELL);
      return out;
    }

    /**
     * Zapatilla con la punta a la izquierda, girada "deg" (punta hacia abajo); suela blanca. En
     * coordenadas del pie: u = hacia el talón, v = hacia abajo, tobillo = (0, 0).
     */
    static void shoe(int[] l, double ax, double ay, double deg, boolean far) {
      double c = Math.cos(Math.toRadians(deg)), s = Math.sin(Math.toRadians(deg));
      int upper = far ? SH_DARK : SH_BASE, sole = far ? SOLE_SH : SOLE;
      for (int y = 0; y < CELL; y++) for (int x = 0; x < CELL; x++) {
        double dx = x + 0.5 - ax, dy = y + 0.5 - ay;
        double u = c * dx - s * dy, v = s * dx + c * dy;
        boolean collar = u >= -2.5 && u <= 2.0 && v >= -1.5 && v < 0.5;
        boolean body = u >= -5.6 && u <= 2.6 && v >= 0.5 && v < 1.6 && !(u < -4.6 && v < 1.0);
        boolean soleRow = u >= -6.0 && u <= 2.6 && v >= 1.6 && v < 2.7;
        if (soleRow) l[y * CELL + x] = sole;
        else if (body || collar) l[y * CELL + x] = u > 1.6 && v >= 0.5 ? SH_DARK : upper;
      }
    }

    /** Segmento grueso; el lado de atrás (derecha, lejos de la luz) con el tono de sombra. */
    static void segment(int[] l, double x0, double y0, double x1, double y1, double r, int main, int edge) {
      double vx = x1 - x0, vy = y1 - y0, len = Math.sqrt(vx * vx + vy * vy);
      for (int y = 0; y < CELL; y++) for (int x = 0; x < CELL; x++) {
        double px = x + 0.5, py = y + 0.5;
        double t = len == 0 ? 0 : Math.max(0, Math.min(1, ((px - x0) * vx + (py - y0) * vy) / (len * len)));
        double dx = px - (x0 + vx * t), dy = py - (y0 + vy * t);
        if (dx * dx + dy * dy <= r * r) l[y * CELL + x] = len > 0 && (dx * vy - dy * vx) / len > r - 1.2 ? edge : main;
      }
    }

    static void hand(int[] canvas, int x, int y) {
      int[] l = new int[CELL * CELL];
      for (int i = 0; i < 3; i++) set(l, x + i, y - 1, J_DARK); // puño de la manga
      set(l, x, y, SKIN_LT); set(l, x + 1, y, SKIN); set(l, x + 2, y, SKIN_SH);
      set(l, x, y + 1, SKIN); set(l, x + 1, y + 1, SKIN_SH); set(l, x + 2, y + 1, SKIN_SH);
      set(l, x + 1, y + 2, SKIN_SH);
      stack(canvas, l);
    }

    static void set(int[] l, int x, int y, int c) {
      if (x >= 0 && y >= 0 && x < CELL && y < CELL) l[y * CELL + x] = c;
    }

    /** Agrega el contorno de la capa (vacíos junto a un lleno) y la apila encima. */
    static void stack(int[] canvas, int[] layer) {
      int[] out = layer.clone();
      for (int y = 0; y < CELL; y++) for (int x = 0; x < CELL; x++)
        if (layer[y * CELL + x] == 0 && (filled(layer, x - 1, y) || filled(layer, x + 1, y) || filled(layer, x, y - 1) || filled(layer, x, y + 1)))
          out[y * CELL + x] = O;
      for (int i = 0; i < canvas.length; i++) if (out[i] != 0) canvas[i] = out[i];
    }

    static boolean filled(int[] l, int x, int y) {
      return x >= 0 && y >= 0 && x < CELL && y < CELL && l[y * CELL + x] != 0;
    }
  }

  // ================================================================ extracción del quieto

  /**
   * El sprite elegido a resolución nativa, con la paleta, ubicado en una celda de 64x64: suelas en
   * FEET_ROW y el centro de la CABEZA en el centro de la celda.
   */
  static int[] extract(BufferedImage ref) {
    int W = ref.getWidth(), H = ref.getHeight();
    List<int[]> rowBands = bands(ref, true), colBands = bands(ref, false);
    if (rowBands.size() != ROWS || colBands.size() != COLS)
      throw new IllegalStateException("Se esperaban " + ROWS + "x" + COLS + " sprites y hay " + rowBands.size() + "x" + colBands.size());
    // Vertical: la referencia tiene UNA cuadrícula regular para toda la imagen. Horizontal: se desvía
    // de un sprite a otro, así que los cortes se buscan en el sprite.
    double[] gy = new double[H];
    for (int y = 1; y < H; y++) for (int x = 0; x < W; x++) gy[y] += diff(ref.getRGB(x, y), ref.getRGB(x, y - 1));
    double[] grid = comb(gy, 4.0, 7.0);
    System.out.printf("Cuadrícula vertical: %.3f px, desfase %.2f%n", grid[0], grid[1]);

    int x0 = colBands.get(SOURCE_COL)[0] - 6, x1 = colBands.get(SOURCE_COL)[1] + 6;
    int y0 = rowBands.get(SOURCE_ROW)[0] - 6, y1 = rowBands.get(SOURCE_ROW)[1] + 6;
    double[] gx = new double[x1 - x0];
    for (int y = y0; y < y1; y++) for (int x = x0 + 1; x < x1; x++) gx[x - x0] += diff(ref.getRGB(x, y), ref.getRGB(x - 1, y));
    int[] cx = cuts(gx, PITCH_X, 0.3);
    List<Integer> cy = new ArrayList<>();
    for (double p = grid[1] + Math.ceil((y0 - grid[1]) / grid[0]) * grid[0]; p < y1; p += grid[0]) cy.add((int) Math.round(p));
    int w = cx.length - 1, h = cy.size() - 1;
    int[] px = new int[w * h];
    for (int j = 0; j < h; j++)
      for (int i = 0; i < w; i++) px[j * w + i] = quantize(sample(ref, x0 + cx[i], x0 + cx[i + 1], cy.get(j), cy.get(j + 1)));

    int minX = w, maxX = -1, minY = h, maxY = -1;
    for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) if (px[y * w + x] != 0) {
      minX = Math.min(minX, x); maxX = Math.max(maxX, x); minY = Math.min(minY, y); maxY = Math.max(maxY, y);
    }
    int headL = w, headR = -1;
    for (int y = minY; y < minY + 14 && y <= maxY; y++) for (int x = 0; x < w; x++) if (px[y * w + x] != 0) { headL = Math.min(headL, x); headR = Math.max(headR, x); }
    int dx = CELL / 2 - (headL + headR + 1) / 2, dy = FEET_ROW - maxY;
    if (minX + dx < 0 || maxX + dx >= CELL || minY + dy < 0) throw new IllegalStateException("El sprite no cabe en la celda");
    int[] cell = new int[CELL * CELL];
    for (int y = minY; y <= maxY; y++) for (int x = minX; x <= maxX; x++) cell[(y + dy) * CELL + x + dx] = px[y * w + x];
    System.out.printf("Quieto: %dx%d px%n", maxX - minX + 1, maxY - minY + 1);
    return cell;
  }

  /** Franjas (filas o columnas) con algún píxel opaco, separadas por huecos transparentes. */
  static List<int[]> bands(BufferedImage b, boolean rows) {
    int n = rows ? b.getHeight() : b.getWidth(), m = rows ? b.getWidth() : b.getHeight();
    List<int[]> out = new ArrayList<>(); int start = -1;
    for (int i = 0; i <= n; i++) {
      boolean on = false;
      for (int j = 0; i < n && j < m && !on; j++) on = (b.getRGB(rows ? j : i, rows ? i : j) >>> 24) > 128;
      if (on && start < 0) start = i;
      if (!on && start >= 0) { if (i - start > 20) out.add(new int[]{start, i - 1}); start = -1; }
    }
    return out;
  }

  /** Cuadrícula regular {paso, desfase} que mejor cae sobre los bordes del perfil g. */
  static double[] comb(double[] g, double minPitch, double maxPitch) {
    double mean = Arrays.stream(g).average().orElse(1), best = -1, bs = 0, bo = 0;
    for (double s = minPitch; s <= maxPitch; s += 0.002)
      for (double o = 0; o < s; o += 0.05) {
        double sum = 0; int k = 0;
        for (double p = o; p < g.length; p += s) { int i = (int) Math.round(p); if (i < g.length) { sum += g[i]; k++; } }
        double score = sum / k / mean;
        if (score > best) { best = score; bs = s; bo = o; }
      }
    return new double[]{bs, bo};
  }

  /** Cortes de una cuadrícula irregular: programación dinámica que premia caer en un borde y castiga alejarse del paso esperado. */
  static int[] cuts(double[] g, double pitch, double lambda) {
    int n = g.length;
    double mean = Arrays.stream(g).average().orElse(1);
    int dmin = (int) Math.floor(pitch * 0.7), dmax = (int) Math.ceil(pitch * 1.35);
    double[] best = new double[n]; int[] prev = new int[n];
    Arrays.fill(best, Double.NEGATIVE_INFINITY);
    for (int x = 0; x < Math.min(n, dmax); x++) { best[x] = g[x] / mean; prev[x] = -1; }
    for (int x = dmin; x < n; x++)
      for (int d = dmin; d <= dmax && x - d >= 0; d++) {
        if (best[x - d] == Double.NEGATIVE_INFINITY) continue;
        double v = best[x - d] + g[x] / mean - lambda * (d - pitch) * (d - pitch);
        if (v > best[x]) { best[x] = v; prev[x] = x - d; }
      }
    int end = n - 1;
    for (int x = Math.max(0, n - dmax); x < n; x++) if (best[x] > best[end]) end = x;
    List<Integer> out = new ArrayList<>();
    for (int x = end; x >= 0; x = prev[x]) out.add(0, x);
    return out.stream().mapToInt(Integer::intValue).toArray();
  }

  /** Color de una celda: mediana de su parte central (los bordes están borrosos); 0 = transparente. */
  static int sample(BufferedImage b, int x0, int x1, int y0, int y1) {
    int mx = Math.max(1, (x1 - x0) / 4), my = Math.max(1, (y1 - y0) / 4);
    List<int[]> px = new ArrayList<>(); int alpha = 0, n = 0;
    for (int y = y0 + my; y < y1 - my; y++) for (int x = x0 + mx; x < x1 - mx; x++) {
      int c = b.getRGB(x, y); alpha += c >>> 24; n++;
      if ((c >>> 24) > 128) px.add(new int[]{c >> 16 & 255, c >> 8 & 255, c & 255});
    }
    if (n == 0 || alpha / n < 128 || px.isEmpty()) return 0;
    int[] m = new int[3];
    for (int ch = 0; ch < 3; ch++) { final int k = ch; px.sort(Comparator.comparingInt(p -> p[k])); m[ch] = px.get(px.size() / 2)[ch]; }
    return argb(m[0] << 16 | m[1] << 8 | m[2]);
  }

  /** El color de la paleta más parecido (distancia en CIELAB). */
  static int quantize(int c) {
    if (c == 0) return 0;
    double[] l = lab(c); int best = 0; double bd = Double.MAX_VALUE;
    for (int p : PALETTE) {
      double[] q = lab(p); double d = sq(l[0] - q[0]) + sq(l[1] - q[1]) + sq(l[2] - q[2]);
      if (d < bd) { bd = d; best = p; }
    }
    return argb(best);
  }

  static double[] lab(int rgb) {
    double r = lin((rgb >> 16 & 255) / 255.0), g = lin((rgb >> 8 & 255) / 255.0), b = lin((rgb & 255) / 255.0);
    double x = f((0.4124 * r + 0.3576 * g + 0.1805 * b) / 0.95047), y = f(0.2126 * r + 0.7152 * g + 0.0722 * b), z = f((0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.08883);
    return new double[]{116 * y - 16, 500 * (x - y), 200 * (y - z)};
  }
  static double lin(double c) { return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4); }
  static double f(double t) { return t > 0.008856 ? Math.cbrt(t) : 7.787 * t + 16 / 116.0; }
  static double sq(double v) { return v * v; }
  static int argb(int rgb) { return 0xff000000 | rgb; }

  static double diff(int a, int b) {
    int aa = a >>> 24, ab = b >>> 24; double d = 0;
    for (int sh = 0; sh <= 16; sh += 8) d += Math.abs((a >> sh & 255) * aa / 255.0 - (b >> sh & 255) * ab / 255.0);
    return d + Math.abs(aa - ab);
  }

  static BufferedImage preview(BufferedImage sheet, int s) {
    BufferedImage out = new BufferedImage(sheet.getWidth() * s, sheet.getHeight() * s, BufferedImage.TYPE_INT_RGB);
    for (int y = 0; y < out.getHeight(); y++) for (int x = 0; x < out.getWidth(); x++) {
      int c = sheet.getRGB(x / s, y / s);
      boolean edge = (x / s) % CELL == 0 || (y / s) % CELL == 0;
      out.setRGB(x, y, (c >>> 24) != 0 ? c & 0xffffff : edge ? 0xb0b0c8 : ((x / s / 4 + y / s / 4) % 2 == 0 ? 0xdcdce8 : 0xf0f0f0));
    }
    return out;
  }
}
