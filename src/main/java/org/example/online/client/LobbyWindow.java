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

import org.example.lab.LabWindow;
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
import java.util.function.Supplier;
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

    /**
     * CÁMARA (pedido del usuario): no se ve toda la sala, sino la zona
     * alrededor de tu avatar, acercada x2. Con ZOOM 2 el Digimon (que en el
     * mundo mide la mitad de su sprite) se ve con sus píxeles originales 1:1.
     */
    private static final double ZOOM = 2.0;
    private static final double VIEW_WIDTH = 960, VIEW_HEIGHT = 600;
    /** La cámara alcanza al avatar suavemente (fracción por segundo, no por cuadro). */
    private static final double CAMERA_FOLLOW_PER_SECOND = 8;
    /** Los pies quedan unos píxeles bajo el centro de la casilla, para que el avatar "pise" su baldosa. */
    private static final double FEET_OFFSET = 5;
    /**
     * El servidor manda 10 fotos por segundo. Se dibuja este tiempo "en el
     * pasado" y se interpola entre fotos (ver render). Antes el dibujo se
     * acercaba un % por cuadro: arrancaba de golpe y frenaba 10 veces por
     * segundo (el efecto "látigo"). 120 ms = poco más de una foto de margen.
     */
    private static final long INTERP_DELAY_NANOS = 120_000_000L;
    /** Un salto mayor que esto (entró a la sala, se reconectó) no se anima como caminata. */
    private static final double SNAP_DISTANCE = 64;
    /** Fotos recientes del servidor por jugador: {tiempo de llegada, x, y}. */
    private final Map<Integer, java.util.ArrayDeque<double[]>> histories = new HashMap<>();
    /** A esta distancia (unidades del mundo) de un NPC o lugar, aparece su aviso. */
    private static final double NEAR_DISTANCE = 44;
    /** Minimapa en la esquina superior derecha (unidades de pantalla). */
    private static final double MINIMAP_WIDTH = 170;

    private double cameraX = Double.NaN, cameraY = Double.NaN;
    private long lastFrameNanos = -1;


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
    private boolean wasNearPc = false;
    /** Arma el mensaje "digimon" con tu equipo actual (puesto 1 + compañero). Null = se usa myDigimon fijo. */
    private Supplier<JSONObject> teamSupplier;
    /** Salas abiertas: al cambiar el equipo en el Laboratorio se les avisa (notifyTeamChanged). */
    private static final List<LobbyWindow> OPEN = new ArrayList<>();

    private VsClient client = new VsClient();
    private final Canvas canvas = new Canvas(VIEW_WIDTH, VIEW_HEIGHT);
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
    /** Un solo avatar por ahora (el editor de personajes vendrá después), de lado: mira a la izquierda y se espeja a la derecha. */
    private final Image[] avatarFrames = AvatarSprites.build(AvatarSprites.Palette.defaultUrban());
    private final Map<Integer, AvatarAnim> anims = new HashMap<>();
    private final Map<Integer, LobbyDigimon> digimons = new HashMap<>();
    /** Cambios de Digimon por portal en curso, por jugador (LobbyPortalSwap). */
    private final Map<Integer, LobbyPortalSwap> swaps = new HashMap<>();
    /** El servidor rechazó el equipo nuevo con el portal abierto: se reintenta UNA vez. */
    private boolean teamRetried = false;

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

    /** El equipo se toma de aquí al entrar y cada vez que cambia en la PC/Laboratorio. */
    public void setTeamSupplier(Supplier<JSONObject> teamSupplier) {
        this.teamSupplier = teamSupplier;
    }

    /** Lo llama el programa principal tras cambiar el equipo (puestos 1 y 2): se reenvía al servidor. */
    public static void notifyTeamChanged() {
        for (LobbyWindow lobby : List.copyOf(OPEN)) {
            if (lobby.teamSupplier == null || lobby.myId < 0) continue;
            JSONObject team = lobby.teamSupplier.get();
            if (team != null) lobby.client.sendDigimon(team);
        }
    }

    /**
     * Tu Digimon de la sala empieza a irse por un portal que queda abierto;
     * el que llega sale por él cuando el servidor reparta el equipo nuevo
     * (notifyTeamChanged). Lo llama el programa principal al reemplazar o
     * intercambiar el puesto 1 desde la PC.
     */
    public static void beginPortalSwap() {
        for (LobbyWindow lobby : List.copyOf(OPEN)) lobby.startMyDeparture();
    }

    /** Hay una sala abierta (solo se entra con un Digimon a la vez). */
    public static boolean isAnyOpen() {
        return !OPEN.isEmpty();
    }

    /** Por qué no se puede cambiar el equipo ahora mismo, o null si se puede. */
    public static String teamChangeBlocker() {
        for (LobbyWindow lobby : OPEN) {
            if (lobby.battleUi != null && lobby.battleUi.isBusy()) {
                return "No puedes cambiar tu equipo durante un reto o una pelea del VS Online.";
            }
            if (lobby.swaps.containsKey(lobby.myId)) return "Espera a que termine el cambio por el portal.";
        }
        return null;
    }

    private void startMyDeparture() {
        LobbyDigimon mine = digimons.get(myId);
        double[] me = drawPos.get(myId);
        if (mine == null || Double.isNaN(mine.x) || me == null || swaps.containsKey(myId)) return;
        teamRetried = false;
        swaps.put(myId, new LobbyPortalSwap(mine, me[0], null, System.nanoTime()));
    }

    private JSONObject currentTeam() {
        return teamSupplier != null ? teamSupplier.get() : myDigimon;
    }

    public void open() {
        OPEN.add(this);
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

        // Clic en pantalla -> punto del mundo, según dónde esté la cámara.
        canvas.setOnMouseClicked(e -> {
            if (Double.isNaN(cameraX)) return;
            client.moveTo(cameraX + e.getX() / ZOOM, cameraY + e.getY() / ZOOM);
        });

        // Centro = el mapa + una capa encima para los diálogos del NPC y de los retos.
        StackPane center = new StackPane(canvas);
        BorderPane root = new BorderPane(center);
        battleUi = new OfficialBattleUi(() -> client, digimons, () -> myId, root, center,
                canvas.getWidth(), canvas.getHeight(), this::addChatLine);
        battleUi.setOnResult(onBattleResult);
        battleUi.setArenaHooks(arenaBefore, arenaAfter);
        center.getChildren().add(battleUi.layer);
        root.setTop(status);
        root.setRight(chatBox);
        root.setStyle("-fx-background-color: #1b1f2a;");

        stage.setTitle(title);
        stage.setScene(new Scene(root));
        stage.setX(40 + windowIndex * 60);
        stage.setY(40 + windowIndex * 60);
        stage.setOnHidden(e -> {
            OPEN.remove(this);
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
                JSONObject team = currentTeam();
                if (team != null) client.sendDigimon(team);
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
                swaps.keySet().retainAll(fresh.keySet());
            }
            case "joined" -> addChatLine("* " + m.optString("name") + " entró a la sala");
            case "left" -> addChatLine("* " + m.optString("name") + " salió de la sala");
            case "digimon" -> {
                try {
                    int id = m.getInt("id");
                    LobbyDigimon fresh = LobbyDigimon.fromMessage(m);
                    LobbyDigimon old = digimons.put(id, fresh);
                    LobbyPortalSwap swap = swaps.get(id);
                    if (swap != null && swap.waiting()) {
                        swap.arrive(fresh); // tu portal ya está abierto: el nuevo sale por él
                    } else if (old != null && swap == null && old.look != fresh.look && !Double.isNaN(old.x)) {
                        // Otro Digimon en el puesto 1: se va uno por el portal y sale el otro.
                        double[] avatar = drawPos.get(id);
                        swaps.put(id, new LobbyPortalSwap(old, avatar != null ? avatar[0] : old.x, fresh, System.nanoTime()));
                    } else if (old != null) {
                        fresh.takePlaceOf(old); // mismo Digimon (p. ej. solo cambió el compañero): sin saltos
                    }
                    if (id == myId) teamRetried = false;
                    if (old != null && id == myId) addChatLine("* Tu equipo se actualizó.");
                } catch (RuntimeException ex) {
                    System.out.println("Digimon inválido recibido, ignorado: " + ex.getMessage());
                }
            }
            case "teamRejected" -> {
                addChatLine("* " + m.optString("reason"));
                LobbyPortalSwap swap = swaps.get(myId);
                if (swap == null || !swap.waiting()) return;
                if (teamRetried) {
                    swap.arrive(digimons.get(myId)); // no se pudo: vuelve a salir el mismo
                    return;
                }
                // Con el portal abierto, un rechazo por "espera un momento" se reintenta solo.
                teamRetried = true;
                javafx.animation.PauseTransition retry = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(5.5));
                retry.setOnFinished(e -> {
                    JSONObject team = currentTeam();
                    if (team != null && OPEN.contains(this)) client.sendDigimon(team);
                });
                retry.play();
            }
            case "chat" -> addChatLine(m.optString("name") + ": " + m.optString("text"));
            case "error" -> {
                String code = m.optString("code");
                if ("notAllowed".equals(code) || "removed".equals(code)) {
                    // Lista de acceso del anfitrión: sin permiso no hay sala; se cierra y tu Digimon vuelve.
                    status.setText(m.optString("msg"));
                    javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                            javafx.scene.control.Alert.AlertType.INFORMATION, m.optString("msg"));
                    alert.initOwner(stage);
                    alert.setTitle("VS Online");
                    alert.setHeaderText("notAllowed".equals(code) ? "No estás en la lista del anfitrión" : "Saliste del servidor");
                    alert.setOnHidden(e -> stage.close());
                    alert.show();
                } else {
                    status.setText("Error del servidor: " + m.optString("msg"));
                }
            }
            default -> { }
        }
    }

    /** Los pies del avatar quedan en su posición del mundo; el sprite de lado se espeja al ir a la derecha. */
    private void drawAvatar(GraphicsContext g, int id, double[] pos) {
        AvatarAnim a = anims.get(id);
        if (a == null) return;
        Image frame = avatarFrames[a.frame];
        // Coordenadas del MUNDO: la cámara (escala y desplazamiento) ya está aplicada al GraphicsContext.
        double px = pos[0], py = pos[1];
        double w = AvatarSprites.WIDTH, h = AvatarSprites.HEIGHT;
        double top = py + FEET_OFFSET - h;

        g.setFill(Color.rgb(0, 0, 0, 0.18));
        g.fillOval(px - w * 0.4, py + FEET_OFFSET - 1.5, w * 0.8, 3);
        if (a.facingRight) {
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
        // Texto en el mundo: con el acercamiento x2 se ve de 13 px (el texto del lienzo es vectorial, queda nítido).
        g.setFont(Font.font("Consolas", FontWeight.BOLD, 6.5));
        g.setFill(Color.rgb(0, 0, 0, 0.55));
        g.fillText(label, px + 0.5 - (d != null && LobbyDigimon.hasRank(d.rank) ? 4 : 0), top - 2 + 0.5);
        g.setFill(Color.WHITE);
        if (d == null || !LobbyDigimon.hasRank(d.rank)) {
            g.fillText(label, px, top - 2);
        } else {
            double textW = label.length() * 3.6;
            g.fillText(label, px - 4, top - 2);
            drawRankBadge(g, d.rank, px - 4 + textW / 2 + 5, top - 4.5);
        }
        if (id == myId) { // marcador de "este eres tú"
            g.setFill(Color.rgb(90, 220, 255));
            g.fillPolygon(new double[]{px - 2.5, px + 2.5, px}, new double[]{top - 13, top - 13, top - 9.5}, 3);
        }
    }

    /**
     * El Digimon con los pies en su posición, espejado al ir a la derecha. En
     * el mundo mide la mitad de su sprite (frente al avatar de 16x24), y con
     * la cámara x2 se ve con sus píxeles originales 1:1.
     */
    private void drawDigimon(GraphicsContext g, LobbyDigimon d) {
        Image frame = d.currentFrame();
        double w = frame.getWidth() / 2, h = frame.getHeight() / 2;
        double px = d.x, feet = d.y + FEET_OFFSET;
        g.setFill(Color.rgb(0, 0, 0, 0.18));
        g.fillOval(px - w * 0.3, feet - 1.5, w * 0.6, 3);
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
        g.fillOval(cx - 4.5, cy - 4.5, 9, 9);
        g.setFill(fill);
        g.fillOval(cx - 3.8, cy - 3.8, 7.6, 7.6);
        g.setFill(Color.WHITE);
        g.fillText(rank, cx, cy + 2.3);
    }

    private void addChatLine(String line) {
        chatLog.getItems().add(line);
        chatLog.scrollTo(chatLog.getItems().size() - 1);
    }

    private void render() {
        GraphicsContext g = canvas.getGraphicsContext2D();
        if (mapImage == null) mapImage = LobbyMapRenderer.renderStatic(ZOOM);
        long now = System.nanoTime();
        // Segundos desde el cuadro anterior: el movimiento no depende de los FPS (tope por si la ventana se congeló).
        double dt = lastFrameNanos < 0 ? 0 : Math.min(0.1, (now - lastFrameNanos) / 1e9);
        lastFrameNanos = now;

        // 1) Posiciones dibujadas: INTERPOLACIÓN CON RETRASO (la técnica de los juegos en red).
        // Cada personaje se dibuja INTERP_DELAY "en el pasado", recorriendo en línea recta
        // el tramo entre las dos fotos del servidor que rodean ese instante: avanza a la
        // velocidad exacta del servidor, sin tirones ni esperas entre fotos.
        long renderTime = now - INTERP_DELAY_NANOS;
        for (Map.Entry<Integer, double[]> e : drawPos.entrySet()) {
            int id = e.getKey();
            double[] pos = e.getValue();
            double[] target = serverPos.get(id);
            java.util.ArrayDeque<double[]> history = histories.computeIfAbsent(id, k -> new java.util.ArrayDeque<>());
            if (target != null) {
                double[] newest = history.peekLast();
                if (newest == null || newest[1] != target[0] || newest[2] != target[1]) {
                    history.addLast(new double[]{now, target[0], target[1]});
                    while (history.size() > 12) history.pollFirst();
                }
            }
            double[] interpolated = interpolate(history, renderTime);
            double dx = 0, dy = 0;
            if (interpolated != null) {
                dx = interpolated[0] - pos[0];
                dy = interpolated[1] - pos[1];
                if (Math.hypot(dx, dy) > SNAP_DISTANCE) dx = dy = 0; // salto (entró a la sala): sin animar
                pos[0] = interpolated[0];
                pos[1] = interpolated[1];
            }
            anims.computeIfAbsent(id, k -> new AvatarAnim()).update(dx, dy, now);
            LobbyDigimon d = digimons.get(id);
            if (d != null && !swaps.containsKey(id)) d.follow(pos[0], pos[1], now, dt);
        }
        histories.keySet().retainAll(drawPos.keySet());
        // Cambios por portal: al terminar, el nuevo sigue al avatar desde la salida del portal.
        swaps.entrySet().removeIf(e -> e.getValue().update(now, digimons.get(e.getKey())));

        // 2) Cámara: centrada en MI avatar, sin salirse del mapa.
        double viewW = canvas.getWidth() / ZOOM, viewH = canvas.getHeight() / ZOOM;
        double[] me = drawPos.get(myId);
        double focusX = me != null ? me[0] : LobbyMap.WIDTH / 2.0, focusY = me != null ? me[1] - 8 : LobbyMap.HEIGHT / 2.0;
        double wantX = clamp(focusX - viewW / 2, 0, LobbyMap.WIDTH - viewW);
        double wantY = clamp(focusY - viewH / 2, 0, LobbyMap.HEIGHT - viewH);
        if (Double.isNaN(cameraX) || dt == 0) {
            cameraX = wantX;
            cameraY = wantY;
        } else {
            double k = Math.min(1, CAMERA_FOLLOW_PER_SECOND * dt);
            cameraX += (wantX - cameraX) * k;
            cameraY += (wantY - cameraY) * k;
        }

        // 3) El mundo, con la cámara aplicada.
        g.setImageSmoothing(false); // pixel art nítido
        g.setFill(Color.rgb(10, 14, 24));
        g.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
        g.save();
        g.scale(ZOOM, ZOOM);
        g.translate(-cameraX, -cameraY);
        g.drawImage(mapImage, 0, 0, LobbyMap.WIDTH, LobbyMap.HEIGHT);
        LobbyMapRenderer.drawPortalAnimation(g, now); // portal decorativo, animado
        g.setTextAlign(TextAlignment.CENTER);

        // Avatares y Digimon en una sola lista, ordenados por la altura de sus pies
        // (el que está más abajo en pantalla tapa al de atrás).
        List<Object[]> drawables = new ArrayList<>();
        for (Map.Entry<Integer, double[]> e : drawPos.entrySet()) {
            drawables.add(new Object[]{e.getValue()[1], (Runnable) () -> drawAvatar(g, e.getKey(), e.getValue())});
            LobbyPortalSwap swap = swaps.get(e.getKey());
            LobbyDigimon d = digimons.get(e.getKey());
            if (swap != null) {
                drawables.add(new Object[]{swap.sortY(), (Runnable) () -> swap.draw(g, now, FEET_OFFSET)});
            } else if (d != null && !Double.isNaN(d.y)) {
                drawables.add(new Object[]{d.y, (Runnable) () -> drawDigimon(g, d)});
            }
        }
        drawables.sort(Comparator.comparingDouble(o -> (double) o[0]));
        drawables.forEach(o -> ((Runnable) o[1]).run());
        g.restore();

        // 4) En pantalla (sin cámara): aviso del NPC o lugar cercano a MI jugador y minimapa.
        // El NPC de Batalla oficial no usa globo: abre su diálogo al llegar (OfficialBattleUi).
        boolean nearRanking = false;
        boolean nearPc = false;
        if (me != null) {
            for (LobbyMap.Feature f : LobbyMap.FEATURES) {
                boolean isNear = Math.hypot(me[0] - f.x(), me[1] - f.y()) <= NEAR_DISTANCE;
                if (f.type() == LobbyMap.PC) nearPc = isNear;
                if (f.type() == LobbyMap.NPC_RANKING) {
                    nearRanking = isNear;
                    continue;
                }
                if (isNear) {
                    LobbyMapRenderer.drawBubble(g, f, (canvas.getWidth() - LobbyMapRenderer.BUBBLE_WIDTH) / 2, 12);
                }
            }
        }
        drawMinimap(g, viewW, viewH);
        if (battleUi != null) battleUi.setNearNpc(nearRanking);
        // La PC abre el Laboratorio (equipo: puestos 1 y 2) al LLEGAR, no en cada frame.
        if (nearPc && !wasNearPc) LabWindow.open();
        wasNearPc = nearPc;
    }

    /** Toda la sala en pequeño (esquina superior derecha): jugadores como puntos y el recuadro de la cámara. */
    private void drawMinimap(GraphicsContext g, double viewW, double viewH) {
        double s = MINIMAP_WIDTH / LobbyMap.WIDTH;
        double w = MINIMAP_WIDTH, h = LobbyMap.HEIGHT * s;
        double x0 = canvas.getWidth() - w - 10, y0 = 10;
        g.setGlobalAlpha(0.85);
        g.setImageSmoothing(true);
        g.drawImage(mapImage, x0, y0, w, h);
        g.setImageSmoothing(false);
        g.setGlobalAlpha(1);
        g.setStroke(Color.rgb(90, 200, 250));
        g.setLineWidth(1);
        g.strokeRect(x0, y0, w, h);
        for (Map.Entry<Integer, double[]> e : drawPos.entrySet()) {
            boolean mine = e.getKey() == myId;
            g.setFill(mine ? Color.rgb(90, 220, 255) : Color.WHITE);
            double r = mine ? 3 : 2;
            g.fillOval(x0 + e.getValue()[0] * s - r, y0 + e.getValue()[1] * s - r, r * 2, r * 2);
        }
        g.setStroke(Color.rgb(255, 255, 255, 0.8));
        g.strokeRect(x0 + cameraX * s, y0 + cameraY * s, viewW * s, viewH * s);
    }

    /**
     * Posición en el instante {@code time} según las fotos recibidas
     * {tiempo, x, y}: lineal entre las dos que lo rodean; si es más nueva que
     * todas, la última (nunca adivina hacia adelante).
     */
    private static double[] interpolate(java.util.ArrayDeque<double[]> history, long time) {
        if (history.isEmpty()) return null;
        double[] prev = null;
        for (double[] s : history) {
            if (s[0] >= time) {
                if (prev == null) return new double[]{s[1], s[2]};
                double span = s[0] - prev[0];
                double f = span <= 0 ? 1 : (time - prev[0]) / span;
                return new double[]{prev[1] + (s[1] - prev[1]) * f, prev[2] + (s[2] - prev[2]) * f};
            }
            prev = s;
        }
        double[] last = history.peekLast();
        return new double[]{last[1], last[2]};
    }

    private static double clamp(double v, double min, double max) {
        return max < min ? (min + max) / 2 : Math.max(min, Math.min(max, v));
    }

    /** Cada Batalla Oficial: el programa principal la suma al récord del Digimon y la comenta. */
    /**
     * ARENA 2 vs 2 online (pedido del usuario): {@code before} se llama antes de
     * abrir la pelea con un "empezar" (el programa principal hace cruzar a tu
     * puesto 2 por su portal y luego empieza); {@code after}, al cerrarla (vuelve).
     */
    public void setArenaHooks(Consumer<Runnable> before, Runnable after) {
        this.arenaBefore = before;
        this.arenaAfter = after;
        if (battleUi != null) battleUi.setArenaHooks(before, after);
    }

    private Consumer<Runnable> arenaBefore;
    private Runnable arenaAfter;

    public void setOnBattleResult(Consumer<OfficialBattleResult> listener) {
        this.onBattleResult = listener;
        if (battleUi != null) battleUi.setOnResult(listener);
    }

    /**
     * Hacia dónde mira y cuadro de animación de un avatar, deducidos de cuánto
     * se movió en este frame (el servidor solo manda posiciones). Solo
     * izquierda/derecha (decisión del usuario): al ir derecho arriba o abajo
     * conserva hacia dónde miraba.
     */
    private static final class AvatarAnim {
        private static final double MOVING_THRESHOLD = 0.05;
        /** 6 cuadros por ciclo (dos pasos): un cuadro cada 110 ms. */
        private static final long STEP_NANOS = 110_000_000L;
        /**
         * El dibujo alcanza la posición del servidor un instante antes de que
         * llegue la siguiente foto: sin este margen el avatar "parpadearía" a
         * quieto 10 veces por segundo mientras camina.
         */
        private static final long STOP_GRACE_NANOS = 150_000_000L;

        boolean facingRight = false;
        int frame = 0;
        private long walkStart = -1;
        private long lastMove = -1;

        void update(double dx, double dy, long now) {
            if (Math.hypot(dx, dy) < MOVING_THRESHOLD) {
                if (walkStart >= 0 && now - lastMove < STOP_GRACE_NANOS) return; // sigue el paso un instante
                frame = 0;
                walkStart = -1;
                return;
            }
            lastMove = now;
            if (Math.abs(dx) > MOVING_THRESHOLD * 0.5) facingRight = dx > 0;
            if (walkStart < 0) walkStart = now;
            frame = 1 + (int) (((now - walkStart) / STEP_NANOS) % AvatarSprites.WALK_FRAMES);
        }
    }
}
