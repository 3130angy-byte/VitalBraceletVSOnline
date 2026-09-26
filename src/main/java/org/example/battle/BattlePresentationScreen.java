package org.example.battle;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.Bounds;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;

import org.example.animation.DimSpriteSet;
import org.example.animation.SpriteRole;

/**
 * Fondo real del coliseo + Digimon en tamaño nativo dentro de la arena.
 * BattleResult/RoundOutcome siguen siendo la ÚNICA fuente de verdad.
 *
 * Ajuste de ritmo (esta ronda): ATTACK_TRAVEL_SECONDS y los DODGE_*
 * subieron ligeramente (proporción similar entre sí) para acercarse al
 * ritmo Vital Bracelet sin volverse lento. DODGE_START_PROGRESS y
 * MISS_OVERSHOOT_DISTANCE NO cambiaron -- ya estaban expresados como
 * fracción/distancia, así que se reajustan solos con la nueva velocidad.
 * ATTACK_FADE_PROGRESS es nuevo: antes no existía ningún fade, el
 * proyectil desaparecía de golpe en ambos casos.
 */
public class BattlePresentationScreen {

    private static final boolean DEFAULT_EFFECT_FACES_RIGHT = false;

    private static final double BG_NATIVE_W = 1920;
    private static final double BG_NATIVE_H = 1080;
    public static final double BG_ASPECT = BG_NATIVE_W / BG_NATIVE_H; // antes private

    private static final double ARENA_PIVOT_X_FRAC = 0.5;
    private static final double ARENA_PIVOT_Y_FRAC = 246.0 / 270.0;
    private static final double ARENA_RY_FRAC = 66.0 / 270.0;

    private static final double PLAYER_X_FRAC = ARENA_PIVOT_X_FRAC - 0.16;
    private static final double ENEMY_X_FRAC = ARENA_PIVOT_X_FRAC + 0.16;
    // Subido de 0.55 a 0.85 -- mas adentro del ovalo, lejos del borde frontal.
    // Se probó otro código sugerido por Claude, pero lo puse muy al borde frontal por eso se mantuvo así.
    private static final double COMBATANT_Y_FRAC = ARENA_PIVOT_Y_FRAC - ARENA_RY_FRAC * 0.85;

    // Medido sobre el PNG real del coliseo, que mide 1671x941 (no 1920x1080).
    // Es el rectángulo útil DENTRO del marco de cada pantalla (el marco está
    // en perspectiva, así que se toma el área que no toca ninguna esquina).
    // Los valores anteriores (368/1920 etc.) caían sobre el borde derecho de
    // la pantalla izquierda, no en su centro.
    private static final double BG_REAL_W = 1671;
    private static final double BG_REAL_H = 941;
    private static final double SCREEN_W_FRAC = BattleScreenHud.DESIGN_W / BG_REAL_W;
    private static final double SCREEN_H_FRAC = BattleScreenHud.DESIGN_H / BG_REAL_H;
    private static final double LEFT_SCREEN_CX_FRAC = 182.0 / BG_REAL_W;
    private static final double RIGHT_SCREEN_CX_FRAC = 1489.0 / BG_REAL_W;
    private static final double SCREEN_CY_FRAC = 209.0 / BG_REAL_H;

    private static final double EFFECT_SIZE_SMALL = 28;
    private static final double EFFECT_SIZE_BIG = 42;
    private static final double IMPACT_SIZE = 40;

    private static final double ROUND_LABEL_SECONDS = 0.5;
    private static final double POSE_SECONDS = 0.3;
    private static final double ATTACK_TRAVEL_SECONDS = 0.65; // antes 0.45
    private static final double IMPACT_DISPLAY_SECONDS = 0.3;
    private static final double ACTION_RESULT_HOLD_SECONDS = 0.35;
    private static final double ACTION_GAP_SECONDS = 0.3;
    private static final double ROUND_PAUSE_SECONDS = 0.35;
    private static final double RESULT_HOLD_SECONDS = 1.4;

