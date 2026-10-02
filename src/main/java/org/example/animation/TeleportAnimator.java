package org.example.animation;

import javafx.animation.AnimationTimer;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Background;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

public class TeleportAnimator {

    private static final int CHAR_WIDTH = 64;
    private static final int CHAR_HEIGHT = 56;

    private static final double RUN_DISTANCE_THRESHOLD = 250;
    private static final double WALK_SPEED = 90;
    private static final double RUN_SPEED = 170;

    private static final double WALK_FRAME_SECONDS = 0.16;
    private static final double IDLE_FRAME_SECONDS = 0.45;
    private static final int IDLE_REPS = 2;

    // Distancia visible real entre el borde del Digimon y el borde mas
    // cercano del portal, restada del ancho REAL de su ventana -- ya no
    // un punto arbitrario que dejaba el portal encimado.
    private static final double PORTAL_CLEARANCE = 45;

    private static final double MATERIALIZE_FRAME_SECONDS = 0.09;
    private static final double LOOP_FRAME_SECONDS = 0.12;
    private static final double PORTAL_HOLD_AFTER_VANISH_SECONDS = 0.5;

    private static final double[] OPACITY_STEPS = {1.0, 0.9, 0.8, 0.7, 0.5, 0.3, 0.1, 0.0};
    private static final int FREEZE_STEP_INDEX = 5; // paso del 30% -> deja de alternar WALK
    private static final double FADE_STEP_SECONDS = 0.13;
    // Distancia recorrida DURANTE el desvanecimiento, ligada al ancho real
    // del portal (avanza "hacia su interior"), no un numero suelto.
    private static final double FADE_ADVANCE_DISTANCE = PortalSpriteSheet.FRAME_WIDTH * 0.5;

    private Stage portalStage;
    /** Ventana del Digimon: el portal siempre queda detrás de ella. */
    private Stage petStage;
    /** Al terminar de entrar, el portal queda abierto para que otro salga por él. */
    private boolean keepPortalOpen = false;
    /** Dónde quedó el portal de la entrada y hacia dónde caminaba quien entró. */
    private double openPortalLeft, openPortalTop, openDirX;

    /** La próxima entrada deja el portal abierto (ver playExitThroughOpenPortal). */
    public TeleportAnimator keepPortalOpen() {
        keepPortalOpen = true;
        return this;
    }
    private ImageView portalView;
    private PortalSpriteSheet portalSheet;
    private Timeline portalLoopTimeline;

    public static double centerX() {
        Rectangle2D b = Screen.getPrimary().getVisualBounds();
        return b.getMinX() + (b.getWidth() - CHAR_WIDTH) / 2.0;
    }

    /** Zona inferior, justo encima de la barra de tareas -- visualBounds ya la excluye (mismo criterio que Main.java). */
    public static double bottomY() {
        Rectangle2D b = Screen.getPrimary().getVisualBounds();
        return b.getMaxY() - CHAR_HEIGHT;
    }

    public void playEnter(ImageView view, Stage petStage, DimSpriteSet sprites, VPetStageTier stageTier, Runnable onComplete) {
        playEnter(view, petStage, sprites, stageTier, false, onComplete);
    }

    /**
     * settleFirst: el Digimon venía caminando (o haciendo otra acción) cuando
     * se pidió el portal. Quien llama YA detuvo ese movimiento; aquí se queda
     * en IDLE_1/IDLE_2 IDLE_REPS veces y recién entonces se lee su posición
     * para decidir dónde va el portal. Antes el paseo y esta animación movían
     * la misma ventana a la vez: se deslizaba hasta la esquina y volvía (bug
     * desde 0.0.2).
     */
    public void playEnter(ImageView view, Stage petStage, DimSpriteSet sprites, VPetStageTier stageTier,
                          boolean settleFirst, Runnable onComplete) {
        this.petStage = petStage;
        if (!settleFirst) {
            startEnter(view, petStage, sprites, onComplete);
            return;
        }
        view.setVisible(true);
        view.setOpacity(1.0);
        Timeline settle = new Timeline();
        double t = appendIdleReps(settle, view, sprites, 0);
        settle.getKeyFrames().add(new KeyFrame(Duration.seconds(t), e -> startEnter(view, petStage, sprites, onComplete)));
        settle.play();
    }

