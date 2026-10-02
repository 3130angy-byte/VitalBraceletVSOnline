package org.example.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputDialog;
import javafx.scene.image.Image;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Polygon;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.DirectoryChooser;
import javafx.stage.Popup;
import javafx.stage.Screen;
import javafx.stage.Window;

import org.example.Edition;
import org.example.lab.LabWindow;
import org.example.animation.VPetStageTier;
import org.example.behavior.CompanionController;
import org.example.behavior.VPetMovementMode;
import org.example.chat.AiConversationController;
import org.example.chat.AssistantCommands;
import org.example.chat.AssistantSettings;
import org.example.chat.ChatPanel;
import org.example.chat.MusicControl;
import org.example.chat.NewsService;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Menú del Digimon, organizado en dos páginas (diseño del usuario):
 *  - V-PET: Batalla, Movimiento, Digimon + "ASISTENTE >" (0.0.3: sin crianza,
 *    Cuidado y Entrenamiento se quitaron; Batalla abre su subpágina: Aleatoria y VS
 *    Online).
 *  - ASISTENTE: Hablar, Abrir programa, Música, Funciones + "← V-PET".
 * Cada categoría abre una subpágina con sus opciones y "← VOLVER".
 *
 * Los botones del asistente actúan AL INSTANTE (sin pasar por la IA, que
 * tarda 30-80s en esta laptop) y el Digimon confirma con announce(). Lo que
 * sí necesita a la IA (noticias, recordatorio) pasa por el chat normal.
 *
 * Se muestra al costado del Digimon con una flechita que lo señala; si no
 * cabe a la derecha, se abre a la izquierda.
 */
public class VPetMenu {

    private enum Page { VPET, BATALLA, MOVIMIENTO, ASISTENTE, PROGRAMAS, MUSICA, FUNCIONES, AJUSTES }

    // Estilo del diseño: panel oscuro, título turquesa, texto blanco en mayúsculas.
    private static final String PANEL_BG = "#26292e";
    private static final String PANEL_BORDER = "#3b4249";
    private static final Color TITLE_COLOR = Color.web("#6fd3e0");
    private static final String HOVER_BG = "#343a41";
    private static final double PANEL_WIDTH = 270;
    private static final double ARROW_SIZE = 10;
    private static final Font TITLE_FONT = Font.font("Consolas", FontWeight.BOLD, 16);
    private static final Font ITEM_FONT = Font.font("Consolas", FontWeight.BOLD, 13);
    private static final Font SMALL_FONT = Font.font("Consolas", 11);

    private final Window ownerWindow;
    private final CompanionController companion;
    private final AiConversationController aiController;
    private final ChatPanel chatPanel;
    private VPetStageTier stageTier;
    private final Popup popup = new Popup();
    private final StackPane root = new StackPane();
    private final Runnable onBattleRandom;
    private final Runnable onVsOnline;
    private final Runnable onOpenInfoPanel;
    private final Runnable onChatOpening;
    private final Runnable onRetire;
    private final Runnable onArena;
    private Page currentPage = Page.VPET;
    private boolean arrowOnLeft = true;

    public VPetMenu(Window ownerWindow, CompanionController companion, VPetStageTier stageTier,
                    AiConversationController aiController, Runnable onBattleRandom, Runnable onVsOnline,
                    Runnable onOpenInfoPanel, Runnable onChatOpening, Runnable onRetire, Runnable onArena) {
        this.onVsOnline = onVsOnline;
        this.onRetire = onRetire;
        this.onArena = onArena;
        this.ownerWindow = ownerWindow;
        this.companion = companion;
        this.stageTier = stageTier;
        this.aiController = aiController;
        this.chatPanel = new ChatPanel(ownerWindow, aiController);
        this.onBattleRandom = onBattleRandom;
        this.onOpenInfoPanel = onOpenInfoPanel;
        this.onChatOpening = onChatOpening;
        popup.getContent().setAll(root);
        // Un Popup no trae la clase ".root" del tema: sin ella los botones no encuentran sus
        // colores base y JavaFX avisa "Could not resolve '-fx-text-base-color'" (inofensivo).
        root.getStyleClass().add("root");
        popup.setAutoHide(true);
        showPage(Page.VPET);
    }

