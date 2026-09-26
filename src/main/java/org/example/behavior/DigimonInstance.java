package org.example.behavior;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Background;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.stage.DirectoryChooser;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import com.github.cfogrady.vb.dim.sprite.SpriteData;

import org.example.Edition;
import org.example.animation.DimAnimationPlayer;
import org.example.animation.DimSpriteSet;
import org.example.animation.SpriteRole;
import org.example.animation.TeleportAnimator;
import org.example.animation.VPetAnimations;
import org.example.animation.VPetStageTier;
import org.example.battle.AttackSpriteResolver;
import org.example.battle.BattleEngine;
import org.example.battle.BattlePresentationScreen;
import org.example.battle.PortalTransitionScene;
import org.example.battle.RivalDimPool;
import org.example.battle.VitalRewards;
import org.example.chat.AppPaths;
import org.example.dim.VsDimWriter;
import org.example.chat.AiConversationController;
import org.example.chat.ChatMemory;
import org.example.chat.EmotionalSupportPolicy;
import org.example.chat.OllamaClient;
import org.example.chat.PersonalityBaseLoader;
import org.example.chat.AssistantSettings;
import org.example.chat.SpeciesNameReader;
import org.example.online.client.LobbyDigimon;
import org.example.online.client.LobbyWindow;
import org.json.JSONObject;
import org.example.chat.SpeechBubble;
import org.example.chat.SpeechTraits;
import org.example.chat.VPetEvent;
import org.example.dim.DimSpriteImageFactory;
import org.example.dim.DimVPetData;
import org.example.dim.VsDimData;
import org.example.interaction.VPetClickToMoveController;
import org.example.ui.DigimonInfoPanel;
import org.example.ui.VPetMenu;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Un Digimon en el escritorio (0.0.3: asistente, sin crianza). Llega SOLO
 * desde una VS DIM (decisión del usuario), en forma fija: sin huevo,
 * hambre, suciedad, malestar, evolución, Jogress ni muerte. Conserva los
 * datos del Digimon (sprites, stats, atributo, Activity Type, especie,
 * nombre, edad, Vital Values, Power Trophies) y Batalla aleatoria.
 */
public class DigimonInstance {

    private static final int CANVAS_WIDTH = 64;
    private static final int CANVAS_HEIGHT = 56;

    private final String instanceId;
    private final Path sourceBinPath;
    private final ChatMemory chatMemory;
    private final DigimonAge age;
    private DigimonProgress progress;

    private int currentSlot;
    private VPetStageTier currentStage;
    private String displayName;
    private Image nameSpriteImage;
    /** Especie leída del sprite NAME (SpeciesNameReader), o null si aún no / no se pudo. */
    private String recognizedSpecies;
    private boolean isAssistant = false;

    private Stage stage;
    private ImageView imageView;
    private DimVPetData dimData;
    private DimAnimationPlayer animationPlayer;
    private VPetMovementController movementController;
    private VPetClickToMoveController clickToMove;
    private CompanionController companion;
    private VPetMenu menu;
    private AiConversationController aiController;
    private SpeechBubble speechBubble;
    private Timeline socialCheckTimer;

    private DigimonRegistry registry;
    private OllamaClient ollamaClient;
    private final AttackSpriteResolver attackSpriteResolver = new AttackSpriteResolver();
    /** Aviso a Main cuando el Digimon se retiró (vuelve la pantalla de inicio). */
    private Consumer<DigimonInstance> onRetired;
    private boolean retiring = false;

    private DigimonInstance(String instanceId, Path sourceBinPath, int importedAgeDays) {
        this.instanceId = instanceId;
        this.sourceBinPath = sourceBinPath;
        this.chatMemory = new ChatMemory(instanceId);
        this.age = new DigimonAge(importedAgeDays);
    }

    /** Digimon traído del Vital Bracelet mediante una VS DIM; el slot lo dice el propio archivo. */
    public static DigimonInstance fromVsDim(Path vsDimPath, int ageDays) {
        return new DigimonInstance(UUID.randomUUID().toString(), vsDimPath, ageDays);
    }

    /**
     * 0.0.3.1 no tiene el modelo de visión que lee la especie del sprite
     * NAME: el usuario la escribe al importarlo (vacío = "Digimon").
     */
    public void setTypedSpecies(String species) {
        this.recognizedSpecies = species == null || species.isBlank() ? null : species.trim();
    }