    private void startEnter(ImageView view, Stage petStage, DimSpriteSet sprites, Runnable onComplete) {
        view.setVisible(true);
        view.setOpacity(1.0);
        view.setScaleX(1.0);

        double startX = petStage.getX();
        double startY = petStage.getY();
        double targetX = centerX();
        double targetY = bottomY();
        double dist = Math.hypot(targetX - startX, targetY - startY);
        boolean shouldRun = dist > RUN_DISTANCE_THRESHOLD;

        SpriteRole frame1 = shouldRun ? SpriteRole.RUN_1 : SpriteRole.WALK_1;
        SpriteRole frame2 = shouldRun ? SpriteRole.RUN_2 : SpriteRole.WALK_2;
        if (shouldRun && !sprites.has(SpriteRole.RUN_1)) {
            frame1 = SpriteRole.IDLE_1;
            frame2 = SpriteRole.WALK_1;
        }
        double speed = shouldRun ? RUN_SPEED : WALK_SPEED;
        double travelSeconds = Math.max(0.3, dist / speed);

        double dirX = dist > 0.001 ? (targetX - startX) / dist : 1;
        double dirY = dist > 0.001 ? (targetY - startY) / dist : 0;
        applyFacing(view, dirX);

        SpriteRole finalFrame1 = frame1;
        SpriteRole finalFrame2 = frame2;

        AnimationTimer moveTimer = new AnimationTimer() {
            private long startTime = -1;
            private long lastFrameSwitch = -1;
            private boolean showFirst = true;

            @Override
            public void handle(long now) {
                if (startTime < 0) { startTime = now; lastFrameSwitch = now; }
                double elapsed = (now - startTime) / 1_000_000_000.0;
                double t = Math.min(1.0, elapsed / travelSeconds);

                petStage.setX(startX + (targetX - startX) * t);
                petStage.setY(startY + (targetY - startY) * t);

                if ((now - lastFrameSwitch) / 1_000_000_000.0 >= WALK_FRAME_SECONDS) {
                    showFirst = !showFirst;
                    view.setImage(sprites.get(showFirst ? finalFrame1 : finalFrame2));
                    lastFrameSwitch = now;
                }

                if (t >= 1.0) {
                    stop();
                    runIdlePhase(view, petStage, sprites, dirX, dirY, onComplete);
                }
            }
        };
        moveTimer.start();
    }

    private void runIdlePhase(ImageView view, Stage petStage, DimSpriteSet sprites,
                              double dirX, double dirY, Runnable onComplete) {
        Timeline timeline = new Timeline();
        double t = appendIdleReps(timeline, view, sprites, 0);

        double charCenterY = petStage.getY() + CHAR_HEIGHT / 2.0;
        double portalW = PortalSpriteSheet.FRAME_WIDTH;
        double portalH = PortalSpriteSheet.FRAME_HEIGHT;

        // Borde del portal mas cercano al Digimon, con clearance real.
        double portalLeft = dirX >= 0
                ? petStage.getX() + CHAR_WIDTH + PORTAL_CLEARANCE
                : petStage.getX() - PORTAL_CLEARANCE - portalW;
        double portalTop = charCenterY - portalH / 2.0;

        double entryPointX = dirX >= 0 ? portalLeft : portalLeft + portalW;
        double entryPointY = charCenterY;

        openPortalLeft = portalLeft;
        openPortalTop = portalTop;
        openDirX = dirX;
        timeline.getKeyFrames().add(new KeyFrame(Duration.seconds(t), e ->
                openPortalMaterialize(portalLeft, portalTop, -dirX, () -> {
                    startPortalLoop();
                    walkToEntryPoint(view, petStage, sprites, entryPointX, entryPointY, dirX, dirY, onComplete);
                })));

        timeline.play();
    }

