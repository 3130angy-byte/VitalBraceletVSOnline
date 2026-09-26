package org.example.battle;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.transform.Scale;
import javafx.util.Duration;

import org.example.ui.NameSpriteBanner;

/**
 * Contenido de UNA de las dos pantallas del coliseo: rostro del Digimon,
 * sprite NAME real y barra de vida estilo Vital Bracelet Arena (insignia
 * circular de atributo sobre el extremo izquierdo de la barra, sin número
 * de HP -- decisión explícita del usuario).
 *
 * Se diseña en "unidades del fondo" (píxeles nativos del PNG del coliseo,
 * ver DESIGN_W/DESIGN_H) y se escala entero con un Scale, igual que el
 * fondo -- así sobrevive a cualquier tamaño de ventana sin recalcular
 * posiciones internas.
 *
 * El rostro es un recorte heurístico del IDLE_1 (la DIM no trae un sprite
 * de retrato): de la caja de píxeles opacos se toma un cuadrado del 70%
 * del lado menor, anclado ARRIBA y centrado en los píxeles de las filas
 * superiores (donde suele estar la cabeza); si el sprite es ancho
 * (cuadrúpedo), anclado al FRENTE -- los sprites DIM miran a la izquierda
 * por defecto. Sprites de <= 20px de alto se
 * muestran completos: ahí el cuerpo ya es el rostro.
 */
public class BattleScreenHud {

    /** Tamaño útil (sin tocar el marco) de cada pantalla, en píxeles nativos del PNG del coliseo. */
    public static final double DESIGN_W = 246;
    public static final double DESIGN_H = 150;

    private static final double PAD = 8;
    private static final double COLUMN_GAP = 8;
    private static final double PORTRAIT_BOX = 96;
    private static final double PORTRAIT_INSET = 6;
    private static final double NAME_H = 30;      // 2x el alto nativo del sprite NAME (15px)
    private static final double NAME_TO_BAR_GAP = 10;
    private static final double BADGE_D = 34;
    private static final double BAR_H = 18;
    private static final double BAR_BORDER = 2;
    private static final double LABEL_H = 18;
    private static final double HP_ANIM_SECONDS = 0.35;

    private static final Color PANEL_FILL = Color.rgb(8, 16, 42);
    private static final Color PANEL_STROKE = Color.rgb(90, 184, 255);
    private static final Color OUTLINE = Color.rgb(10, 10, 14);
    private static final Color BAR_TRACK = Color.rgb(24, 32, 30);

    private final Pane root = new Pane();
    private final Rectangle hpFill;
    private final Rectangle hpShine;
    private final double hpFillMaxW;
    private final NameSpriteBanner nameBanner;
    private Timeline hpTimeline;

