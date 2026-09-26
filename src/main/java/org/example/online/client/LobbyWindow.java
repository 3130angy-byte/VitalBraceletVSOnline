package org.example.online.client;

import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;

import org.example.online.LobbyMap;
import org.example.online.Protocol;
import org.example.online.server.VsServer;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.io.IOException;
import java.util.HashMap;
import java.util.function.Consumer;
import java.util.List;
import java.util.Map;

/**
 * Ventana de la sala del VS Online para UN jugador: el 1er piso (LobbyMap,
 * dibujo provisional), avatares, el Digimon de cada jugador siguiéndolo con
 * su rango, avisos de NPC/lugares al acercarse y chat entre jugadores.
 * Clic para caminar (el servidor calcula el camino alrededor de los muros).
 *
 * La usan el programa principal (Batalla > VS Online, tras la animación del
 * portal) y la ventana de prueba LobbyTestWindow. Al cerrarse avisa con
 * onClosed (el programa principal la usa para la animación de salida).
 */
public final class LobbyWindow {

    private static final double VIEW_SCALE = 1.0;
    /** Los pies quedan unos píxeles bajo el centro de la casilla, para que el avatar "pise" su baldosa. */
    private static final double FEET_OFFSET = 5;
    /** Qué tanto se acerca la posición dibujada a la del servidor en cada frame (suaviza los 10 Hz). */
    private static final double SMOOTHING = 0.25;
    /** A esta distancia (unidades del mundo) de un NPC o lugar, aparece su aviso. */
    private static final double NEAR_DISTANCE = 44;


    private final Stage stage;
    private final String host;
    private final int port;
    private final String name;
    private final int windowIndex;
    /** Mi Digimon listo para mandar al entrar (null = entro sin Digimon). */
    private final JSONObject myDigimon;
    private final String title;
    private final Runnable onClosed;
    private AnimationTimer renderLoop;
    private OfficialBattleUi battleUi;
    private Consumer<OfficialBattleResult> onBattleResult;

    private VsClient client = new VsClient();
    private final Canvas canvas = new Canvas(Protocol.ROOM_WIDTH * VIEW_SCALE, Protocol.ROOM_HEIGHT * VIEW_SCALE);
    private final ListView<String> chatLog = new ListView<>();
    private final TextField chatInput = new TextField();
    private final Label status = new Label("Conectando...");

    /** id -> {x, y} según el servidor, y la posición suavizada que se dibuja. */
    private final Map<Integer, double[]> serverPos = new HashMap<>();
    private final Map<Integer, double[]> drawPos = new HashMap<>();
    private final Map<Integer, String> names = new HashMap<>();
    private int myId = -1;
    private Image mapImage;
    /** Un solo avatar por ahora (el editor de personajes vendrá después): todos usan el mismo. */
    private final Map<AvatarSprites.Direction, Image[]> avatarFrames = AvatarSprites.build(AvatarSprites.Palette.defaultMale());
    private final Map<Integer, AvatarAnim> anims = new HashMap<>();
    private final Map<Integer, LobbyDigimon> digimons = new HashMap<>();

    /**
     * @param title     título de la ventana
     * @param onClosed  se llama una vez al cerrar la ventana (p. ej. para la animación de salida del portal)
     */
    public LobbyWindow(Stage stage, String host, int port, String name, int windowIndex,
                       JSONObject myDigimon, String title, Runnable onClosed) {
        this.title = title;
        this.onClosed = onClosed;
        this.myDigimon = myDigimon;
        this.stage = stage;
        this.host = host;
        this.port = port;
        this.name = name;
        this.windowIndex = windowIndex;
    }