    private double appendIdleReps(Timeline timeline, ImageView view, DimSpriteSet sprites, double startT) {
        double t = startT;
        int frames = IDLE_REPS * 2;
        for (int i = 0; i < frames; i++) {
            double time = t;
            SpriteRole role = (i % 2 == 0) ? SpriteRole.IDLE_1 : SpriteRole.IDLE_2;
            timeline.getKeyFrames().add(new KeyFrame(Duration.seconds(time), e -> view.setImage(sprites.get(role))));
            t += IDLE_FRAME_SECONDS;
        }
        return t;
    }

    /** Camina, a opacidad completa, hasta el borde/entrada del portal (portalEntryPoint). */
    private void walkToEntryPoint(ImageView view, Stage petStage, DimSpriteSet sprites,
                                  double entryX, double entryY, double dirX, double dirY, Runnable onComplete) {
        applyFacing(view, dirX);

        double startX = petStage.getX();
        double startY = petStage.getY();
        double entryStageX = entryX - CHAR_WIDTH / 2.0;
        double entryStageY = entryY - CHAR_HEIGHT / 2.0;
        double dist = Math.hypot(entryStageX - startX, entryStageY - startY);
        int walkFrames = Math.max(2, (int) Math.round((dist / WALK_SPEED) / WALK_FRAME_SECONDS));

        Timeline timeline = new Timeline();
        double t = 0;
        SpriteRole[] lastRoleHolder = { SpriteRole.WALK_1 };

        for (int i = 0; i < walkFrames; i++) {
            double frac = (i + 1) / (double) walkFrames;
            double time = t;
            SpriteRole role = (i % 2 == 0) ? SpriteRole.WALK_1 : SpriteRole.WALK_2;
            double x = startX + (entryStageX - startX) * frac;
            double y = startY + (entryStageY - startY) * frac;
            timeline.getKeyFrames().add(new KeyFrame(Duration.seconds(time), e -> {
                view.setImage(sprites.get(role));
                petStage.setX(x);
                petStage.setY(y);
            }));
            lastRoleHolder[0] = role;
            t += WALK_FRAME_SECONDS;
        }

        double fadeStart = t;
        timeline.getKeyFrames().add(new KeyFrame(Duration.seconds(fadeStart), e ->
                walkIntoPortalWhileFading(view, petStage, sprites, lastRoleHolder[0], dirX, dirY, onComplete)));

        timeline.play();
    }

    /**
     * FASE COMBINADA: una sola secuencia donde cada paso fija a la vez
     * posición X/Y, opacidad y sprite -- nunca dos mecanismos separados.
     * Avanza FADE_ADVANCE_DISTANCE mientras recorre los 8 pasos de
     * opacidad; deja de alternar WALK desde el paso del 30%.
     */
    private void walkIntoPortalWhileFading(ImageView view, Stage petStage, DimSpriteSet sprites,
                                           SpriteRole entryFrame, double dirX, double dirY, Runnable onComplete) {
        Timeline timeline = new Timeline();
        double baseX = petStage.getX();
        double baseY = petStage.getY();

        timeline.getKeyFrames().add(new KeyFrame(Duration.ZERO, e -> {
            view.setOpacity(OPACITY_STEPS[0]);
            view.setImage(sprites.get(entryFrame));
        }));

        for (int i = 1; i < OPACITY_STEPS.length; i++) {
            double time = i * FADE_STEP_SECONDS;
            double opacity = OPACITY_STEPS[i];
            double advanceFrac = i / (double) (OPACITY_STEPS.length - 1);
            boolean stillWalking = i < FREEZE_STEP_INDEX;
            boolean walkToggle = (i % 2 == 0);

            timeline.getKeyFrames().add(new KeyFrame(Duration.seconds(time), e -> {
                view.setOpacity(opacity);
                petStage.setX(baseX + dirX * FADE_ADVANCE_DISTANCE * advanceFrac);
                petStage.setY(baseY + dirY * FADE_ADVANCE_DISTANCE * advanceFrac);
                view.setImage(sprites.get(stillWalking ? (walkToggle ? SpriteRole.WALK_1 : SpriteRole.WALK_2) : entryFrame));
            }));
        }

        double lastStepTime = (OPACITY_STEPS.length - 1) * FADE_STEP_SECONDS;
        timeline.getKeyFrames().add(new KeyFrame(Duration.seconds(lastStepTime + 0.02), e -> {
            view.setVisible(false);
            view.setOpacity(1.0);
            view.setScaleX(1.0);
        }));

        timeline.getKeyFrames().add(new KeyFrame(Duration.seconds(lastStepTime + 0.02 + PORTAL_HOLD_AFTER_VANISH_SECONDS), e -> {
            if (keepPortalOpen) { // otro Digimon saldrá por este mismo portal (playExitThroughOpenPortal)
                if (onComplete != null) onComplete.run();
                return;
            }
            stopPortalLoop();
            closePortalMaterialize(onComplete);
        }));

        timeline.play();
    }

