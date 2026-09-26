package org.example.online.client;

import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

import org.example.online.LobbyMap;

import java.util.ArrayList;
import java.util.List;

/**
 * Dibujo PROVISIONAL del 1er piso (LobbyMap) con figuras simples y los
 * colores del boceto del usuario: piso de baldosas celestes, muros azules
 * con borde cian, mostrador, portal bloqueado y Training Room con candados.
 * El arte final será una imagen en pixel art sobre esta misma cuadrícula;
 * cuando exista, basta con reemplazar renderStatic() por esa imagen.
 */
public final class LobbyMapRenderer {

    private static final Color VOID = Color.rgb(10, 14, 24);
    private static final Color VOID_GRID = Color.rgb(24, 32, 48);
    private static final Color FLOOR = Color.rgb(186, 214, 238);
    private static final Color FLOOR_LINE = Color.rgb(156, 190, 222);
    private static final Color TRAINING_FLOOR = Color.rgb(166, 196, 226);
    private static final Color WALL = Color.rgb(28, 62, 122);
    private static final Color WALL_EDGE = Color.rgb(90, 200, 250);
    private static final Color COUNTER = Color.rgb(30, 110, 178);
    private static final Color LOCK_RED = Color.rgb(214, 48, 48);
    private static final Color LABEL = Color.WHITE;

    private LobbyMapRenderer() {}

    /** Todo lo que no se mueve, dibujado una sola vez a una imagen. */
    public static Image renderStatic() {
        int t = LobbyMap.TILE;
        Canvas canvas = new Canvas(LobbyMap.WIDTH, LobbyMap.HEIGHT);
        GraphicsContext g = canvas.getGraphicsContext2D();

        g.setFill(VOID);
        g.fillRect(0, 0, LobbyMap.WIDTH, LobbyMap.HEIGHT);
        g.setStroke(VOID_GRID);
        for (int x = 0; x <= LobbyMap.WIDTH; x += t * 2) g.strokeLine(x, 0, x, LobbyMap.HEIGHT);
        for (int y = 0; y <= LobbyMap.HEIGHT; y += t * 2) g.strokeLine(0, y, LobbyMap.WIDTH, y);

        for (int row = 0; row < LobbyMap.ROWS; row++) {
            for (int col = 0; col < LobbyMap.COLS; col++) {
                double x = col * t, y = row * t;
                switch (LobbyMap.at(col, row)) {
                    case LobbyMap.FLOOR, LobbyMap.ENTRANCE, LobbyMap.STAFF_FLOOR -> tile(g, x, y, FLOOR);
                    case LobbyMap.TRAINING_FLOOR -> tile(g, x, y, TRAINING_FLOOR);
                    case LobbyMap.COUNTER -> {
                        g.setFill(COUNTER);
                        g.fillRect(x, y, t, t);
                        g.setFill(WALL_EDGE);
                        g.fillRect(x, y + 2, t, 3);
                    }
                    case LobbyMap.NPC_RANKING, LobbyMap.NPC_RECEPTION -> {
                        tile(g, x, y, FLOOR);
                        npc(g, x + t / 2.0, y + t / 2.0);
                    }
                    case LobbyMap.TERMINAL -> {
                        g.setFill(Color.rgb(20, 30, 50));
                        g.fillRect(x, y, t, t);
                        g.setFill(Color.rgb(90, 220, 255));
                        g.fillRect(x + 3, y + 3, t - 6, t - 8);
                    }
                    case LobbyMap.LOCKED_DOOR -> {
                        tile(g, x, y, TRAINING_FLOOR);
                        g.setFill(LOCK_RED);
                        g.fillRect(x + 5, y, 6, t);
                    }
                    case LobbyMap.PORTAL -> {
                        g.setFill(Color.rgb(16, 22, 40));
                        g.fillRect(x, y, t, t);
                    }
                    default -> {
                        if (touchesFloor(col, row)) wall(g, col, row);
                    }
                }
            }
        }

        drawPortal(g);
        drawEntranceMarks(g);
        for (LobbyMap.Feature f : LobbyMap.FEATURES) {
            label(g, f.label(), f.x(), labelY(f));
        }
        label(g, "Entrada", 26 * t + t / 2.0, 27 * t - 4);

        SnapshotParameters params = new SnapshotParameters();
        params.setFill(VOID);
        return canvas.snapshot(params, null);
    }

    private static double labelY(LobbyMap.Feature f) {
        return switch (f.type()) {
            case LobbyMap.NPC_RANKING, LobbyMap.NPC_RECEPTION -> f.y() - 12;
            case LobbyMap.TERMINAL -> f.y() - 12;
            case LobbyMap.PORTAL -> f.y() - 52;
            default -> f.y() - 60; // Training Room: sobre la sala
        };
    }