    private static final double MISS_OVERSHOOT_DISTANCE = 80; // sin cambios -- se reajusta solo
    private static final double DODGE_START_PROGRESS = 0.75;  // sin cambios -- se reajusta solo
    private static final double DODGE_JUMP_HEIGHT = 10;
    private static final double DODGE_OFFSET_X = 8;
    private static final double DODGE_UP_SECONDS = 0.22;   // antes 0.15
    private static final double DODGE_DOWN_SECONDS = 0.20; // antes 0.15

    // NUEVO -- no existía. 0-75% del recorrido: opacidad 1.0. 75-100%: fade a 0.
    private static final double ATTACK_FADE_PROGRESS = 0.75;

    private final AttackSpriteResolver attackSpriteResolver;

    private final Pane sceneRoot = new Pane();
    private final Label roundLabel = new Label();
    private final ImageView playerView = new ImageView();
    private final ImageView enemyView = new ImageView();
    private final ImageView effectView = new ImageView();
    private final ImageView impactView = new ImageView();

    private StackPane leftScreenOverlay;
    private StackPane rightScreenOverlay;
    private BattleScreenHud playerHud;
    private BattleScreenHud enemyHud;

    private int playerMaxHp;
    private int enemyMaxHp;
    private Image impactImage;

    public BattlePresentationScreen(AttackSpriteResolver attackSpriteResolver) {
        this.attackSpriteResolver = attackSpriteResolver;
    }

    /**
     * playerPortrait/enemyPortrait = IDLE_1 (se usa en la arena y como base del rostro de la pantalla).
     * playerNameImage/enemyNameImage = sprite NAME en tamaño NATIVO (DimSpriteImageFactory.toNativeImage).
     * HP máximo y atributo salen del Combatant real -- la pantalla no inventa nada.
     */
    public Node buildNode(Image playerPortrait, Image enemyPortrait,
                          Image playerNameImage, Image enemyNameImage,
                          BattleEngine.Combatant player, BattleEngine.Combatant enemy,
                          double sceneW, double sceneH) {
        this.playerMaxHp = Math.max(1, player.hp);
        this.enemyMaxHp = Math.max(1, enemy.hp);

        double bgW, bgH, bgOffsetX, bgOffsetY;
        double sceneAspect = sceneW / sceneH;
        if (sceneAspect > BG_ASPECT) {
            bgH = sceneH; bgW = sceneH * BG_ASPECT;
            bgOffsetX = (sceneW - bgW) / 2; bgOffsetY = 0;
        } else {
            bgW = sceneW; bgH = sceneW / BG_ASPECT;
            bgOffsetX = 0; bgOffsetY = (sceneH - bgH) / 2;
        }

        sceneRoot.setPrefSize(sceneW, sceneH);
        sceneRoot.setMinSize(sceneW, sceneH);
        sceneRoot.setPickOnBounds(false);

        ImageView backgroundView = new ImageView(loadImageOrNull("/Battle coliseum.png"));
        backgroundView.setPreserveRatio(true);
        backgroundView.setSmooth(false);
        backgroundView.setFitWidth(bgW);
        backgroundView.setFitHeight(bgH);
        backgroundView.setManaged(false);
        backgroundView.setLayoutX(bgOffsetX);
        backgroundView.setLayoutY(bgOffsetY);

        playerView.setImage(playerPortrait);
        playerView.setSmooth(false);
        playerView.setScaleX(-1);
        playerView.setManaged(false);
        centerViewAt(playerView, bgOffsetX + PLAYER_X_FRAC * bgW, bgOffsetY + COMBATANT_Y_FRAC * bgH);

        enemyView.setImage(enemyPortrait);
        enemyView.setSmooth(false);
        enemyView.setScaleX(1);
        enemyView.setManaged(false);
        centerViewAt(enemyView, bgOffsetX + ENEMY_X_FRAC * bgW, bgOffsetY + COMBATANT_Y_FRAC * bgH);

        leftScreenOverlay = buildScreenOverlay(bgOffsetX, bgOffsetY, bgW, bgH, LEFT_SCREEN_CX_FRAC);
        rightScreenOverlay = buildScreenOverlay(bgOffsetX, bgOffsetY, bgW, bgH, RIGHT_SCREEN_CX_FRAC);

        // Mismo factor que el fondo: 1 unidad de diseño del HUD = 1 píxel nativo del PNG.
        double hudScale = bgW / BG_REAL_W;
        playerHud = new BattleScreenHud(playerPortrait, playerNameImage, player.attribute, false, true);
        enemyHud = new BattleScreenHud(enemyPortrait, enemyNameImage, enemy.attribute, true, false);
        playerHud.applyScale(hudScale);
        enemyHud.applyScale(hudScale);
        // Group: su layoutBounds incluye el Scale, así el StackPane lo centra bien.
        leftScreenOverlay.getChildren().add(new Group(playerHud.getNode()));
        rightScreenOverlay.getChildren().add(new Group(enemyHud.getNode()));

        roundLabel.setTextFill(Color.WHITE);
        roundLabel.setFont(Font.font("Consolas", FontWeight.BOLD, 18));
        roundLabel.setManaged(false);
        roundLabel.setLayoutX(bgOffsetX + bgW / 2 - 50);
        roundLabel.setLayoutY(bgOffsetY + 14);

        impactImage = loadImageOrNull("/attacks/impact.png");
        impactView.setSmooth(false);
        impactView.setVisible(false);
        impactView.setManaged(false);
        impactView.setFitWidth(IMPACT_SIZE);
        impactView.setFitHeight(IMPACT_SIZE);
        if (impactImage != null) impactView.setImage(impactImage);

        effectView.setSmooth(false);
        effectView.setVisible(false);
        effectView.setManaged(false);

        sceneRoot.getChildren().setAll(
                backgroundView, leftScreenOverlay, rightScreenOverlay,
                playerView, enemyView, effectView, impactView, roundLabel
        );

        return sceneRoot;
    }