    public void setOnRetired(Consumer<DigimonInstance> onRetired) {
        this.onRetired = onRetired;
    }

    public void spawn(double x, double y, OllamaClient ollamaClient, DigimonRegistry registry) throws Exception {
        this.registry = registry;
        this.ollamaClient = ollamaClient;
        this.dimData = DimVPetData.load(sourceBinPath, 0);
        if (!dimData.isVsDim()) {
            throw new IllegalArgumentException(sourceBinPath.getFileName() + " no es una VS DIM.");
        }
        VsDimData vs = dimData.getVsData();
        this.currentSlot = vs.slot();
        this.progress = new DigimonProgress(vs.vitalValues(), vs.powerTrophies());

        this.aiController = new AiConversationController(instanceId, chatMemory, ollamaClient, registry);
        this.speechBubble = new SpeechBubble();

        imageView = new ImageView();
        imageView.setFitWidth(CANVAS_WIDTH);
        imageView.setFitHeight(CANVAS_HEIGHT);
        imageView.setPreserveRatio(false);
        imageView.setSmooth(false);

        Pane root = new Pane(imageView);
        root.setBackground(Background.EMPTY);
        Scene scene = new Scene(root, CANVAS_WIDTH, CANVAS_HEIGHT);
        scene.setFill(Color.TRANSPARENT);

        stage = new Stage();
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setAlwaysOnTop(true);
        stage.setScene(scene);
        stage.setX(x);
        stage.setY(y);

        aiController.addOnBubbleMessageListener(text -> speechBubble.show(text, stage));

        stage.setOpacity(0);
        stage.show();
        PauseTransition reveal = new PauseTransition(Duration.millis(120));
        reveal.setOnFinished(e -> stage.setOpacity(1));
        reveal.play();

        start();
    }