    public void setStageTier(VPetStageTier stageTier) {
        this.stageTier = stageTier;
        showPage(currentPage);
    }

    public void openChat(double x, double y) {
        onChatOpening.run();
        chatPanel.open(x, y);
    }

    public void setDigimonName(String name) {
        chatPanel.setDigimonName(name);
    }

    public void setDigimonNameSpriteImage(Image image) {
        chatPanel.setDigimonNameSpriteImage(image);
    }

    /** Abre/cierra el menú al costado del Digimon. Siempre abre en la página V-PET. */
    public void toggle(Window pet) {
        if (popup.isShowing()) {
            popup.hide();
            return;
        }
        Rectangle2D screen = Screen.getPrimary().getVisualBounds();
        double rightX = pet.getX() + pet.getWidth() + 4;
        arrowOnLeft = rightX + PANEL_WIDTH + ARROW_SIZE <= screen.getMaxX();
        showPage(Page.VPET);
        root.applyCss();
        root.layout();
        double height = root.prefHeight(-1);
        double x = arrowOnLeft ? rightX : pet.getX() - PANEL_WIDTH - ARROW_SIZE - 4;
        double y = pet.getY() + pet.getHeight() / 2.0 - height / 2.0;
        y = Math.max(screen.getMinY(), Math.min(y, screen.getMaxY() - height));
        popup.show(ownerWindow, x, y);
    }

    // ---------------------------------------------------------------------
    // Páginas

    private void showPage(Page page) {
        currentPage = page;
        VBox panel = switch (page) {
            case VPET -> vpetPage();
            case BATALLA -> batallaPage();
            case MOVIMIENTO -> movimientoPage();
            case ASISTENTE -> asistentePage();
            case PROGRAMAS -> programasPage();
            case MUSICA -> musicaPage();
            case FUNCIONES -> funcionesPage();
            case AJUSTES -> ajustesPage();
        };
        root.getChildren().setAll(withArrow(panel));
        if (popup.isShowing()) popup.sizeToScene();
    }

    private VBox vpetPage() {
        GridPane grid = new GridPane();
        grid.setHgap(6);
        grid.setVgap(6);
        grid.add(tile("⚔", "#d8dde3", "BATALLA", true, () -> showPage(Page.BATALLA)), 0, 0);
        grid.add(tile("➜", "#8fd6ff", "MOVIMIENTO", true, () -> showPage(Page.MOVIMIENTO)), 1, 0);
        grid.add(tile("☻", "#b48cff", "DIGIMON", true, () -> { popup.hide(); onOpenInfoPanel.run(); }), 0, 1);
        // Devuelve al Digimon al Vital Bracelet con el saldo de sus batallas (pide confirmación).
        grid.add(tile("⇪", "#ffb35c", "RETIRAR", true, () -> { popup.hide(); onRetire.run(); }), 1, 1);
        // Laboratorio: ventana aparte; el Digimon sigue en el escritorio mientras está abierta.
        grid.add(tile("⚗", "#7fe0a8", "LAB", true, () -> { popup.hide(); LabWindow.open(); }), 0, 2);

        // 0.0.3.1 (probadores) no tiene asistente ni chat IA (decisión del usuario).
        if (!Edition.ASSISTANT) {
            Label version = new Label("versión " + Edition.VERSION);
            version.setTextFill(Color.web("#7c8691"));
            version.setFont(SMALL_FONT);
            return panel("V-PET", grid, rightAligned(version));
        }
        Button toAssistant = pillButton("ASISTENTE ›", () -> showPage(Page.ASISTENTE));
        return panel("V-PET", grid, rightAligned(toAssistant));
    }