    private void centerViewAt(ImageView view, double centerX, double centerY) {
        view.setLayoutX(centerX - view.getImage().getWidth() / 2.0);
        view.setLayoutY(centerY - view.getImage().getHeight() / 2.0);
    }

    private StackPane buildScreenOverlay(double bgOffsetX, double bgOffsetY, double bgW, double bgH, double cxFrac) {
        double w = SCREEN_W_FRAC * bgW;
        double h = SCREEN_H_FRAC * bgH;
        StackPane overlay = new StackPane();
        overlay.setManaged(false);
        overlay.setPrefSize(w, h);
        overlay.resize(w, h); // no-gestionado: el padre nunca le da tamaño por su cuenta
        overlay.setLayoutX(bgOffsetX + cxFrac * bgW - w / 2.0);
        overlay.setLayoutY(bgOffsetY + SCREEN_CY_FRAC * bgH - h / 2.0);
        return overlay;
    }

    public StackPane getLeftScreenOverlay() { return leftScreenOverlay; }
    public StackPane getRightScreenOverlay() { return rightScreenOverlay; }

    public void play(BattleEngine.BattleResult result, DimSpriteSet playerSprites, DimSpriteSet enemySprites,
                     BattleEngine.Combatant playerCombatant, BattleEngine.Combatant enemyCombatant,
                     Runnable onComplete) {
        Timeline timeline = new Timeline();
        double t = 0;

        for (BattleEngine.RoundOutcome round : result.rounds) {
            t = appendRound(timeline, t, round, playerSprites, enemySprites, playerCombatant, enemyCombatant);
        }

        double finalT = t;
        timeline.getKeyFrames().add(new KeyFrame(Duration.seconds(finalT), e -> showResult(result)));
        timeline.getKeyFrames().add(new KeyFrame(Duration.seconds(finalT + RESULT_HOLD_SECONDS), e -> {
            if (onComplete != null) onComplete.run();
        }));

        timeline.play();
    }