    /**
     * targetX/targetY: la posición REAL a la que debe volver el Digimon.
     * Sin esto no hay forma de calcular una dirección real de regreso --
     * antes se asumía "siempre hacia la derecha", ese era el bug.
     */
    public void playExit(ImageView view, Stage petStage, DimSpriteSet sprites, double targetX, double targetY, Runnable onComplete) {
        this.petStage = petStage;
        double portalX = centerX();
        double portalY = bottomY();

        double dist = Math.hypot(targetX - portalX, targetY - portalY);
        double dirX = dist > 0.001 ? (targetX - portalX) / dist : 1;
        double dirY = dist > 0.001 ? (targetY - portalY) / dist : 0;

        petStage.setX(portalX);
        petStage.setY(portalY);
        view.setVisible(true);
        view.setOpacity(0.0);
        view.setImage(sprites.get(SpriteRole.WALK_1));
        applyFacing(view, dirX);

        double portalLeft = portalX + CHAR_WIDTH / 2.0 - PortalSpriteSheet.FRAME_WIDTH / 2.0;
        double portalTop = portalY + CHAR_HEIGHT / 2.0 - PortalSpriteSheet.FRAME_HEIGHT / 2.0;

        // Por si sale otro detrás (keepPortalOpen): sale por el lado contrario (playExitThroughOpenPortal).
        openPortalLeft = portalLeft;
        openPortalTop = portalTop;
        openDirX = dirX;
        openPortalMaterialize(portalLeft, portalTop, dirX, () -> {
            startPortalLoop();
            emergeAndClose(view, petStage, sprites, portalX, portalY, dirX, dirY, onComplete);
        });
    }

    /**
     * Un SEGUNDO Digimon entra por el portal que dejó abierto el primero
     * (keepPortalOpen; ARENA 2 vs 2 local: el compañero sigue al principal):
     * camina desde donde esté hasta la misma entrada, entra desvaneciéndose en
     * la misma dirección y recién entonces el portal se cierra.
     */
    public void playFollowIntoOpenPortal(ImageView view, Stage petStage, DimSpriteSet sprites, Runnable onComplete) {
        if (portalStage == null || !portalStage.isShowing()) {
            playEnter(view, petStage, sprites, null, onComplete); // no quedó portal: abre el suyo
            return;
        }
        this.petStage = petStage;
        keepPortalOpen = false; // detrás de este ya no entra nadie: el portal se cierra
        keepPetInFront();
        view.setVisible(true);
        view.setOpacity(1.0);
        double dirX = openDirX >= 0 ? 1 : -1;
        double entryX = dirX >= 0 ? openPortalLeft : openPortalLeft + PortalSpriteSheet.FRAME_WIDTH;
        double entryY = openPortalTop + PortalSpriteSheet.FRAME_HEIGHT / 2.0;
        walkToEntryPoint(view, petStage, sprites, entryX, entryY, dirX, 0, onComplete);
    }