    public void open() {
        status.setStyle("-fx-text-fill: #cfd8e3;");
        status.setPadding(new Insets(6, 10, 6, 10));

        chatLog.setPrefWidth(260);
        chatInput.setPromptText("Escribe y presiona Enter");
        chatInput.setOnAction(e -> {
            String text = chatInput.getText().trim();
            if (!text.isEmpty()) client.chat(text);
            chatInput.clear();
        });
        VBox chatBox = new VBox(6, chatLog, chatInput);
        chatBox.setPadding(new Insets(6));
        VBox.setVgrow(chatLog, Priority.ALWAYS);

        canvas.setOnMouseClicked(e -> client.moveTo(e.getX() / VIEW_SCALE, e.getY() / VIEW_SCALE));

        // Centro = el mapa + una capa encima para los diálogos del NPC y de los retos.
        StackPane center = new StackPane(canvas);
        BorderPane root = new BorderPane(center);
        battleUi = new OfficialBattleUi(() -> client, digimons, () -> myId, root, center,
                canvas.getWidth(), canvas.getHeight(), this::addChatLine);
        battleUi.setOnResult(onBattleResult);
        center.getChildren().add(battleUi.layer);
        root.setTop(status);
        root.setRight(chatBox);
        root.setStyle("-fx-background-color: #1b1f2a;");

        stage.setTitle(title);
        stage.setScene(new Scene(root));
        stage.setX(40 + windowIndex * 60);
        stage.setY(40 + windowIndex * 60);
        stage.setOnHidden(e -> {
            client.close();
            if (renderLoop != null) renderLoop.stop();
            if (onClosed != null) onClosed.run();
        });
        stage.show();

        renderLoop = new AnimationTimer() {
            @Override
            public void handle(long now) {
                render();
            }
        };
        renderLoop.start();

        try {
            connect();
        } catch (Exception first) {
            // Sin servidor en ESTA PC: se arranca uno dentro del programa y se reintenta,
            // para que siempre aparezcas en la sala con tu avatar y tu Digimon.
            if (isLocalHost(host) && VsServer.startEmbedded(port)) {
                try {
                    client = new VsClient(); // un socket que falló no se puede reutilizar
                    connect();
                    return;
                } catch (Exception second) {
                    first = second;
                }
            }
            status.setText("No se pudo conectar a " + host + ":" + port + " (" + first.getMessage() + ")"
                    + (isLocalHost(host) ? "" : " -- ¿el anfitrión tiene el servidor abierto y Tailscale conectado?"));
        }
    }

    private void connect() throws IOException {
        client.connect(host, port, name,
                msg -> Platform.runLater(() -> onMessage(msg)),
                reason -> Platform.runLater(() -> status.setText("Desconectado: " + reason)));
    }

    private static boolean isLocalHost(String host) {
        return host.equals("127.0.0.1") || host.equalsIgnoreCase("localhost");
    }

    private void onMessage(JSONObject m) {
        if (battleUi != null && battleUi.handle(m)) return; // disponibles, retos y peleas
        switch (m.optString("t")) {
            case "welcome" -> {
                myId = m.getInt("id");
                if (myDigimon != null) client.sendDigimon(myDigimon);
                status.setText("Conectado como " + name + " a " + host + ":" + port + " (" + m.optString("room") + ")");
            }
            case "snapshot" -> {
                JSONArray players = m.getJSONArray("players");
                Map<Integer, double[]> fresh = new HashMap<>();
                for (int i = 0; i < players.length(); i++) {
                    JSONObject p = players.getJSONObject(i);
                    int id = p.getInt("id");
                    fresh.put(id, new double[]{p.getDouble("x"), p.getDouble("y")});
                    names.put(id, p.getString("name"));
                    drawPos.putIfAbsent(id, new double[]{p.getDouble("x"), p.getDouble("y")});
                }
                serverPos.clear();
                serverPos.putAll(fresh);
                drawPos.keySet().retainAll(fresh.keySet());
                anims.keySet().retainAll(fresh.keySet());
                digimons.keySet().retainAll(fresh.keySet());
            }
            case "joined" -> addChatLine("* " + m.optString("name") + " entró a la sala");
            case "left" -> addChatLine("* " + m.optString("name") + " salió de la sala");
            case "digimon" -> {
                try {
                    digimons.put(m.getInt("id"), LobbyDigimon.fromMessage(m));
                } catch (RuntimeException ex) {
                    System.out.println("Digimon inválido recibido, ignorado: " + ex.getMessage());
                }
            }
            case "chat" -> addChatLine(m.optString("name") + ": " + m.optString("text"));
            case "error" -> status.setText("Error del servidor: " + m.optString("msg"));
            default -> { }
        }
    }