    private VBox batallaPage() {
        // Batalla aleatoria sigue pidiendo Child o superior; el VS Online acepta cualquier etapa.
        boolean canBattle = stageTier == VPetStageTier.CHILD_PLUS;
        return panel("BATALLA", list(
                        row("⚔", "#d8dde3", "ALEATORIA", canBattle, () -> { popup.hide(); onBattleRandom.run(); }),
                        row("✦", "#ffb35c", "ARENA 2 VS 2", canBattle, () -> { popup.hide(); onArena.run(); }),
                        row("◎", "#8fd6ff", "VS ONLINE", true, () -> { popup.hide(); onVsOnline.run(); })),
                backButton(Page.VPET));
    }

    private VBox movimientoPage() {
        return panel("MOVIMIENTO", list(
                        row("■", "#c9ced6", "QUIETO", true, () -> selectMode(VPetMovementMode.QUIETO)),
                        row("↔", "#8fd6ff", "BARRA", true, () -> selectMode(VPetMovementMode.BARRA)),
                        row("✥", "#8fd6ff", "LIBRE", true, () -> selectMode(VPetMovementMode.ABSOLUTO_LIBRE))),
                backButton(Page.VPET));
    }

    private VBox asistentePage() {
        return panel("ASISTENTE", list(
                        row("✉", "#8fd6ff", "HABLAR", true, () -> {
                            popup.hide();
                            onChatOpening.run();
                            chatPanel.toggle(ownerWindow.getX(), ownerWindow.getY() - 200);
                        }),
                        row("▣", "#8fb8ff", "ABRIR PROGRAMA", true, () -> showPage(Page.PROGRAMAS)),
                        row("♪", "#ff7ad9", "MÚSICA", true, () -> showPage(Page.MUSICA)),
                        row("⚙", "#b8c0c8", "FUNCIONES", true, () -> showPage(Page.FUNCIONES))),
                leftAligned(pillButton("‹ V-PET", () -> showPage(Page.VPET))));
    }

    private VBox programasPage() {
        VBox items = list();
        for (AssistantCommands.AppEntry app : AssistantCommands.MENU_APPS) {
            items.getChildren().add(row("▸", "#8fb8ff", app.label().toUpperCase(), true, () -> {
                popup.hide();
                try {
                    String name = AssistantCommands.launchApp(app.key());
                    aiController.announce("¡Listo! Abrí " + name + ".");
                } catch (IOException ex) {
                    aiController.announce("Uy, no pude abrir " + app.label() + ". ¿Está instalado?");
                }
            }));
        }
        return panel("ABRIR PROGRAMA", items, backButton(Page.ASISTENTE));
    }

    private VBox musicaPage() {
        return panel("MÚSICA", list(
                        row("♪", "#ff7ad9", "PONER MÚSICA", true, () -> {
                            popup.hide();
                            try {
                                Optional<String> song = MusicControl.playFromFolder(null);
                                aiController.announce(song.map(s -> "¡Puse \"" + s + "\"!")
                                        .orElse("No encontré canciones en tu carpeta de música. Puedes elegirla en Funciones > Ajustes."));
                            } catch (IOException ex) {
                                aiController.announce("Uy, no pude poner música.");
                            }
                        }),
                        row("❚❚", "#c9ced6", "PAUSAR / SEGUIR", true, () -> media(MusicAction.PLAY_PAUSE)),
                        row("⏭", "#c9ced6", "SIGUIENTE", true, () -> media(MusicAction.NEXT)),
                        row("⏮", "#c9ced6", "ANTERIOR", true, () -> media(MusicAction.PREVIOUS)),
                        row("⌕", "#ff6b6b", "BUSCAR EN YOUTUBE", true, () -> {
                            popup.hide();
                            ask("Buscar en YouTube", "¿Qué quieres escuchar?").ifPresent(q -> {
                                try {
                                    MusicControl.searchYouTube(q);
                                    aiController.announce("¡Abrí YouTube con \"" + q + "\"! Elige la que quieras.");
                                } catch (IOException ex) {
                                    aiController.announce("Uy, no pude abrir YouTube.");
                                }
                            });
                        })),
                backButton(Page.ASISTENTE));
    }