    /**
     * Sale por el portal que dejó ABIERTO otro Digimon al entrar
     * (keepPortalOpen, intercambio de puestos con la sala online): el portal
     * no se cierra ni se vuelve a abrir en otro lado. Sale hacia el lado por
     * donde llegó el otro.
     */
    public void playExitThroughOpenPortal(ImageView view, Stage petStage, DimSpriteSet sprites, Runnable onComplete) {
        if (portalStage == null || !portalStage.isShowing()) {
            playExit(view, petStage, sprites, petStage.getX(), petStage.getY(), onComplete);
            return;
        }
        this.petStage = petStage;
        keepPortalOpen = false; // es el último en salir: el portal se cierra detrás de él
        double dirX = openDirX >= 0 ? -1 : 1;
        double portalX = openPortalLeft + PortalSpriteSheet.FRAME_WIDTH / 2.0 - CHAR_WIDTH / 2.0;
        double portalY = openPortalTop + PortalSpriteSheet.FRAME_HEIGHT / 2.0 - CHAR_HEIGHT / 2.0;
        petStage.setX(portalX);
        petStage.setY(portalY);
        view.setVisible(true);
        view.setOpacity(0.0);
        view.setImage(sprites.get(SpriteRole.WALK_1));
        applyFacing(view, dirX);
        keepPetInFront();
        emergeAndClose(view, petStage, sprites, portalX, portalY, dirX, 0, onComplete);
    }

    /** Si quien entró dejó el portal abierto y nadie sale por él, se cierra. */
    public void closeOpenPortal(Runnable onClosed) {
        if (portalStage == null || !portalStage.isShowing()) {
            if (onClosed != null) onClosed.run();
            return;
        }
        stopPortalLoop();
        closePortalMaterialize(onClosed);
    }

    /** Aparece (desvanecido al revés) desde el centro del portal, da unos pasos y el portal se cierra. */
    private void emergeAndClose(ImageView view, Stage petStage, DimSpriteSet sprites, double portalX, double portalY,
                                double dirX, double dirY, Runnable onComplete) {
        Timeline timeline = new Timeline();
        for (int i = 0; i < OPACITY_STEPS.length; i++) {
            double time = i * FADE_STEP_SECONDS;
            double opacity = OPACITY_STEPS[OPACITY_STEPS.length - 1 - i];
            SpriteRole role = (i % 2 == 0) ? SpriteRole.WALK_1 : SpriteRole.WALK_2;
            timeline.getKeyFrames().add(new KeyFrame(Duration.seconds(time), e -> {
                view.setOpacity(opacity);
                view.setImage(sprites.get(role));
            }));
        }
        double t = OPACITY_STEPS.length * FADE_STEP_SECONDS;

        int walkFrames = 4;
        for (int i = 0; i < walkFrames; i++) {
            double time = t + i * WALK_FRAME_SECONDS;
            double frac = (i + 1) / (double) walkFrames;
            SpriteRole role = (i % 2 == 0) ? SpriteRole.WALK_2 : SpriteRole.WALK_1;
            timeline.getKeyFrames().add(new KeyFrame(Duration.seconds(time), e -> {
                view.setImage(sprites.get(role));
                petStage.setX(portalX + dirX * FADE_ADVANCE_DISTANCE * frac);
                petStage.setY(portalY + dirY * FADE_ADVANCE_DISTANCE * frac);
            }));
        }
        t += walkFrames * WALK_FRAME_SECONDS;

        timeline.getKeyFrames().add(new KeyFrame(Duration.seconds(t), e -> view.setImage(sprites.get(SpriteRole.IDLE_1))));
        timeline.getKeyFrames().add(new KeyFrame(Duration.seconds(t), e -> {
            if (keepPortalOpen) { // sale otro detrás por este mismo portal (playExitThroughOpenPortal)
                if (onComplete != null) onComplete.run();
                return;
            }
            stopPortalLoop();
            closePortalMaterialize(onComplete);
        }));

        timeline.play();
    }

    // ---------- utilitarios ----------

    /**
     * TEMPORAL: volteo por setScaleX. Pendiente confirmar contra la
     * convención real de VPetMovementController -- todavía no la vi. Si el
     * proyecto usa otro mecanismo, esto hay que unificarlo, no dejarlo
     * como una segunda lógica de facing aparte.
     */
    private void applyFacing(ImageView view, double dirX) {
        view.setScaleX(dirX >= 0 ? -1.0 : 1.0);
    }