    private static void tile(GraphicsContext g, double x, double y, Color fill) {
        int t = LobbyMap.TILE;
        g.setFill(fill);
        g.fillRect(x, y, t, t);
        g.setStroke(FLOOR_LINE);
        g.strokeRect(x + 0.5, y + 0.5, t - 1, t - 1);
    }

    private static void wall(GraphicsContext g, int col, int row) {
        int t = LobbyMap.TILE;
        double x = col * t, y = row * t;
        g.setFill(WALL);
        g.fillRect(x, y, t, t);
        g.setFill(WALL_EDGE);
        if (LobbyMap.walkable(col, row + 1)) g.fillRect(x, y + t - 3, t, 3);
        if (LobbyMap.walkable(col, row - 1)) g.fillRect(x, y, t, 3);
        if (LobbyMap.walkable(col + 1, row)) g.fillRect(x + t - 3, y, 3, t);
        if (LobbyMap.walkable(col - 1, row)) g.fillRect(x, y, 3, t);
    }

    private static boolean touchesFloor(int col, int row) {
        for (int dy = -1; dy <= 1; dy++)
            for (int dx = -1; dx <= 1; dx++)
                if (LobbyMap.walkable(col + dx, row + dy)) return true;
        return false;
    }

    private static void npc(GraphicsContext g, double cx, double cy) {
        g.setFill(Color.rgb(60, 60, 90));
        g.fillRect(cx - 5, cy - 2, 10, 9);
        g.setFill(Color.rgb(240, 200, 170));
        g.fillOval(cx - 5, cy - 11, 10, 10);
        g.setFill(Color.rgb(90, 60, 40));
        g.fillRect(cx - 5, cy - 12, 10, 4);
    }

    private static void drawPortal(GraphicsContext g) {
        int t = LobbyMap.TILE;
        double cx = 47.5 * t, cy = 15.5 * t;
        g.setStroke(Color.rgb(70, 110, 190));
        g.setLineWidth(6);
        g.strokeOval(cx - 26, cy - 36, 52, 72);
        g.setStroke(Color.rgb(40, 60, 120));
        g.setLineWidth(2);
        g.strokeOval(cx - 18, cy - 28, 36, 56);
        g.setLineWidth(1);
        g.setFill(LOCK_RED);
        g.fillRect(cx - 30, cy - 7, 60, 14);
        g.setFill(Color.WHITE);
        g.setFont(Font.font("Consolas", FontWeight.BOLD, 9));
        g.setTextAlign(TextAlignment.CENTER);
        g.fillText("BLOQUEADO", cx, cy + 3);
    }

    private static void drawEntranceMarks(GraphicsContext g) {
        int t = LobbyMap.TILE;
        g.setFill(Color.rgb(90, 200, 250, 0.7));
        for (int col = 24; col <= 28; col++) g.fillRect(col * t + 3, 28 * t + t - 4, t - 6, 3);
    }

    private static void label(GraphicsContext g, String text, double cx, double y) {
        g.setFont(Font.font("Consolas", FontWeight.BOLD, 11));
        // Consolas 11 ~ 6.1 px por letra: nunca dejar que la etiqueta se salga del mapa.
        double half = text.length() * 6.1 / 2 + 4;
        cx = Math.max(half, Math.min(LobbyMap.WIDTH - half, cx));
        g.setTextAlign(TextAlignment.CENTER);
        g.setFill(Color.rgb(0, 0, 0, 0.6));
        g.fillText(text, cx + 1, y + 1);
        g.setFill(LABEL);
        g.fillText(text, cx, y);
    }

    /** Globo con el aviso del lugar/NPC cercano; se dibuja cada frame encima de todo. */
    public static void drawBubble(GraphicsContext g, LobbyMap.Feature f) {
        List<String> lines = wrap(f.message(), 34);
        double w = 230, lineH = 14, h = lines.size() * lineH + 12;
        double x = Math.max(4, Math.min(LobbyMap.WIDTH - w - 4, f.x() - w / 2));
        double y = f.type() == LobbyMap.NPC_RANKING || f.type() == LobbyMap.NPC_RECEPTION
                ? f.y() + 34 : Math.max(4, f.y() - h - 30);
        g.setFill(Color.rgb(255, 255, 255, 0.95));
        g.fillRoundRect(x, y, w, h, 12, 12);
        g.setStroke(Color.rgb(30, 60, 110));
        g.strokeRoundRect(x, y, w, h, 12, 12);
        g.setFill(Color.rgb(20, 30, 50));
        g.setFont(Font.font("Consolas", 11));
        g.setTextAlign(TextAlignment.LEFT);
        for (int i = 0; i < lines.size(); i++) g.fillText(lines.get(i), x + 8, y + 16 + i * lineH);
    }

    private static List<String> wrap(String text, int maxChars) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (line.length() + word.length() + 1 > maxChars && line.length() > 0) {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) line.append(' ');
            line.append(word);
        }
        if (line.length() > 0) lines.add(line.toString());
        return lines;
    }
}