    /**
     * @param portraitOnRight true para la pantalla del rival (diseño espejado, rostro hacia el centro).
     * @param faceRight       convención del proyecto: mirar a la derecha = scaleX -1.
     */
    public BattleScreenHud(Image idleSprite, Image nativeNameImage, int attribute,
                           boolean portraitOnRight, boolean faceRight) {
        root.setPrefSize(DESIGN_W, DESIGN_H);
        root.setMinSize(DESIGN_W, DESIGN_H);
        root.setMaxSize(DESIGN_W, DESIGN_H);
        root.setPickOnBounds(false);

        double portraitX = portraitOnRight ? DESIGN_W - PAD - PORTRAIT_BOX : PAD;
        double portraitY = (DESIGN_H - PORTRAIT_BOX) / 2.0;
        double colW = DESIGN_W - 2 * PAD - PORTRAIT_BOX - COLUMN_GAP;
        double colX = portraitOnRight ? PAD : PAD + PORTRAIT_BOX + COLUMN_GAP;

        // ---- Rostro ----
        Rectangle portraitFrame = new Rectangle(portraitX, portraitY, PORTRAIT_BOX, PORTRAIT_BOX);
        portraitFrame.setArcWidth(12);
        portraitFrame.setArcHeight(12);
        portraitFrame.setFill(PANEL_FILL);
        portraitFrame.setStroke(PANEL_STROKE);
        portraitFrame.setStrokeWidth(2);

        double innerBox = PORTRAIT_BOX - 2 * PORTRAIT_INSET;
        ImageView portraitView = new ImageView(cropFace(idleSprite));
        portraitView.setSmooth(false);
        portraitView.setPreserveRatio(true);
        portraitView.setFitWidth(innerBox);
        portraitView.setFitHeight(innerBox);
        portraitView.setScaleX(faceRight ? -1 : 1);
        StackPane portraitHolder = new StackPane(portraitView);
        portraitHolder.setPrefSize(innerBox, innerBox);
        portraitHolder.setLayoutX(portraitX + PORTRAIT_INSET);
        portraitHolder.setLayoutY(portraitY + PORTRAIT_INSET);
        portraitHolder.setClip(new Rectangle(innerBox, innerBox));

        // ---- Columna: nombre + barra + atributo ----
        double columnH = NAME_H + NAME_TO_BAR_GAP + BADGE_D + LABEL_H;
        double nameY = (DESIGN_H - columnH) / 2.0;
        double badgeY = nameY + NAME_H + NAME_TO_BAR_GAP;
        double barCenterY = badgeY + BADGE_D / 2.0;

        nameBanner = new NameSpriteBanner(nativeNameImage, colW, NAME_H);
        nameBanner.setLayoutX(colX);
        nameBanner.setLayoutY(nameY);

        // Barra: arranca bajo el centro de la insignia (que la tapa), el relleno
        // útil empieza en el borde derecho de la insignia para que la fracción
        // visible sea fiel al HP real.
        double barX = colX + BADGE_D / 2.0;
        double barW = colX + colW - barX;
        double barY = barCenterY - BAR_H / 2.0;
        Rectangle barOutline = new Rectangle(barX, barY, barW, BAR_H);
        barOutline.setArcWidth(BAR_H);
        barOutline.setArcHeight(BAR_H);
        barOutline.setFill(OUTLINE);

        double fillX = colX + BADGE_D - 1;
        double fillY = barY + BAR_BORDER;
        double fillH = BAR_H - 2 * BAR_BORDER;
        hpFillMaxW = colX + colW - BAR_BORDER - fillX;

        Rectangle track = new Rectangle(fillX, fillY, hpFillMaxW, fillH);
        track.setArcWidth(fillH);
        track.setArcHeight(fillH);
        track.setFill(BAR_TRACK);

        hpFill = new Rectangle(fillX, fillY, hpFillMaxW, fillH);
        hpFill.setArcWidth(fillH);
        hpFill.setArcHeight(fillH);

        hpShine = new Rectangle(fillX + 2, fillY + 2, hpFillMaxW - 4, 2);
        hpShine.setFill(Color.rgb(255, 255, 255, 0.45));
        hpShine.widthProperty().bind(Bindings.max(0, hpFill.widthProperty().subtract(4)));
        applyHpColor(1.0);

        StackPane badge = buildAttributeBadge(attribute);
        badge.setLayoutX(colX);
        badge.setLayoutY(badgeY);

        Label attributeLabel = new Label(attributeName(attribute).toUpperCase());
        attributeLabel.setFont(Font.font("Consolas", FontWeight.BOLD, 16));
        attributeLabel.setTextFill(attributeColor(attribute).brighter());
        attributeLabel.setLayoutX(fillX);
        attributeLabel.setLayoutY(barY + BAR_H + 1);

        root.getChildren().addAll(portraitFrame, portraitHolder, nameBanner,
                barOutline, track, hpFill, hpShine, badge, attributeLabel);
    }

    public Node getNode() { return root; }

    public void applyScale(double scale) {
        root.getTransforms().setAll(new Scale(scale, scale, 0, 0));
    }

    /** fraction en [0,1]. Animado, sin número visible. */
    public void setHpFraction(double fraction) {
        double clamped = Math.max(0, Math.min(1, fraction));
        if (hpTimeline != null) hpTimeline.stop();
        hpTimeline = new Timeline(new KeyFrame(Duration.seconds(HP_ANIM_SECONDS),
                new KeyValue(hpFill.widthProperty(), hpFillMaxW * clamped)));
        hpTimeline.setOnFinished(e -> hpFill.setVisible(clamped > 0));
        applyHpColor(clamped);
        hpTimeline.play();
    }

    public void stop() {
        nameBanner.stop();
        if (hpTimeline != null) hpTimeline.stop();
    }

    // ---------------------------------------------------------------------