    private void openPortalMaterialize(double left, double top, double dirX, Runnable onMaterializeComplete) {
        if (portalStage == null) createPortalWindow();
        double scaleX = dirX >= 0 ? 1.0 : -1.0;
        System.out.println("[PORTAL FACING] dirX=" + dirX + " -> scaleX=" + scaleX); // TEMPORAL -- quitar una vez diagnosticado
        portalView.setScaleX(scaleX);
        portalStage.setX(left);
        portalStage.setY(top);
        portalStage.show();
        keepPetInFront();

        Timeline t = new Timeline();
        for (int i = 0; i < PortalSpriteSheet.MATERIALIZE_FRAMES; i++) {
            double time = i * MATERIALIZE_FRAME_SECONDS;
            int frameIndex = i;
            t.getKeyFrames().add(new KeyFrame(Duration.seconds(time), e -> portalView.setImage(portalSheet.getMaterializeFrame(frameIndex))));
        }
        t.setOnFinished(e -> { if (onMaterializeComplete != null) onMaterializeComplete.run(); });
        t.play();
    }

    /**
     * El portal va DETRÁS del Digimon (pedido del usuario). Ambas ventanas
     * son "siempre encima", así que manda la última que pasó al frente: el
     * portal se muestra después, y por eso se vuelve a traer al Digimon.
     */
    private void keepPetInFront() {
        if (petStage != null && petStage.isShowing()) petStage.toFront();
    }

    private void startPortalLoop() {
        portalLoopTimeline = new Timeline();
        for (int i = 0; i < PortalSpriteSheet.LOOP_FRAMES; i++) {
            double time = i * LOOP_FRAME_SECONDS;
            int frameIndex = i;
            portalLoopTimeline.getKeyFrames().add(new KeyFrame(Duration.seconds(time), e -> portalView.setImage(portalSheet.getLoopFrame(frameIndex))));
        }
        portalLoopTimeline.getKeyFrames().add(new KeyFrame(Duration.seconds(PortalSpriteSheet.LOOP_FRAMES * LOOP_FRAME_SECONDS)));
        portalLoopTimeline.setCycleCount(Timeline.INDEFINITE);
        portalLoopTimeline.play();
    }

    private void stopPortalLoop() {
        if (portalLoopTimeline != null) portalLoopTimeline.stop();
    }

    private void closePortalMaterialize(Runnable onClosed) {
        Timeline t = new Timeline();
        for (int i = 0; i < PortalSpriteSheet.MATERIALIZE_FRAMES; i++) {
            double time = i * MATERIALIZE_FRAME_SECONDS;
            int reverseIndex = PortalSpriteSheet.MATERIALIZE_FRAMES - 1 - i;
            t.getKeyFrames().add(new KeyFrame(Duration.seconds(time), e -> portalView.setImage(portalSheet.getMaterializeFrame(reverseIndex))));
        }
        double closeDuration = PortalSpriteSheet.MATERIALIZE_FRAMES * MATERIALIZE_FRAME_SECONDS;
        t.getKeyFrames().add(new KeyFrame(Duration.seconds(closeDuration), e -> {
            portalStage.hide();
            if (onClosed != null) onClosed.run();
        }));
        t.play();
    }

    private void createPortalWindow() {
        Image sheetImage = new Image(getClass().getResourceAsStream("/PortalSpriteV3.png"));
        portalSheet = new PortalSpriteSheet(sheetImage);

        portalView = new ImageView();
        portalView.setFitWidth(PortalSpriteSheet.FRAME_WIDTH);
        portalView.setFitHeight(PortalSpriteSheet.FRAME_HEIGHT);
        portalView.setSmooth(false);

        Pane root = new Pane(portalView);
        root.setBackground(Background.EMPTY);

        Scene scene = new Scene(root, PortalSpriteSheet.FRAME_WIDTH, PortalSpriteSheet.FRAME_HEIGHT);
        scene.setFill(Color.TRANSPARENT);

        portalStage = new Stage();
        portalStage.initStyle(StageStyle.TRANSPARENT);
        portalStage.setAlwaysOnTop(true);
        portalStage.setScene(scene);
    }
}