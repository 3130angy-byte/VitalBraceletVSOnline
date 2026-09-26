package org.example.chat;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Popup;
import javafx.stage.Screen;
import javafx.stage.Window;

/**
 * Chat con el Digimon (diseño del usuario, misma línea visual que VPetMenu):
 * panel oscuro con título turquesa, barra con puntitos para ARRASTRARLO,
 * cierre con borde verde, historial tipo terminal (usuario en blanco,
 * Digimon en verde) y fila "> [Escribe algo...] [ENVIAR]". Sin íconos de
 * usuario/Digimon (decisión del usuario).
 */
public class ChatPanel {

    private static final String PANEL_BG = "#26292e";
    private static final String PANEL_BORDER = "#3b4249";
    private static final String HISTORY_BG = "#16181b";
    private static final Color TITLE_COLOR = Color.web("#6fd3e0");
    private static final Color GREEN = Color.web("#39FF14");
    private static final Color USER_COLOR = Color.web("#e8eaed");
    private static final Font TITLE_FONT = Font.font("Consolas", FontWeight.BOLD, 16);
    private static final Font DIGIMON_FONT = Font.font("Consolas", 13);
    private static final Font USER_FONT = Font.font("Segoe UI", 13);
    private static final double WIDTH = 400;
    private static final double HISTORY_HEIGHT = 230;

    private final Window ownerWindow;
    private final Popup popup = new Popup();
    private final VBox historyBox = new VBox(10);
    private final ScrollPane historyScroll;
    private final VBox panel;
    private String digimonName = "";
    private Image nameSpriteImage;
    private double dragOffsetX, dragOffsetY;

    public ChatPanel(Window ownerWindow, AiConversationController aiController) {
        this.ownerWindow = ownerWindow;

        // ---- Barra de título: título, puntitos para arrastrar, cerrar ----
        Label title = new Label("CHAT CON EL DIGIMON");
        title.setTextFill(TITLE_COLOR);
        title.setFont(TITLE_FONT);

        GridPane dragHandle = dragDots();
        Button close = new Button("×");
        close.setFont(Font.font("Consolas", FontWeight.BOLD, 13));
        close.setTextFill(GREEN);
        close.setStyle("-fx-background-color: transparent; -fx-border-color: #39FF14; -fx-border-width: 1.5; "
                + "-fx-border-radius: 3; -fx-padding: 0 6 0 6; -fx-cursor: hand;");
        close.setOnAction(e -> popup.hide());

        HBox titleRow = new HBox(10, title, spacer(), dragHandle, spacer(), close);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        titleRow.setCursor(Cursor.MOVE);
        enableDrag(titleRow);

        // ---- Historial ----
        Label header = new Label("C:\\DIGIMON> chat_history.exe");
        header.setTextFill(USER_COLOR);
        header.setFont(Font.font("Consolas", 12));
        historyBox.getChildren().add(header);
        historyBox.setPadding(new Insets(10, 12, 10, 12));
        historyBox.setStyle("-fx-background-color: " + HISTORY_BG + ";");

        historyScroll = new ScrollPane(historyBox);
        historyScroll.setPrefHeight(HISTORY_HEIGHT);
        historyScroll.setFitToWidth(true);
        historyScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        historyScroll.setStyle("-fx-background: " + HISTORY_BG + "; -fx-background-color: " + HISTORY_BG + "; "
                + "-fx-background-radius: 8; -fx-border-color: transparent;");

        // ---- Fila de escritura ----
        Label prompt = new Label("›");
        prompt.setTextFill(GREEN);
        prompt.setFont(Font.font("Consolas", FontWeight.BOLD, 18));

        TextField input = new TextField();
        input.setPromptText("Escribe algo...");
        input.setFont(USER_FONT);
        input.setStyle("-fx-control-inner-background: " + HISTORY_BG + "; -fx-background-color: " + HISTORY_BG + "; "
                + "-fx-text-fill: #e8eaed; -fx-prompt-text-fill: #7d858e; -fx-background-radius: 6; "
                + "-fx-border-color: " + PANEL_BORDER + "; -fx-border-radius: 6; -fx-padding: 6 10 6 10;");
        HBox.setHgrow(input, Priority.ALWAYS);

        Button send = new Button("ENVIAR");
        send.setFont(Font.font("Consolas", FontWeight.BOLD, 13));
        send.setTextFill(TITLE_COLOR);
        String sendNormal = "-fx-background-color: #1d2024; -fx-background-radius: 6; -fx-border-color: #6fd3e0; "
                + "-fx-border-width: 1.5; -fx-border-radius: 6; -fx-padding: 5 14 5 14; -fx-cursor: hand;";
        send.setStyle(sendNormal);
        send.setOnMouseEntered(e -> send.setStyle(sendNormal.replace("#1d2024", "#343a41")));
        send.setOnMouseExited(e -> send.setStyle(sendNormal));

        Runnable doSend = () -> {
            String text = input.getText().trim();
            if (text.isEmpty()) return;
            appendUserMessage(text);
            input.clear();
            aiController.userSays(text);
        };
        send.setOnAction(e -> doSend.run());
        input.setOnAction(e -> doSend.run());

        // SOLO este listener -- si además se escucha addOnUserChatReplyListener,
        // cada respuesta directa se dispara DOS veces (userSays notifica ambos
        // eventos para el mismo mensaje) y aparece duplicada en el historial.
        aiController.addOnBubbleMessageListener(this::appendDigimonMessage);

        HBox inputRow = new HBox(8, prompt, input, send);
        inputRow.setAlignment(Pos.CENTER_LEFT);

        panel = new VBox(10, titleRow, separator(), historyScroll, inputRow);
        panel.setPadding(new Insets(12, 14, 14, 14));
        panel.setPrefWidth(WIDTH);
        panel.setStyle("-fx-background-color: " + PANEL_BG + "; -fx-background-radius: 10; "
                + "-fx-border-color: " + PANEL_BORDER + "; -fx-border-radius: 10; -fx-border-width: 1.5;");

        popup.getContent().add(panel);
        popup.setAutoHide(true);
    }