    /** Los pies del avatar quedan en su posición del mundo; el sprite de lado se espeja al ir a la derecha. */
    private void drawAvatar(GraphicsContext g, int id, double[] pos) {
        AvatarAnim a = anims.get(id);
        if (a == null) return;
        Image frame = avatarFrames.get(a.direction)[a.frame];
        double px = pos[0] * VIEW_SCALE, py = pos[1] * VIEW_SCALE;
        double w = AvatarSprites.WIDTH * VIEW_SCALE, h = AvatarSprites.HEIGHT * VIEW_SCALE;
        double top = py + FEET_OFFSET * VIEW_SCALE - h;

        g.setFill(Color.rgb(0, 0, 0, 0.18));
        g.fillOval(px - w * 0.4, py + FEET_OFFSET * VIEW_SCALE - 3, w * 0.8, 5);
        if (a.direction == AvatarSprites.Direction.SIDE && a.facingRight) {
            g.save();
            g.translate(px, 0);
            g.scale(-1, 1); // convención del proyecto: derecha = espejado
            g.drawImage(frame, -w / 2, top, w, h);
            g.restore();
        } else {
            g.drawImage(frame, px - w / 2, top, w, h);
        }

        // Nombre del jugador y, si trajo Digimon, el rango de ese Digimon (nunca stats).
        String label = names.getOrDefault(id, "?");
        LobbyDigimon d = digimons.get(id);
        g.setFill(Color.WHITE);
        if (d == null || !LobbyDigimon.hasRank(d.rank)) {
            g.fillText(label, px, top - 4);
        } else {
            double textW = label.length() * 6.1;
            g.fillText(label, px - 7, top - 4);
            drawRankBadge(g, d.rank, px - 7 + textW / 2 + 8, top - 8);
        }
        if (id == myId) { // marcador de "este eres tú"
            g.setFill(Color.rgb(90, 220, 255));
            g.fillPolygon(new double[]{px - 4, px + 4, px}, new double[]{top - 22, top - 22, top - 16}, 3);
        }
    }

    /** El Digimon a la mitad de tamaño, con los pies en su posición; espejado al ir a la derecha. */
    private void drawDigimon(GraphicsContext g, LobbyDigimon d) {
        Image frame = d.currentFrame();
        double w = frame.getWidth() * VIEW_SCALE, h = frame.getHeight() * VIEW_SCALE;
        double px = d.x * VIEW_SCALE, feet = (d.y + FEET_OFFSET) * VIEW_SCALE;
        g.setFill(Color.rgb(0, 0, 0, 0.18));
        g.fillOval(px - w * 0.3, feet - 3, w * 0.6, 5);
        if (d.facingRight) {
            g.save();
            g.translate(px, 0);
            g.scale(-1, 1); // convención del proyecto: derecha = espejado
            g.drawImage(frame, -w / 2, feet - h, w, h);
            g.restore();
        } else {
            g.drawImage(frame, px - w / 2, feet - h, w, h);
        }
    }

    /** Insignia redonda con la letra del rango: C gris, B azul, A naranja, S dorado (colores provisionales). */
    private void drawRankBadge(GraphicsContext g, String rank, double cx, double cy) {
        Color fill = switch (rank) {
            case "S" -> Color.rgb(240, 190, 40);
            case "A" -> Color.rgb(240, 130, 40);
            case "B" -> Color.rgb(60, 140, 240);
            default -> Color.rgb(140, 140, 150);
        };
        g.setFill(Color.rgb(20, 20, 28));
        g.fillOval(cx - 7, cy - 7, 14, 14);
        g.setFill(fill);
        g.fillOval(cx - 6, cy - 6, 12, 12);
        g.setFill(Color.WHITE);
        g.fillText(rank, cx, cy + 4);
    }

    private void addChatLine(String line) {
        chatLog.getItems().add(line);
        chatLog.scrollTo(chatLog.getItems().size() - 1);
    }