    private double appendRound(Timeline timeline, double t, BattleEngine.RoundOutcome round,
                               DimSpriteSet playerSprites, DimSpriteSet enemySprites,
                               BattleEngine.Combatant playerCombatant, BattleEngine.Combatant enemyCombatant) {

        timeline.getKeyFrames().add(new KeyFrame(Duration.seconds(t), e -> {
            playerView.setImage(playerSprites.get(SpriteRole.IDLE_1));
            enemyView.setImage(enemySprites.get(SpriteRole.IDLE_1));
            boolean isBigRound = round.attackType == BattleEngine.AttackType.BIG;
            roundLabel.setText("RONDA " + round.roundNumber + (isBigRound ? "  \u2605 SUPER HIT" : ""));
        }));
        t += ROUND_LABEL_SECONDS;

        boolean playerHits = round.attacker == BattleEngine.Side.PLAYER;
        boolean enemyHits = round.attacker == BattleEngine.Side.ENEMY;

        BattleEngine.AttackType playerVisualType = playerHits ? round.attackType : BattleEngine.AttackType.SMALL;
        int playerVisualId = playerHits ? round.attackId : playerCombatant.smallAttackId;
        t = appendAttackAction(timeline, t, playerView, enemyView, playerSprites, enemySprites,
                true, playerHits, playerVisualType, playerVisualId, round.playerHpAfter, round.enemyHpAfter);

        boolean koAfterPlayerAction = playerHits && (round.playerHpAfter <= 0 || round.enemyHpAfter <= 0);
        if (koAfterPlayerAction) {
            return t;
        }

        t += ACTION_GAP_SECONDS;

        BattleEngine.AttackType enemyVisualType = enemyHits ? round.attackType : BattleEngine.AttackType.SMALL;
        int enemyVisualId = enemyHits ? round.attackId : enemyCombatant.smallAttackId;
        t = appendAttackAction(timeline, t, enemyView, playerView, enemySprites, playerSprites,
                false, enemyHits, enemyVisualType, enemyVisualId, round.playerHpAfter, round.enemyHpAfter);

        t += ROUND_PAUSE_SECONDS;
        return t;
    }