    public void setDigimonName(String name) {
        this.digimonName = name != null ? name : "";
    }

    public void setDigimonNameSpriteImage(Image image) {
        this.nameSpriteImage = image;
    }

    private void appendUserMessage(String message) {
        Text text = new Text(message);
        text.setFill(USER_COLOR);
        text.setFont(USER_FONT);
        addToHistory(new TextFlow(text));
    }

    private void appendDigimonMessage(String message) {
        TextFlow flow = new TextFlow();
        // Menciones de su nombre se muestran con el sprite NAME real, como antes.
        flow.getChildren().addAll(NameBadge.buildInlineNodesWithSprite(message, digimonName, nameSpriteImage, GREEN, DIGIMON_FONT));
        addToHistory(flow);
    }

    private void addToHistory(TextFlow flow) {
        flow.setLineSpacing(2);
        flow.maxWidthProperty().bind(historyScroll.widthProperty().subtract(40));
        historyBox.getChildren().add(flow);
        Platform.runLater(() -> historyScroll.setVvalue(1.0));
    }

    public void toggle(double x, double y) {
        if (popup.isShowing()) popup.hide();
        else open(x, y);
    }

    /** Abre en (x, y) pero siempre dentro de la pantalla. */
    public void open(double x, double y) {
        if (popup.isShowing()) return;
        Rectangle2D screen = Screen.getPrimary().getVisualBounds();
        panel.applyCss();
        panel.layout();
        double w = panel.prefWidth(-1);
        double h = panel.prefHeight(w);
        double cx = Math.max(screen.getMinX(), Math.min(x, screen.getMaxX() - w));
        double cy = Math.max(screen.getMinY(), Math.min(y, screen.getMaxY() - h));
        popup.show(ownerWindow, cx, cy);
    }

    // ---------------------------------------------------------------------

    private void enableDrag(HBox handle) {
        handle.setOnMousePressed(e -> {
            dragOffsetX = e.getScreenX() - popup.getX();
            dragOffsetY = e.getScreenY() - popup.getY();
        });
        handle.setOnMouseDragged(e -> {
            popup.setX(e.getScreenX() - dragOffsetX);
            popup.setY(e.getScreenY() - dragOffsetY);
        });
    }

    /** Puntitos de "arrastrar" del diseño (3 filas x 6). */
    private GridPane dragDots() {
        GridPane dots = new GridPane();
        dots.setHgap(4);
        dots.setVgap(4);
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 6; c++) {
                dots.add(new Circle(1.6, Color.web("#5a6470")), c, r);
            }
        }
        return dots;
    }

    private Region separator() {
        Region line = new Region();
        line.setPrefHeight(2);
        line.setMaxWidth(Double.MAX_VALUE);
        line.setStyle("-fx-background-color: linear-gradient(to right, #6fd3e0 0, #6fd3e0 70%, #2f5f66 100%);");
        return line;
    }

    private Region spacer() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }
}