    private void render() {
        GraphicsContext g = canvas.getGraphicsContext2D();
        if (mapImage == null) mapImage = LobbyMapRenderer.renderStatic();
        g.drawImage(mapImage, 0, 0, canvas.getWidth(), canvas.getHeight());

        g.setFont(Font.font("Consolas", FontWeight.BOLD, 11));
        g.setTextAlign(TextAlignment.CENTER);
        g.setImageSmoothing(false); // pixel art nítido

        // Avanzar posiciones suavizadas y animación, luego dibujar de arriba hacia
        // abajo: el que está más abajo en pantalla tapa al de atrás.
        long now = System.nanoTime();
        for (Map.Entry<Integer, double[]> e : drawPos.entrySet()) {
            double[] target = serverPos.get(e.getKey());
            double[] pos = e.getValue();
            double dx = 0, dy = 0;
            if (target != null) {
                dx = (target[0] - pos[0]) * SMOOTHING;
                dy = (target[1] - pos[1]) * SMOOTHING;
                pos[0] += dx;
                pos[1] += dy;
            }
            anims.computeIfAbsent(e.getKey(), k -> new AvatarAnim()).update(dx, dy, now);
            LobbyDigimon d = digimons.get(e.getKey());
            if (d != null) d.follow(pos[0], pos[1], now);
        }

        // Avatares y Digimon en una sola lista, ordenados por la altura de sus pies.
        List<Object[]> drawables = new ArrayList<>();
        for (Map.Entry<Integer, double[]> e : drawPos.entrySet()) {
            drawables.add(new Object[]{e.getValue()[1], (Runnable) () -> drawAvatar(g, e.getKey(), e.getValue())});
            LobbyDigimon d = digimons.get(e.getKey());
            if (d != null && !Double.isNaN(d.y)) drawables.add(new Object[]{d.y, (Runnable) () -> drawDigimon(g, d)});
        }
        drawables.sort(Comparator.comparingDouble(o -> (double) o[0]));
        drawables.forEach(o -> ((Runnable) o[1]).run());

        // Aviso del NPC o lugar cercano a MI jugador (solo en mi ventana).
        // El NPC de Batalla oficial no usa globo: abre su diálogo al llegar (OfficialBattleUi).
        double[] me = drawPos.get(myId);
        boolean nearRanking = false;
        if (me != null) {
            for (LobbyMap.Feature f : LobbyMap.FEATURES) {
                boolean isNear = Math.hypot(me[0] - f.x(), me[1] - f.y()) <= NEAR_DISTANCE;
                if (f.type() == LobbyMap.NPC_RANKING) {
                    nearRanking = isNear;
                    continue;
                }
                if (isNear) {
                    g.save();
                    g.scale(VIEW_SCALE, VIEW_SCALE);
                    LobbyMapRenderer.drawBubble(g, f);
                    g.restore();
                }
            }
        }
        if (battleUi != null) battleUi.setNearNpc(nearRanking);
    }

    /** Cada Batalla Oficial: el programa principal la suma al récord del Digimon y la comenta. */
    public void setOnBattleResult(Consumer<OfficialBattleResult> listener) {
        this.onBattleResult = listener;
        if (battleUi != null) battleUi.setOnResult(listener);
    }

    /**
     * Dirección y cuadro de animación de un avatar, deducidos de cuánto se movió
     * en este frame (el servidor solo manda posiciones).
     */
    private static final class AvatarAnim {
        private static final double MOVING_THRESHOLD = 0.12;
        private static final long STEP_NANOS = 140_000_000L;
        /** Frente/espalda: pierna, quieto, otra pierna, quieto. De lado: paso, quieto. */
        private static final int[] FRONT_CYCLE = {1, 0, 2, 0};
        private static final int[] SIDE_CYCLE = {1, 0};

        AvatarSprites.Direction direction = AvatarSprites.Direction.UP; // aparece de espaldas, entrando por el sur
        boolean facingRight = false;
        int frame = 0;
        private long walkStart = -1;

        void update(double dx, double dy, long now) {
            if (Math.hypot(dx, dy) < MOVING_THRESHOLD) {
                frame = 0;
                walkStart = -1;
                return;
            }
            if (Math.abs(dx) > Math.abs(dy)) {
                direction = AvatarSprites.Direction.SIDE;
                facingRight = dx > 0;
            } else {
                direction = dy > 0 ? AvatarSprites.Direction.DOWN : AvatarSprites.Direction.UP;
            }
            if (walkStart < 0) walkStart = now;
            int[] cycle = direction == AvatarSprites.Direction.SIDE ? SIDE_CYCLE : FRONT_CYCLE;
            frame = cycle[(int) (((now - walkStart) / STEP_NANOS) % cycle.length)];
        }
    }
}