    private enum MusicAction { PLAY_PAUSE, NEXT, PREVIOUS }

    private void media(MusicAction action) {
        popup.hide();
        try {
            switch (action) {
                case PLAY_PAUSE -> MusicControl.playPause();
                case NEXT -> MusicControl.next();
                case PREVIOUS -> MusicControl.previous();
            }
        } catch (IOException ex) {
            aiController.announce("Uy, no pude controlar la música.");
        }
    }

    private VBox funcionesPage() {
        return panel("FUNCIONES", list(
                        row("◷", "#ffd166", "¿QUÉ HORA ES?", true, () -> {
                            popup.hide();
                            Locale es = Locale.forLanguageTag("es");
                            LocalDateTime now = LocalDateTime.now();
                            aiController.announce("Son las " + now.format(DateTimeFormatter.ofPattern("HH:mm", es))
                                    + ", " + now.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", es)) + ".");
                        }),
                        row("☷", "#8fd6ff", "NOTICIAS DE HOY", true, () -> {
                            popup.hide();
                            aiController.announce("¡Voy a ver las noticias! Dame un momento...");
                            aiController.userSays("¿Qué noticias hay hoy?");
                        }),
                        row("⏰", "#ff9f6b", "RECORDATORIO", true, () -> {
                            popup.hide();
                            ask("Recordatorio", "¿Qué te recuerdo?").ifPresent(what ->
                                    ask("Recordatorio", "¿En cuántos minutos?").ifPresent(minutes -> {
                                        if (minutes.trim().matches("\\d{1,4}")) {
                                            aiController.userSays("recuérdame " + what + " en " + minutes.trim() + " minutos");
                                        } else {
                                            aiController.announce("Necesito los minutos en número, como 20.");
                                        }
                                    }));
                        }),
                        row("⚙", "#b8c0c8", "AJUSTES", true, () -> showPage(Page.AJUSTES))),
                backButton(Page.ASISTENTE));
    }

    private VBox ajustesPage() {
        ComboBox<String> country = new ComboBox<>();
        Map<String, String> countries = NewsService.COUNTRY_NAMES;
        countries.forEach((code, name) -> country.getItems().add(name));
        country.setValue(countries.getOrDefault(AssistantSettings.newsCountry(), "Perú"));
        country.setMaxWidth(Double.MAX_VALUE);

        CheckBox proactive = new CheckBox("Puede comentarme noticias por su cuenta");
        proactive.setSelected(AssistantSettings.proactiveInternet());
        proactive.setTextFill(Color.WHITE);
        proactive.setFont(SMALL_FONT);
        proactive.setWrapText(true);

        final String[] musicFolder = {AssistantSettings.musicFolder().toString()};
        Label folderLabel = smallLabel(musicFolder[0]);
        Button pickFolder = pillButton("ELEGIR CARPETA", () -> chooseFolder("Carpeta de música", musicFolder, folderLabel));

        // Rivales de Batalla aleatoria: la VS DIM solo trae los sprites de su Digimon (ver RivalDimPool).
        final String[] rivalFolder = {AssistantSettings.rivalDimFolder().map(Object::toString).orElse("")};
        Label rivalLabel = smallLabel(rivalFolder[0].isEmpty() ? "(sin elegir)" : rivalFolder[0]);
        // Se guarda en cuanto se elige: antes había que pulsar GUARDAR, y si el menú se
        // cerraba al abrir el selector de carpetas, la carpeta se perdía sin avisar.
        Button pickRivals = pillButton("ELEGIR CARPETA", () -> {
            if (chooseFolder("Carpeta con DIM cards rivales (también se revisan sus subcarpetas)", rivalFolder, rivalLabel)) {
                AssistantSettings.saveRivalFolder(rivalFolder[0]);
                aiController.announce("¡Listo! Buscaré rivales en esa carpeta.");
            }
        });

        Button save = pillButton("GUARDAR", () -> {
            String code = countries.entrySet().stream()
                    .filter(e -> e.getValue().equals(country.getValue()))
                    .map(Map.Entry::getKey).findFirst().orElse("PE");
            AssistantSettings.save(code, proactive.isSelected(), AssistantSettings.proactiveNewsMinutes(),
                    musicFolder[0], rivalFolder[0]);
            popup.hide();
            aiController.announce("¡Listo! Guardé tus ajustes.");
        });

        VBox body = list(smallLabel("PAÍS DE LAS NOTICIAS"), country, proactive,
                smallLabel("CARPETA DE MÚSICA"), folderLabel, leftAligned(pickFolder),
                smallLabel("CARPETA DE DIM CARDS RIVALES (BATALLA)"), rivalLabel, leftAligned(pickRivals));
        HBox footer = new HBox(8, backButton(Page.FUNCIONES), spacer(), save);
        footer.setAlignment(Pos.CENTER_LEFT);
        return panel("AJUSTES", body, footer);
    }

    /** true si el usuario eligió una carpeta. El menú no se cierra mientras el selector está abierto. */
    private boolean chooseFolder(String title, String[] holder, Label label) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(title);
        File current = new File(holder[0]);
        if (current.isDirectory()) chooser.setInitialDirectory(current);
        popup.setAutoHide(false); // sin esto el menú se cerraba al perder el foco y se perdía lo elegido
        File chosen;
        try {
            chosen = chooser.showDialog(ownerWindow);
        } finally {
            popup.setAutoHide(true);
        }
        if (chosen == null) return false;
        holder[0] = chosen.getAbsolutePath();
        label.setText(holder[0]);
        return true;
    }