    private void applyHpColor(double fraction) {
        Color top, bottom;
        if (fraction > 0.5)       { top = Color.rgb(150, 255, 120); bottom = Color.rgb(24, 190, 60); }
        else if (fraction > 0.25) { top = Color.rgb(255, 240, 120); bottom = Color.rgb(220, 170, 20); }
        else                      { top = Color.rgb(255, 140, 120); bottom = Color.rgb(210, 40, 40); }
        hpFill.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, top), new Stop(1, bottom)));
    }

    private StackPane buildAttributeBadge(int attribute) {
        Color base = attributeColor(attribute);
        Circle circle = new Circle(BADGE_D / 2.0);
        circle.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, base.brighter()), new Stop(1, base.darker())));
        circle.setStroke(OUTLINE);
        circle.setStrokeWidth(3);

        Circle ring = new Circle(BADGE_D / 2.0 - 4);
        ring.setFill(Color.TRANSPARENT);
        ring.setStroke(Color.rgb(255, 255, 255, 0.55));
        ring.setStrokeWidth(1.5);

        Label abbrev = new Label(attributeAbbrev(attribute));
        abbrev.setFont(Font.font("Consolas", FontWeight.BOLD, 16));
        abbrev.setTextFill(Color.WHITE);
        abbrev.setStyle("-fx-effect: dropshadow(one-pass-box, rgba(0,0,0,0.85), 2, 1, 0, 1);");

        StackPane badge = new StackPane(circle, ring, abbrev);
        badge.setAlignment(Pos.CENTER);
        badge.setPrefSize(BADGE_D, BADGE_D);
        return badge;
    }

    // Atributo DIM: 0=Null, 1=Virus, 2=Data, 3=Vaccine, 4=Free.
    static String attributeName(int attribute) {
        return switch (attribute) {
            case 1 -> "Virus";
            case 2 -> "Data";
            case 3 -> "Vaccine";
            case 4 -> "Free";
            default -> "Null";
        };
    }

    private static String attributeAbbrev(int attribute) {
        return switch (attribute) {
            case 1 -> "Vi";
            case 2 -> "Da";
            case 3 -> "Va";
            case 4 -> "Fr";
            default -> "--";
        };
    }

    /** Colores elegidos por nosotros (no extraídos de VB Arena) -- ajustables aquí. */
    private static Color attributeColor(int attribute) {
        return switch (attribute) {
            case 1 -> Color.rgb(170, 70, 230);  // Virus
            case 2 -> Color.rgb(40, 140, 250);  // Data
            case 3 -> Color.rgb(40, 190, 80);   // Vaccine
            case 4 -> Color.rgb(240, 160, 30);  // Free
            default -> Color.rgb(130, 130, 140);
        };
    }

    /** Recorte cuadrado arriba/al frente de la caja opaca del sprite. Si algo falla, sprite completo. */
    static Image cropFace(Image sprite) {
        PixelReader reader = sprite.getPixelReader();
        if (reader == null) return sprite;
        int w = (int) sprite.getWidth();
        int h = (int) sprite.getHeight();

        int minX = w, minY = h, maxX = -1, maxY = -1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if ((reader.getArgb(x, y) >>> 24) != 0) {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
            }
        }
        if (maxX < 0) return sprite;

        int boxW = maxX - minX + 1;
        int boxH = maxY - minY + 1;
        // Sprites diminutos (Child pequeños): el cuerpo completo YA es el rostro.
        int side = boxH <= FACE_MIN_SIDE
                ? Math.max(boxW, boxH)
                : Math.max(FACE_MIN_SIDE, (int) Math.round(Math.min(boxW, boxH) * FACE_FRACTION));
        side = Math.min(side, Math.min(w, h));

        // Centro horizontal = centro de los píxeles opacos de las filas superiores
        // (donde está la cabeza), no el centro de la caja completa.
        long sumX = 0, count = 0;
        int rowsEnd = Math.min(maxY, minY + side - 1);
        for (int y = minY; y <= rowsEnd; y++) {
            for (int x = minX; x <= maxX; x++) {
                if ((reader.getArgb(x, y) >>> 24) != 0) { sumX += x; count++; }
            }
        }
        int centerX = count > 0 ? (int) (sumX / count) : (minX + maxX) / 2;
        // Sprite ancho (cuadrúpedo, dragón): la cabeza va al FRENTE, y los sprites
        // DIM miran a la izquierda por defecto -> anclar al borde izquierdo.
        boolean wide = boxW > boxH * WIDE_RATIO;
        int cropX = wide ? minX : centerX - side / 2;
        cropX = Math.max(0, Math.min(w - side, cropX));
        int cropY = Math.max(0, Math.min(h - side, minY - FACE_TOP_MARGIN));
        return new WritableImage(reader, cropX, cropY, side, side);
    }

    private static final int FACE_MIN_SIDE = 20;
    private static final double FACE_FRACTION = 0.7;
    private static final int FACE_TOP_MARGIN = 1;
    private static final double WIDE_RATIO = 1.1;
}