    private void start() {
        DimSpriteSet sprites = buildSpriteSetForSlot(dimData, currentSlot);
        currentStage = sprites.getStage();

        chatMemory.getNameIdentity().setCurrentSlot(currentSlot);
        this.displayName = getDisplayName();
        updatePersonalityContext();

        animationPlayer = new DimAnimationPlayer(imageView, sprites);

        movementController = new VPetMovementController(stage, imageView, animationPlayer, currentStage);
        clickToMove = new VPetClickToMoveController(stage, movementController);
        companion = new CompanionController(stage, movementController, clickToMove, currentStage);
        menu = new VPetMenu(stage, companion, currentStage, aiController,
                this::battleRandomAction, this::vsOnlineAction, this::openInfoPanel, this::updatePersonalityContext,
                this::retireAction);

        // 0.0.3.1 no tiene chat: el globo solo muestra textos fijos.
        if (Edition.ASSISTANT) speechBubble.setOnBubbleClicked(() -> menu.openChat(stage.getX(), stage.getY() - 200));
        speechBubble.setDigimonName(displayName);
        menu.setDigimonName(displayName);
        refreshNameSpriteImage();
        speechBubble.setDigimonNameSpriteImage(nameSpriteImage);
        menu.setDigimonNameSpriteImage(nameSpriteImage);

        aiController.setOnNameChanged(newName -> {
            displayName = newName;
            speechBubble.setDigimonName(newName);
            menu.setDigimonName(newName);
            refreshProfileText();
        });

        imageView.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                clickToMove.onPetClicked();
            } else if (e.getButton() == MouseButton.SECONDARY) {
                menu.toggle(stage); // se abre al costado del Digimon (ver VPetMenu)
            }
        });

        // Llega al escritorio saliendo de un portal (pedido del usuario); recién
        // entonces empieza a pasear. Mientras tanto no se le puede mandar nada.
        companion.setMenuOpen(true);
        new TeleportAnimator().playExit(imageView, stage, sprites, stage.getX(), stage.getY(), () -> {
            animationPlayer.play(VPetAnimations.idle(currentStage), 2.0);
            companion.start();
            companion.setMenuOpen(false);
        });

        if (!Edition.ASSISTANT) return; // sin IA: nada de charlas ni noticias por iniciativa propia
        socialCheckTimer = new Timeline(new KeyFrame(Duration.minutes(5), e -> {
            aiController.maybeInitiateInterDigimonChat();
            aiController.maybeShareNews();
        }));
        socialCheckTimer.setCycleCount(Animation.INDEFINITE);
        socialCheckTimer.play();
    }

    // ---------- Nombre / especie / personalidad ----------

    private void refreshNameSpriteImage() {
        SpriteData.Sprite rawNameSprite = dimData.getSpritesForSlot(currentSlot).get(0);
        this.nameSpriteImage = DimSpriteImageFactory.toNativeImage(rawNameSprite);
        startSpeciesRecognition();
    }

    /**
     * Lee la especie del sprite NAME con el modelo de visión (ver
     * SpeciesNameReader), en segundo plano. Mientras tanto, o si no se puede
     * leer, se muestra "Digimon" -- nunca el nombre del archivo.
     */
    private void startSpeciesRecognition() {
        if (!Edition.ASSISTANT) return; // 0.0.3.1: la especie la escribió el usuario (setTypedSpecies)
        recognizedSpecies = null;
        SpeciesNameReader.read(nameSpriteImage, ollamaClient).thenAccept(species ->
                Platform.runLater(() -> {
                    if (species.isEmpty()) return;
                    recognizedSpecies = species.get();
                    displayName = getDisplayName();
                    if (speechBubble != null) speechBubble.setDigimonName(displayName);
                    if (menu != null) menu.setDigimonName(displayName);
                    updatePersonalityContext();
                }));
    }

    private void updatePersonalityContext() {
        String userBase = PersonalityBaseLoader.load();

        StringBuilder ctx = new StringBuilder();
        ctx.append("Eres un Digimon que vive en la computadora del usuario, con tu propia personalidad, ")
                .append("y eres su amigo -- NO un asistente: no ofreces ayuda ni respuestas, ")
                .append("conversas como un amigo. ")
                .append("Habla siempre en primera persona (yo, mi, me) de forma natural; nunca te refieras a ti ")
                .append("mismo por tu nombre o especie en una oración normal (di 'puedo...', no 'Como Curimon, ")
                .append("puedo...'). Tu especie (ej. 'Curimon') es tu clasificación biológica, como 'humano' o ")
                .append("'halcón' -- menciónala solo si preguntan explícitamente qué eres. Tu nombre es tu ")
                .append("identidad personal, distinta de tu especie. Sé breve y natural. ")
                .append("Si el usuario menciona o te asigna un nombre dirigido a ti, usa la etiqueta exacta ")
                .append("##NAME_CANDIDATE:<nombre>## en tu respuesta (se quita antes de mostrarse).");

        if (!userBase.isEmpty()) {
            ctx.append("\n\n--- Base adicional (definida por el usuario) ---\n").append(userBase);
        }

        // Solo la línea mínima de seguridad es permanente; el modo de apoyo y la
        // forma de hablar por etapa los agrega ChatMemory cuando corresponde.
        ctx.append("\n\n").append(EmotionalSupportPolicy.SAFETY_TEXT);

        chatMemory.setContextBase(ctx.toString());
        refreshProfileText();
    }

    private void refreshProfileText() {
        var entry = dimData.getCard().getCharacterStats().getCharacterEntries().get(currentSlot);
        int dimStage = dimData.getStageForSlot(currentSlot);

        chatMemory.setDimStage(dimStage);

        StringBuilder profile = new StringBuilder();
        profile.append(SpeechTraits.buildSection(entry.getType(), entry.getAttribute()));

        String known = chatMemory.getNameIdentity().effectiveName();
        if (recognizedSpecies != null) {
            // Especie = lo que eres (clasificación), NO tu nombre propio.
            profile.append("\nTu especie (lo que eres, no tu nombre): ").append(recognizedSpecies);
        }
        profile.append("\nNombre actual: ").append(known != null ? known : "aún no lo conoces.");

        chatMemory.setProfileText(profile.toString());
    }

    // ---------- Panel "Digimon" ----------

    private void openInfoPanel() {
        Image portraitImage = animationPlayer.getSpriteSet().get(SpriteRole.IDLE_1);
        var entry = dimData.getCard().getCharacterStats().getCharacterEntries().get(currentSlot);
        new DigimonInfoPanel(stage).show(stage.getX(), stage.getY() - 220, nameSpriteImage, portraitImage,
                attributeLabel(entry.getAttribute()), activityTypeLabel(entry.getType()), age.getAgeDays(),
                new DigimonInfoPanel.BattleStats(entry.getDp(), entry.getHp(), entry.getAp(), progress.getPowerTrophies()));
    }

    private String attributeLabel(int attribute) {
        return switch (attribute) {
            case 1 -> "Virus";
            case 2 -> "Data";
            case 3 -> "Vaccine";
            case 4 -> "Free";
            default -> "Null";
        };
    }

    private String activityTypeLabel(int activityType) {
        return switch (activityType) {
            case 0 -> "Estoico";
            case 1 -> "Activo";
            case 2 -> "Normal";
            case 3 -> "Interior";
            case 4 -> "Perezoso";
            default -> "Desconocido";
        };
    }

    // ---------- Batalla aleatoria ----------

    /**
     * La VS DIM solo trae los sprites de su propio Digimon, así que los rivales
     * salen de la carpeta de DIM cards elegida en Funciones > Ajustes
     * (RivalDimPool). Esas DIM no se crían: solo aportan rivales.
     */
    private void battleRandomAction() {
        Optional<DimVPetData> rivalCardOpt = RivalDimPool.pickRandomCard();
        if (rivalCardOpt.isEmpty() && !Edition.ASSISTANT) {
            // 0.0.3.1 no tiene la pantalla de Ajustes: la carpeta se elige aquí mismo.
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Elige una carpeta con DIM cards normales (.bin) para los rivales");
            java.io.File folder = chooser.showDialog(stage);
            if (folder == null) return;
            AssistantSettings.saveRivalFolder(folder.getAbsolutePath());
            rivalCardOpt = RivalDimPool.pickRandomCard();
            if (rivalCardOpt.isEmpty()) {
                aiController.announce("En esa carpeta no encontré DIM cards normales para pelear.");
                return;
            }
        }
        if (rivalCardOpt.isEmpty()) {
            speechBubble.show("Para pelear necesito rivales: elige una carpeta con DIM cards en "
                    + "Asistente > Funciones > Ajustes.", stage);
            return;
        }
        DimVPetData rivalCard = rivalCardOpt.get();

        companion.setMenuOpen(true);
        animationPlayer.stop();

        double originalX = stage.getX();
        double originalY = stage.getY();

        TeleportAnimator teleport = new TeleportAnimator();
        DimSpriteSet currentSprites = animationPlayer.getSpriteSet();

        teleport.playEnter(imageView, stage, currentSprites, currentStage, () -> {
            BattleEngine engine = new BattleEngine();
            int enemySlot = engine.pickOpponentSlot(rivalCard, dimData.getStageForSlot(currentSlot), -1);
            BattleEngine.Combatant player = engine.combatantFromSlot(dimData, currentSlot);
            BattleEngine.Combatant enemy = engine.combatantFromSlot(rivalCard, enemySlot);
            BattleEngine.BattleResult result = engine.fight(player, enemy, currentSlot, enemySlot);

            DimSpriteSet enemySprites = buildSpriteSetForSlot(rivalCard, enemySlot);

            // El ancho se DERIVA del alto usando la proporción real del coliseo:
            // la ventana nunca se desajusta de la imagen, sea cual sea el monitor.
            Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();
            double sceneH = screenBounds.getHeight() * 0.62;
            double sceneW = sceneH * BattlePresentationScreen.BG_ASPECT;
            if (sceneW > screenBounds.getWidth() * 0.95) {
                sceneW = screenBounds.getWidth() * 0.95;
                sceneH = sceneW / BattlePresentationScreen.BG_ASPECT;
            }

            PortalTransitionScene portal = new PortalTransitionScene(sceneW, sceneH, true);
            BattlePresentationScreen presentation = new BattlePresentationScreen(attackSpriteResolver);
            portal.setContent(presentation.buildNode(
                    currentSprites.get(SpriteRole.IDLE_1), enemySprites.get(SpriteRole.IDLE_1),
                    nameSpriteImage, nativeNameImage(rivalCard, enemySlot),
                    player, enemy, sceneW, sceneH));
            int rivalStage = rivalCard.getStageForSlot(enemySlot);
            portal.open(() -> presentation.play(result, currentSprites, enemySprites, player, enemy, () -> {
                presentation.dispose();
                applyBattleResult(result, rivalStage);

                portal.close(() ->
                        teleport.playExit(imageView, stage, animationPlayer.getSpriteSet(), originalX, originalY, () -> {
                            if (result.won) companion.victoryAction();
                            else companion.loseAction();
                            companion.setMenuOpen(false);
                        })
                );
            }));
        });
    }

    // ---------- VS Online ----------

    /**
     * Batalla > VS Online: el Digimon entra al portal (misma animación que la
     * Batalla aleatoria), se abre la sala del VS Online donde se maneja el
     * avatar, y el Digimon aparece ahí siguiéndolo. Al cerrar la sala, el
     * Digimon vuelve al escritorio con la animación de salida.
     * Servidor, puerto y nombre de jugador: Ajustes (assistant.properties).
     */
    private void vsOnlineAction() {
        companion.setMenuOpen(true);
        animationPlayer.stop();

        double originalX = stage.getX();
        double originalY = stage.getY();
        TeleportAnimator teleport = new TeleportAnimator();
        DimSpriteSet currentSprites = animationPlayer.getSpriteSet();

        teleport.playEnter(imageView, stage, currentSprites, currentStage, () -> {
            // Especie leída del sprite NAME; si aún no se pudo, "Digimon" (nunca el nombre del archivo).
            String species = recognizedSpecies != null ? recognizedSpecies : "Digimon";
            JSONObject myDigimon = LobbyDigimon.payloadFrom(dimData.getVsData(), species);

            LobbyWindow lobby = new LobbyWindow(new Stage(), AssistantSettings.onlineHost(), AssistantSettings.onlinePort(),
                    AssistantSettings.playerName(), 0, myDigimon, "VS Online - " + displayName,
                    () -> teleport.playExit(imageView, stage, animationPlayer.getSpriteSet(), originalX, originalY, () -> {
                        animationPlayer.play(VPetAnimations.idle(currentStage), 2.0);
                        companion.setMenuOpen(false);
                    }));
            // Cada Batalla Oficial suma o resta Vital Values según la etapa del rival
            // (VitalRewards) y el Digimon la comenta; el saldo viaja al VB al retirarlo.
            lobby.setOnBattleResult(r -> {
                VitalRewards.BattleOutcome outcome = switch (r.outcome()) {
                    case "WIN" -> VitalRewards.BattleOutcome.WIN;
                    case "LOSS" -> VitalRewards.BattleOutcome.LOSS;
                    default -> VitalRewards.BattleOutcome.DRAW;
                };
                recordAndReact(outcome, r.rivalStage(), "batalla oficial online", true);
            });
            lobby.open();
        });
    }

    private Image nativeNameImage(DimVPetData card, int slot) {
        return DimSpriteImageFactory.toNativeImage(card.getSpritesForSlot(slot).get(0));
    }

    private void applyBattleResult(BattleEngine.BattleResult result, int rivalStage) {
        VitalRewards.BattleOutcome outcome = result.draw ? VitalRewards.BattleOutcome.DRAW
                : result.won ? VitalRewards.BattleOutcome.WIN : VitalRewards.BattleOutcome.LOSS;
        recordAndReact(outcome, rivalStage, "combate", false);
    }

    /**
     * Suma la batalla al récord (Vital Values por etapa del rival) y el
     * Digimon la comenta: con la IA en 0.0.3, con un texto fijo en 0.0.3.1.
     */
    private void recordAndReact(VitalRewards.BattleOutcome outcome, int rivalStage, String detail, boolean important) {
        int delta = progress.recordBattle(outcome, rivalStage, VitalRewards.load());
        if (Edition.ASSISTANT) {
            if (outcome == VitalRewards.BattleOutcome.WIN) {
                aiController.onGameEvent(new VPetEvent(VPetEvent.Type.VICTORIA, detail, important));
            } else if (outcome == VitalRewards.BattleOutcome.LOSS) {
                aiController.onGameEvent(new VPetEvent(VPetEvent.Type.DERROTA, detail, important));
            }
            return;
        }
        String text = switch (outcome) {
            case WIN -> "¡Gané! " + String.format("%+d", delta) + " Vital Values.";
            case LOSS -> "Perdí... " + String.format("%+d", delta) + " Vital Values.";
            case DRAW -> "¡Empate! Nadie gana ni pierde Vital Values.";
        };
        aiController.announce(text);
    }

    // ---------- Retirar al Digimon (volver al Vital Bracelet) ----------

    /**
     * Pedido del usuario: el Digimon se despide con el saldo de TODAS sus
     * batallas (aleatorias y oficiales). Se escribe una VS DIM nueva a partir
     * de la original con los Vital Values sumados o restados (VsDimWriter):
     * saldo positivo = vuelve ganando, negativo = vuelve perdiendo. Después se
     * va por un portal, se abre la carpeta con el archivo para pasarlo al VB y
     * vuelve la pantalla de inicio.
     */
    private void retireAction() {
        if (retiring) return;
        VitalRewards rewards = VitalRewards.load();
        int before = progress.getImportedVitalValues();
        int after = progress.getVitalValues(rewards);
        int change = after - before;

        String verdict;
        if (progress.getBattles() == 0) verdict = "No peleó: vuelve igual que llegó.";
        else if (change > 0) verdict = "Saldo POSITIVO: vuelve al Vital Bracelet como VICTORIA.";
        else if (change < 0) verdict = "Saldo NEGATIVO: vuelve al Vital Bracelet como DERROTA.";
        else verdict = "Saldo en cero: vuelve igual que llegó.";
        if (after != before + progress.getVitalDelta()) {
            verdict += "\n(Los Vital Values quedan entre 0 y " + rewards.maxVitalValues + ".)";
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.initOwner(stage);
        confirm.setTitle("Retirar a " + displayName);
        confirm.setHeaderText("¿Retirar a " + displayName + " y devolverlo al Vital Bracelet?");
        confirm.setContentText("Batallas: " + progress.getBattles() + "  (" + progress.getWins() + " ganadas, "
                + progress.getLosses() + " perdidas, " + progress.getDraws() + " empates)\n"
                + "Vital Values: " + before + " → " + after + "  (" + String.format("%+d", change) + ")\n\n"
                + verdict + "\n\nSe creará una VS DIM nueva para pasarla al Vital Bracelet. "
                + "La original no se toca. El Digimon se irá del escritorio.");
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;

        Path target = AppPaths.returns().resolve(returnFileName(change));
        try {
            VsDimWriter.writeWithVitalValues(sourceBinPath, target, after);
        } catch (IOException e) {
            Alert error = new Alert(Alert.AlertType.ERROR, "No se pudo crear la VS DIM de vuelta:\n" + e.getMessage());
            error.initOwner(stage);
            error.setHeaderText(null);
            error.showAndWait();
            return;
        }
        System.out.println("[RETIRO] " + displayName + ": " + before + " -> " + after + " VV en " + target);

        retiring = true;
        companion.setMenuOpen(true);
        stopAll();
        animationPlayer.stop();
        new TeleportAnimator().playEnter(imageView, stage, animationPlayer.getSpriteSet(), currentStage, () -> {
            showInExplorer(target);
            // Primero la pantalla de inicio y DESPUÉS se cierra esta ventana: si no quedara
            // ninguna ventana abierta, JavaFX cerraría el programa.
            if (onRetired != null) onRetired.accept(this);
            dispose();
        });
    }

    /** "VS DIM MagnaKidmon 2026-09-26 18-40 (+530 VV).bin" -- solo caracteres válidos en Windows. */
    private String returnFileName(int change) {
        String species = recognizedSpecies != null ? recognizedSpecies : "Digimon";
        species = species.replaceAll("[^A-Za-z0-9 _-]", "").trim();
        if (species.isEmpty()) species = "Digimon";
        String when = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH-mm-ss"));
        return "VS DIM " + species + " " + when + " (" + String.format("%+d", change) + " VV).bin";
    }

    /** Abre el Explorador con el archivo seleccionado (Windows); si falla, solo la carpeta. */
    private static void showInExplorer(Path file) {
        try {
            new ProcessBuilder("explorer.exe", "/select,", file.toAbsolutePath().toString()).start();
        } catch (IOException e) {
            try {
                java.awt.Desktop.getDesktop().open(file.getParent().toFile());
            } catch (Exception ignored) {
                System.out.println("[RETIRO] Abre a mano: " + file.getParent());
            }
        }
    }

    // ---------- Sprites ----------

    private DimSpriteSet buildSpriteSetForSlot(DimVPetData card, int slot) {
        int dimStage = card.getStageForSlot(slot);
        VPetStageTier stageTier = stageTierForDimStage(dimStage);
        List<SpriteData.Sprite> sprites = card.getSpritesForSlot(slot);

        DimSpriteSet spriteSet = new DimSpriteSet(stageTier, CANVAS_WIDTH, CANVAS_HEIGHT);

        if (stageTier == VPetStageTier.CHILD_PLUS) {
            spriteSet.put(SpriteRole.NAME,    img(sprites.get(0)));
            spriteSet.put(SpriteRole.IDLE_1,  img(sprites.get(1)));
            spriteSet.put(SpriteRole.IDLE_2,  img(sprites.get(2)));
            spriteSet.put(SpriteRole.WALK_1,  img(sprites.get(3)));
            spriteSet.put(SpriteRole.WALK_2,  img(sprites.get(4)));
            spriteSet.put(SpriteRole.RUN_1,   img(sprites.get(5)));
            spriteSet.put(SpriteRole.RUN_2,   img(sprites.get(6)));
            spriteSet.put(SpriteRole.TRAIN_1, img(sprites.get(7)));
            spriteSet.put(SpriteRole.TRAIN_2, img(sprites.get(8)));
            spriteSet.put(SpriteRole.VICTORY, img(sprites.get(9)));
            spriteSet.put(SpriteRole.SLEEP,   img(sprites.get(10)));
            spriteSet.put(SpriteRole.ATTACK,  img(sprites.get(11)));
            spriteSet.put(SpriteRole.DODGE,   img(sprites.get(12)));
            if (sprites.size() > 13) {
                spriteSet.put(SpriteRole.SPLASH, img(sprites.get(13)));
            }
        } else {
            spriteSet.put(SpriteRole.NAME,    img(sprites.get(0)));
            spriteSet.put(SpriteRole.IDLE_1,  img(sprites.get(1)));
            spriteSet.put(SpriteRole.IDLE_2,  img(sprites.get(2)));
            spriteSet.put(SpriteRole.WALK_1,  img(sprites.get(3)));
            spriteSet.put(SpriteRole.VICTORY, img(sprites.get(4)));
            spriteSet.put(SpriteRole.SLEEP,   img(sprites.get(5)));
            if (stageTier != VPetStageTier.BABY_I && sprites.size() > 6) {
                spriteSet.put(SpriteRole.SPLASH, img(sprites.get(6)));
            }
        }
        return spriteSet;
    }

    private Image img(SpriteData.Sprite sprite) {
        return DimSpriteImageFactory.toImage(sprite, CANVAS_WIDTH, CANVAS_HEIGHT);
    }

    private VPetStageTier stageTierForDimStage(int dimStage) {
        if (dimStage == 0) return VPetStageTier.BABY_I;
        if (dimStage == 1) return VPetStageTier.BABY_II;
        return VPetStageTier.CHILD_PLUS;
    }

    // ---------- Ciclo de vida de la ventana ----------

    public void stopAll() {
        if (companion != null) companion.stop();
        if (socialCheckTimer != null) socialCheckTimer.stop();
    }

    public void dispose() {
        stopAll();
        if (stage != null) stage.close();
    }

    public String getInstanceId() { return instanceId; }
    public Path getSourceBinPath() { return sourceBinPath; }
    public int getCurrentSlot() { return currentSlot; }
    public VPetStageTier getCurrentStage() { return currentStage; }
    /** Nombre propio si lo tiene; si no, su especie leída de la DIM; si no, "Digimon" (nunca el archivo). */
    public String getDisplayName() {
        String effective = chatMemory.getNameIdentity().effectiveName();
        if (effective != null) return effective;
        return recognizedSpecies != null ? recognizedSpecies : "Digimon";
    }
    public DimVPetData getDimData() { return dimData; }
    public DigimonProgress getProgress() { return progress; }
    public DigimonAge getAge() { return age; }
    public ChatMemory getChatMemory() { return chatMemory; }
    public AiConversationController getAiController() { return aiController; }
    public boolean isAssistant() { return isAssistant; }
    public void setAssistant(boolean assistant) { this.isAssistant = assistant; }
    public double getX() { return stage != null ? stage.getX() : 0; }
    public double getY() { return stage != null ? stage.getY() : 0; }
}