    private void selectMode(VPetMovementMode mode) {
        companion.setMode(mode);
        popup.hide();
    }

    // ---------------------------------------------------------------------
    // Piezas visuales

    private VBox panel(String title, Node body, Node footer) {
        Label titleLabel = new Label(title);
        titleLabel.setTextFill(TITLE_COLOR);
        titleLabel.setFont(TITLE_FONT);

        VBox panel = new VBox(8, titleLabel, separator(), body, separator(), footer);
        panel.setPadding(new Insets(10, 12, 10, 12));
        panel.setPrefWidth(PANEL_WIDTH);
        panel.setMaxWidth(PANEL_WIDTH);
        panel.setStyle("-fx-background-color: " + PANEL_BG + "; -fx-background-radius: 8; "
                + "-fx-border-color: " + PANEL_BORDER + "; -fx-border-radius: 8; -fx-border-width: 1.5;");
        return panel;
    }

    /** Flechita hacia el Digimon, del lado donde está. */
    private Node withArrow(VBox panel) {
        Polygon arrow = arrowOnLeft
                ? new Polygon(ARROW_SIZE, 0, 0, ARROW_SIZE, ARROW_SIZE, ARROW_SIZE * 2)
                : new Polygon(0, 0, ARROW_SIZE, ARROW_SIZE, 0, ARROW_SIZE * 2);
        arrow.setFill(Color.web(PANEL_BG));
        arrow.setStroke(Color.web(PANEL_BORDER));
        arrow.setTranslateX(arrowOnLeft ? 1.5 : -1.5); // tapa el borde del panel en la unión
        HBox box = arrowOnLeft ? new HBox(arrow, panel) : new HBox(panel, arrow);
        box.setAlignment(Pos.CENTER);
        // viewOrder (no toFront): toFront reordena los hijos del HBox y mandaba
        // la flecha al lado equivocado. Solo se quiere dibujarla encima del borde.
        arrow.setViewOrder(-1);
        return box;
    }

    private Region separator() {
        Region line = new Region();
        line.setPrefHeight(2);
        line.setMaxWidth(Double.MAX_VALUE);
        line.setStyle("-fx-background-color: linear-gradient(to right, #6fd3e0 0, #6fd3e0 4, #2f5f66 4, "
                + "#2f5f66 96%, #6fd3e0 96%);");
        return line;
    }

