package org.example.chat;

import javafx.animation.AnimationTimer;
import javafx.animation.PauseTransition;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import javafx.scene.image.Image;

public class SpeechBubble {

    private static final double MAX_TEXT_WIDTH = 190;
    private static final double PADDING = 12;
    private static final double TAIL_HEIGHT = 14;
    private static final double TAIL_WIDTH = 16;
    private static final double CLOSE_RADIUS = 8;
    private static final double SECONDS_PER_WORD = 0.45;
    private static final double MIN_DISPLAY_SECONDS = 3.0;
    private static final double MAX_DISPLAY_SECONDS = 14.0;
    private static final double OFFSET_X = -20;
    private static final double OFFSET_Y = -90;

    private static final Color FILL = Color.rgb(255, 250, 225);
    private static final Color BORDER = Color.rgb(70, 55, 20);
    private static final Color TEXT_COLOR = Color.rgb(40, 30, 10);
    private static final Font TEXT_FONT = Font.font("Comic Sans MS", 12);

    private final Stage bubbleStage;
    private final Pane root;
    private final Rectangle body;
    private final Polygon tail;
    private final TextFlow textFlow;
    private final Circle closeCircle;
    private final Text closeX;
    private PauseTransition hideTimer;
    private AnimationTimer followTimer;
    private Runnable onBubbleClicked;
    private String digimonName = "";
    private Image nameSpriteImage;

    public void setDigimonNameSpriteImage(Image image) {
        this.nameSpriteImage = image;
    }
    public SpeechBubble() {
        root = new Pane();

        body = new Rectangle();
        body.setArcWidth(16);
        body.setArcHeight(16);
        body.setFill(FILL);
        body.setStroke(BORDER);
        body.setStrokeWidth(1.5);
        body.setCursor(Cursor.HAND);

        tail = new Polygon();
        tail.setFill(FILL);
        tail.setStroke(BORDER);
        tail.setStrokeWidth(1.5);
        tail.setMouseTransparent(true);

        textFlow = new TextFlow();
        textFlow.setMaxWidth(MAX_TEXT_WIDTH);
        textFlow.setMouseTransparent(true);

        closeCircle = new Circle(CLOSE_RADIUS, FILL);
        closeCircle.setStroke(BORDER);
        closeCircle.setStrokeWidth(1.2);
        closeCircle.setCursor(Cursor.HAND);

        closeX = new Text("\u00D7");
        closeX.setFont(Font.font(null, FontWeight.BOLD, 11));
        closeX.setFill(BORDER);
        closeX.setMouseTransparent(true);

        root.getChildren().addAll(body, tail, textFlow, closeCircle, closeX);

        body.setOnMouseClicked(e -> {
            if (onBubbleClicked != null) onBubbleClicked.run();
        });
        closeCircle.setOnMouseClicked(e -> {
            e.consume();
            hide();
        });

        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);

        bubbleStage = new Stage();
        bubbleStage.initStyle(StageStyle.TRANSPARENT);
        bubbleStage.setAlwaysOnTop(true);
        bubbleStage.setScene(scene);
    }

    public void setOnBubbleClicked(Runnable callback) {
        this.onBubbleClicked = callback;
    }

    public void setDigimonName(String name) {
        this.digimonName = name != null ? name : "";
    }

    /** La burbuja se reposiciona continuamente sobre petStage mientras esté visible. */
    public void show(String text, Stage petStage) {
        if (hideTimer != null) hideTimer.stop();
        if (followTimer != null) followTimer.stop();

        textFlow.getChildren().setAll(NameBadge.buildInlineNodesWithSprite(text, digimonName, nameSpriteImage, TEXT_COLOR, TEXT_FONT));
        textFlow.setLayoutX(PADDING);
        textFlow.setLayoutY(PADDING);

        root.applyCss();
        root.layout();

        double textW = Math.max(textFlow.getWidth(), 30);
        double textH = Math.max(textFlow.getHeight(), 16);
        double bodyW = textW + PADDING * 2;
        double bodyH = textH + PADDING * 2;

        body.setLayoutX(0);
        body.setLayoutY(0);
        body.setWidth(bodyW);
        body.setHeight(bodyH);

        double tailX = bodyW * 0.35;
        tail.getPoints().setAll(
                tailX - TAIL_WIDTH / 2, bodyH - 1,
                tailX + TAIL_WIDTH / 2, bodyH - 1,
                tailX - 3, bodyH + TAIL_HEIGHT
        );

        closeCircle.setLayoutX(0);
        closeCircle.setLayoutY(0);
        closeX.setLayoutX(-closeX.getLayoutBounds().getWidth() / 2);
        closeX.setLayoutY(closeX.getLayoutBounds().getHeight() / 4);

        bubbleStage.setWidth(bodyW + CLOSE_RADIUS);
        bubbleStage.setHeight(bodyH + TAIL_HEIGHT + CLOSE_RADIUS);

        repositionOver(petStage);
        bubbleStage.show();

        followTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                repositionOver(petStage);
            }
        };
        followTimer.start();

        int wordCount = Math.max(1, text.trim().split("\\s+").length);
        double seconds = clamp(wordCount * SECONDS_PER_WORD, MIN_DISPLAY_SECONDS, MAX_DISPLAY_SECONDS);

        hideTimer = new PauseTransition(Duration.seconds(seconds));
        hideTimer.setOnFinished(e -> hide());
        hideTimer.play();
    }

    private void repositionOver(Stage petStage) {
        bubbleStage.setX(petStage.getX() + OFFSET_X);
        bubbleStage.setY(petStage.getY() + OFFSET_Y);
    }

    public void hide() {
        if (hideTimer != null) hideTimer.stop();
        if (followTimer != null) followTimer.stop();
        bubbleStage.hide();
    }

    private double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}