    private double appendAttackAction(Timeline timeline, double t, ImageView attackerView, ImageView defenderView,
                                      DimSpriteSet attackerSprites, DimSpriteSet defenderSprites,
                                      boolean attackerFacesRight, boolean isHit,
                                      BattleEngine.AttackType attackType, int attackId,
                                      int playerHpAfter, int enemyHpAfter) {

        boolean isBig = attackType == BattleEngine.AttackType.BIG;
        Image effectImage = isBig
                ? attackSpriteResolver.getBigAttack(attackId)
                : attackSpriteResolver.getSmallAttack(attackId);

        timeline.getKeyFrames().add(new KeyFrame(Duration.seconds(t), e ->
                attackerView.setImage(attackerSprites.get(SpriteRole.ATTACK))));
        t += POSE_SECONDS;

        double travelStart = t;

        Bounds attackerBounds = boundsInSceneRoot(attackerView);
        Bounds defenderBounds = boundsInSceneRoot(defenderView);
        double startX = attackerFacesRight ? attackerBounds.getMaxX() : attackerBounds.getMinX();
        double startY = (attackerBounds.getMinY() + attackerBounds.getMaxY()) / 2.0;
        double hitEndX = (defenderBounds.getMinX() + defenderBounds.getMaxX()) / 2.0;
        double hitEndY = (defenderBounds.getMinY() + defenderBounds.getMaxY()) / 2.0;
        double size = isBig ? EFFECT_SIZE_BIG : EFFECT_SIZE_SMALL;
        boolean shouldMirror = DEFAULT_EFFECT_FACES_RIGHT != attackerFacesRight;
        double hitTravelEnd = travelStart + ATTACK_TRAVEL_SECONDS;

        if (effectImage != null) {
            addEffectStart(timeline, effectImage, size, shouldMirror, startX, startY, travelStart);
            timeline.getKeyFrames().add(new KeyFrame(Duration.seconds(hitTravelEnd),
                    new KeyValue(effectView.translateXProperty(), hitEndX - startX),
                    new KeyValue(effectView.translateYProperty(), hitEndY - startY)));
        }

        if (isHit) {
            // HIT: sin fade -- desaparece justo al impacto, el impacto conserva el protagonismo (punto 7).
            t = hitTravelEnd;
            int finalPlayerHp = playerHpAfter;
            int finalEnemyHp = enemyHpAfter;
            timeline.getKeyFrames().add(new KeyFrame(Duration.seconds(t), e -> {
                effectView.setVisible(false);
                showImpactOn(defenderView);
                updateHpBars(finalPlayerHp, finalEnemyHp);
            }));
            t += IMPACT_DISPLAY_SECONDS;
            timeline.getKeyFrames().add(new KeyFrame(Duration.seconds(t), e -> impactView.setVisible(false)));
            double remaining = ACTION_RESULT_HOLD_SECONDS - IMPACT_DISPLAY_SECONDS;
            t += Math.max(0, remaining);

        } else {
            double normalDistance = Math.hypot(hitEndX - startX, hitEndY - startY);
            double overshootSeconds = normalDistance > 0.001
                    ? ATTACK_TRAVEL_SECONDS * (MISS_OVERSHOOT_DISTANCE / normalDistance)
                    : ATTACK_TRAVEL_SECONDS * 0.3;
            double missTravelEnd = hitTravelEnd + overshootSeconds;
            double direction = attackerFacesRight ? 1.0 : -1.0;
            double missEndX = hitEndX + direction * MISS_OVERSHOOT_DISTANCE;

            if (effectImage != null) {
                timeline.getKeyFrames().add(new KeyFrame(Duration.seconds(missTravelEnd),
                        new KeyValue(effectView.translateXProperty(), missEndX - startX),
                        new KeyValue(effectView.translateYProperty(), hitEndY - startY)));

                // NUEVO: fade progresivo -- 0 a ATTACK_FADE_PROGRESS del recorrido total en
                // opacidad 1.0, despues interpola a 0 sin detener el movimiento.
                double totalMissDuration = missTravelEnd - travelStart;
                double fadeStart = travelStart + totalMissDuration * ATTACK_FADE_PROGRESS;
                timeline.getKeyFrames().add(new KeyFrame(Duration.seconds(fadeStart),
                        new KeyValue(effectView.opacityProperty(), 1.0)));
                timeline.getKeyFrames().add(new KeyFrame(Duration.seconds(missTravelEnd),
                        new KeyValue(effectView.opacityProperty(), 0.0)));

                timeline.getKeyFrames().add(new KeyFrame(Duration.seconds(missTravelEnd), e -> effectView.setVisible(false)));
            }

            double dodgeStart = travelStart + ATTACK_TRAVEL_SECONDS * DODGE_START_PROGRESS;
            double dodgeSign = attackerFacesRight ? 1.0 : -1.0;
            double dodgeDownEnd = addDodgeKeyFrames(timeline, defenderView, defenderSprites, dodgeStart, dodgeSign);

            t = Math.max(missTravelEnd, dodgeDownEnd);
        }

        timeline.getKeyFrames().add(new KeyFrame(Duration.seconds(t), e -> {
            attackerView.setImage(attackerSprites.get(SpriteRole.IDLE_1));
            defenderView.setImage(defenderSprites.get(SpriteRole.IDLE_1));
        }));

        return t;
    }