    /** Casilla de la página V-PET: ícono arriba, nombre abajo. */
    private Button tile(String icon, String iconColor, String text, boolean enabled, Runnable action) {
        Label iconLabel = new Label(icon);
        iconLabel.setFont(Font.font("Segoe UI Symbol", FontWeight.BOLD, 22));
        iconLabel.setTextFill(Color.web(iconColor));
        Button b = baseButton(text, enabled, action);
        b.setGraphic(iconLabel);
        b.setContentDisplay(ContentDisplay.TOP);
        b.setPrefWidth((PANEL_WIDTH - 24 - 6) / 2);
        b.setPrefHeight(64);
        return b;
    }

    /** Fila de las demás páginas: ícono a la izquierda, texto a la derecha. */
    private Button row(String icon, String iconColor, String text, boolean enabled, Runnable action) {
        Label iconLabel = new Label(icon);
        iconLabel.setFont(Font.font("Segoe UI Symbol", FontWeight.BOLD, 15));
        iconLabel.setTextFill(Color.web(iconColor));
        iconLabel.setMinWidth(24);
        iconLabel.setAlignment(Pos.CENTER);
        Button b = baseButton(text, enabled, action);
        b.setGraphic(iconLabel);
        b.setGraphicTextGap(8);
        b.setAlignment(Pos.CENTER_LEFT);
        b.setMaxWidth(Double.MAX_VALUE);
        return b;
    }

    private Button baseButton(String text, boolean enabled, Runnable action) {
        Button b = new Button(text);
        b.setFont(ITEM_FONT);
        b.setTextFill(Color.WHITE);
        String normal = "-fx-background-color: transparent; -fx-background-radius: 6; -fx-cursor: hand;";
        String hover = "-fx-background-color: " + HOVER_BG + "; -fx-background-radius: 6; -fx-cursor: hand;";
        b.setStyle(normal);
        b.setOnMouseEntered(e -> b.setStyle(hover));
        b.setOnMouseExited(e -> b.setStyle(normal));
        b.setDisable(!enabled);
        b.setOnAction(e -> action.run());
        return b;
    }

    private Button pillButton(String text, Runnable action) {
        Button b = new Button(text);
        b.setFont(ITEM_FONT);
        b.setTextFill(Color.WHITE);
        String normal = "-fx-background-color: #1d2024; -fx-background-radius: 6; -fx-border-color: #5a6470; "
                + "-fx-border-radius: 6; -fx-cursor: hand; -fx-padding: 3 10 3 10;";
        String hover = normal.replace("#1d2024", HOVER_BG);
        b.setStyle(normal);
        b.setOnMouseEntered(e -> b.setStyle(hover));
        b.setOnMouseExited(e -> b.setStyle(normal));
        b.setOnAction(e -> action.run());
        return b;
    }

    private Button backButton(Page target) {
        return pillButton("‹ VOLVER", () -> showPage(target));
    }

    private VBox list(Node... items) {
        VBox box = new VBox(2, items);
        box.setFillWidth(true);
        return box;
    }

    private HBox rightAligned(Node n) {
        HBox box = new HBox(n);
        box.setAlignment(Pos.CENTER_RIGHT);
        return box;
    }

    private HBox leftAligned(Node n) {
        HBox box = new HBox(n);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private Region spacer() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    private Label smallLabel(String text) {
        Label l = new Label(text);
        l.setTextFill(Color.web("#aab3bc"));
        l.setFont(SMALL_FONT);
        l.setWrapText(true);
        return l;
    }

    private Optional<String> ask(String title, String question) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle(title);
        dialog.setHeaderText(null);
        dialog.setContentText(question);
        dialog.initOwner(ownerWindow);
        return dialog.showAndWait().map(String::trim).filter(s -> !s.isEmpty());
    }
}