    private void addEffectStart(Timeline timeline, Image effectImage, double size, boolean shouldMirror,
                                double startX, double startY, double travelStart) {
        timeline.getKeyFrames().add(new KeyFrame(Duration.seconds(travelStart), e -> {
            effectView.setImage(effectImage);
            effectView.setFitWidth(size);
            effectView.setFitHeight(size);
            effectView.setScaleX(shouldMirror ? -1.0 : 1.0);
            effectView.setLayoutX(startX - size / 2.0);
            effectView.setLayoutY(startY - size / 2.0);
            effectView.setTranslateX(0);
            effectView.setTranslateY(0);
            effectView.setVisible(true);
        },
                new KeyValue(effectView.translateXProperty(), 0),
                new KeyValue(effectView.translateYProperty(), 0),
                new KeyValue(effectView.opacityProperty(), 1.0))); // KeyValue real, no una llamada suelta -- ancla la interpolación en cada ronda
    }

    private double addDodgeKeyFrames(Timeline timeline, ImageView defenderView, DimSpriteSet defenderSprites,
                                     double dodgeStart, double horizontalSign) {
        double peakX = DODGE_OFFSET_X * horizontalSign;
        double peakY = -DODGE_JUMP_HEIGHT;
        double upEnd = dodgeStart + DODGE_UP_SECONDS;
        double downEnd = upEnd + DODGE_DOWN_SECONDS;

        timeline.getKeyFrames().add(new KeyFrame(Duration.seconds(dodgeStart),
                e -> defenderView.setImage(defenderSprites.get(SpriteRole.DODGE)),
                new KeyValue(defenderView.translateXProperty(), 0),
                new KeyValue(defenderView.translateYProperty(), 0)));

        timeline.getKeyFrames().add(new KeyFrame(Duration.seconds(upEnd),
                new KeyValue(defenderView.translateXProperty(), peakX),
                new KeyValue(defenderView.translateYProperty(), peakY)));

        timeline.getKeyFrames().add(new KeyFrame(Duration.seconds(downEnd),
                new KeyValue(defenderView.translateXProperty(), 0),
                new KeyValue(defenderView.translateYProperty(), 0)));

        return downEnd;
    }

    private void showImpactOn(ImageView defenderView) {
        if (impactImage == null) return;
        Bounds defenderBounds = boundsInSceneRoot(defenderView);
        double centerX = (defenderBounds.getMinX() + defenderBounds.getMaxX()) / 2.0;
        double centerY = (defenderBounds.getMinY() + defenderBounds.getMaxY()) / 2.0;

        impactView.setLayoutX(centerX - IMPACT_SIZE / 2.0);
        impactView.setLayoutY(centerY - IMPACT_SIZE / 2.0);
        impactView.setVisible(true);
    }

    private Bounds boundsInSceneRoot(Node node) {
        return sceneRoot.sceneToLocal(node.localToScene(node.getBoundsInLocal()));
    }

    private void updateHpBars(int playerHp, int enemyHp) {
        playerHud.setHpFraction(Math.max(0, playerHp) / (double) playerMaxHp);
        enemyHud.setHpFraction(Math.max(0, enemyHp) / (double) enemyMaxHp);
    }

    /** Detiene los timers del HUD (scroll del nombre). Llamar al terminar la batalla. */
    public void dispose() {
        if (playerHud != null) playerHud.stop();
        if (enemyHud != null) enemyHud.stop();
    }

    private void showResult(BattleEngine.BattleResult result) {
        if (result.draw) {
            roundLabel.setTextFill(Color.rgb(240, 220, 120));
            roundLabel.setText("EMPATE");
            return;
        }
        boolean won = result.won;
        roundLabel.setTextFill(won ? Color.rgb(120, 255, 150) : Color.rgb(255, 90, 90));
        roundLabel.setText(won ? "\u00A1VICTORIA!" : "DERROTA...");
    }

    private Image loadImageOrNull(String resourcePath) {
        var stream = getClass().getResourceAsStream(resourcePath);
        if (stream == null) {
            System.out.println("BattlePresentationScreen: no se encontró " + resourcePath);
            return null;
        }
        return new Image(stream);
    }
}