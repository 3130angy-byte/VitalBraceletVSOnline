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
import org.example.arena.ArenaConfig;
import org.example.arena.ArenaEngine;
import org.example.arena.ArenaFighter;
import org.example.arena.ArenaScreen;
import org.example.lab.BattleHistory;
import org.example.lab.Digidex;
import org.example.lab.LabStorage;
import org.example.lab.LabWindow;
import org.example.online.Protocol;
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
import org.example.online.client.OfficialBattleResult;
import org.example.online.client.LobbyWindow;
import org.json.JSONObject;
import org.example.chat.SpeechBubble;
import org.example.chat.SpeechTraits;
import org.example.chat.VPetEvent;
import org.example.dim.DimSpriteImageFactory;
import org.example.dim.NameSpriteReader;
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
    /** Entrando o saliendo por el portal: nada más puede mover la ventana. */
    private boolean teleporting = false;
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
    /** Cápsula del Laboratorio de la que salió (todo Digimon del escritorio tiene una). */
    private String capsuleId;
    /** Está dentro del portal (batalla, ARENA o sala online). */
    private boolean away = false;
    /** Lo reemplazaron desde el Laboratorio mientras estaba fuera: se va al volver. */
    private Runnable pendingDismiss;
    /** Está en la sala del VS Online siguiendo a tu avatar (caso particular de away). */
    private boolean inLobby = false;
    /** A dónde vuelve en el escritorio al salir de la sala. */
    private double lobbyReturnX, lobbyReturnY;
    /** Nace directo en la sala (reemplazo desde la PC): sin animación de llegada al escritorio. */
    private boolean spawnIntoLobby = false;
    /** Está dentro de una ARENA 2 vs 2 como compañero (puesto 2); vuelve a donde estaba. */
    private boolean inArena = false;
    private double arenaReturnX, arenaReturnY;

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

    public void setCapsuleId(String capsuleId) { this.capsuleId = capsuleId; }
    public String getCapsuleId() { return capsuleId; }

    /**
     * Sale del escritorio y vuelve a su cápsula del Laboratorio, SIN generar
     * archivo para el VB (eso es RETIRAR). Guarda su récord pendiente. Si
     * está dentro del portal (sala online, batalla), se va al volver de ahí.
     */
    public void dismiss(Runnable onGone) {
        if (capsuleId != null && progress != null) LabStorage.saveProgress(capsuleId, progress.toProperties());
        Runnable finish = () -> {
            stopAll();
            dispose();
            onGone.run();
        };
        if (inLobby) {
            // En la sala: la sala lo anima entrando a su portal (LobbyWindow.beginPortalSwap);
            // aquí su ventana ya está vacía, así que se cierra al instante.
            inLobby = false;
            away = false;
            stopAll();
            dispose();
            onGone.run();
            return;
        }
        if (away) {
            // Está en una pelea: el equipo cambia YA (el nuevo ocupa su
            // puesto), y esta ventana, que está escondida, se cierra cuando vuelva.
            pendingDismiss = () -> {
                stopAll();
                dispose();
            };
            onGone.run();
            return;
        }
        retiring = true;
        companion.setMenuOpen(true);
        stopAll();
        animationPlayer.stop();
        enterPortal(new TeleportAnimator(), animationPlayer.getSpriteSet(), finish);
    }

    /**
     * Entrada al portal. Primero CORTA el paseo, el clic-para-mover o la
     * acción que estuviera en curso: antes seguían moviendo la ventana a la
     * vez que el portal y el Digimon se deslizaba hasta la esquina y volvía
     * (bug desde 0.0.2). Si venía moviéndose, se queda en IDLE un momento y
     * recién entonces se calcula dónde aparece el portal (TeleportAnimator).
     */
    private void enterPortal(TeleportAnimator teleport, DimSpriteSet sprites, Runnable onComplete) {
        boolean wasMoving = movementController.isMoving();
        movementController.stopMovement();
        clickToMove.cancel();
        animationPlayer.stop();
        teleporting = true;
        teleport.playEnter(imageView, stage, sprites, currentStage, wasMoving, () -> {
            teleporting = false;
            onComplete.run();
        });
    }

    /** Salida del portal; mientras dura, el clic no lo mueve. */
    private void exitPortal(TeleportAnimator teleport, DimSpriteSet sprites, double x, double y, Runnable onComplete) {
        clickToMove.cancel();
        teleporting = true;
        teleport.playExit(imageView, stage, sprites, x, y, () -> {
            teleporting = false;
            onComplete.run();
        });
    }

    /** Vuelve del portal: normalmente con la animación de salida; si lo reemplazaron mientras tanto, se va. */
    private void comeBack(Runnable exitAnimation) {
        away = false;
        if (pendingDismiss != null) {
            Runnable finish = pendingDismiss;
            pendingDismiss = null;
            finish.run();
            return;
        }
        exitAnimation.run();
    }

    public boolean isAway() { return away; }

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
        // Récord pendiente guardado en su cápsula (sobrevive a reemplazos y a cerrar el programa).
        if (capsuleId != null) progress.restore(LabStorage.loadProgress(capsuleId));

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
                this::retireAction, this::arenaAction);

        // 0.0.3.1 no tiene chat: el globo solo muestra textos fijos.
        if (Edition.ASSISTANT) speechBubble.setOnBubbleClicked(() -> menu.openChat(stage.getX(), stage.getY() - 200));
        speechBubble.setDigimonName(displayName);
        menu.setDigimonName(displayName);
        refreshNameSpriteImage();
        Digidex.seen(dimData.getSpritesForSlot(currentSlot).get(0), recognizedSpecies);
        speechBubble.setDigimonNameSpriteImage(nameSpriteImage);
        menu.setDigimonNameSpriteImage(nameSpriteImage);

        aiController.setOnNameChanged(newName -> {
            displayName = newName;
            speechBubble.setDigimonName(newName);
            menu.setDigimonName(newName);
            refreshProfileText();
        });

        imageView.setOnMouseClicked(e -> {
            if (teleporting) return; // en plena animación del portal no se le manda nada
            if (e.getButton() == MouseButton.PRIMARY) {
                clickToMove.onPetClicked();
            } else if (e.getButton() == MouseButton.SECONDARY) {
                menu.toggle(stage); // se abre al costado del Digimon (ver VPetMenu)
            }
        });

        // Llega al escritorio saliendo de un portal (pedido del usuario); recién
        // entonces empieza a pasear. Mientras tanto no se le puede mandar nada.
        companion.setMenuOpen(true);
        if (spawnIntoLobby) {
            // Reemplazó al puesto 1 mientras estaba en la sala: aparece ALLÁ (por el portal de
            // la sala), no aquí. Su ventana queda vacía hasta que cierres la sala.
            imageView.setVisible(false);
            lobbyReturnX = stage.getX();
            lobbyReturnY = stage.getY();
            away = true;
            inLobby = true;
        } else {
            exitPortal(new TeleportAnimator(), sprites, stage.getX(), stage.getY(), () -> {
                animationPlayer.play(VPetAnimations.idle(currentStage), 2.0);
                companion.start();
                companion.setMenuOpen(false);
            });
        }

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
        startSpeciesRecognition(rawNameSprite);
    }

    /**
     * Lee la especie del sprite NAME. Primero SIN IA (NameSpriteReader:
     * plantillas de las fuentes del VB, al instante y sin errores con letras
     * latinas); si no puede (p. ej. katakana), con el modelo de visión
     * (SpeciesNameReader), en segundo plano. Mientras tanto, o si no se puede
     * leer, se muestra "Digimon" -- nunca el nombre del archivo.
     */
    private void startSpeciesRecognition(SpriteData.Sprite rawNameSprite) {
        if (!Edition.ASSISTANT) return; // 0.0.3.1: la especie la escribió el usuario (prellenada con NameSpriteReader)
        recognizedSpecies = NameSpriteReader.read(rawNameSprite).map(NameSpriteReader.Result::text).orElse(null);
        if (recognizedSpecies != null) return; // leída sin IA: no hace falta el modelo de visión
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
        Optional<DimVPetData> rivalCardOpt = rivalCard();
        if (rivalCardOpt.isEmpty()) return;
        DimVPetData rivalCard = rivalCardOpt.get();

        companion.setMenuOpen(true);
        animationPlayer.stop();

        double originalX = stage.getX();
        double originalY = stage.getY();

        TeleportAnimator teleport = new TeleportAnimator();
        DimSpriteSet currentSprites = animationPlayer.getSpriteSet();

        away = true;
        enterPortal(teleport, currentSprites, () -> {
            BattleEngine engine = new BattleEngine();
            int enemySlot = engine.pickOpponentSlot(rivalCard, dimData.getStageForSlot(currentSlot), -1);
            Digidex.seen(rivalCard.getSpritesForSlot(enemySlot).get(0), null);
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

                portal.close(() -> comeBack(() ->
                        exitPortal(teleport, animationPlayer.getSpriteSet(), originalX, originalY, () -> {
                            if (result.won) companion.victoryAction();
                            else companion.loseAction();
                            companion.setMenuOpen(false);
                        })
                ));
            }));
        });
    }

    /**
     * Una DIM card normal de la carpeta de rivales (RivalDimPool). En 0.0.3.1
     * no hay pantalla de Ajustes: si falta la carpeta se elige aquí mismo.
     * Vacío = no hay rivales (ya se le avisó al usuario).
     */
    private Optional<DimVPetData> rivalCard() {
        Optional<DimVPetData> card = RivalDimPool.pickRandomCard();
        if (card.isEmpty() && !Edition.ASSISTANT) {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Elige una carpeta con DIM cards normales (.bin) para los rivales");
            java.io.File folder = chooser.showDialog(stage);
            if (folder == null) return Optional.empty();
            AssistantSettings.saveRivalFolder(folder.getAbsolutePath());
            card = RivalDimPool.pickRandomCard();
            if (card.isEmpty()) {
                aiController.announce("En esa carpeta no encontré DIM cards normales para pelear.");
                return Optional.empty();
            }
        }
        if (card.isEmpty()) {
            speechBubble.show("Para pelear necesito rivales: agrega DIM cards en Laboratorio > RIVALES"
                    + (Edition.ASSISTANT ? " (o en Asistente > Funciones > Ajustes)." : "."), stage);
        }
        return card;
    }

    // ---------- ARENA (2 vs 2) ----------

    /**
     * Tercer modo de combate, inspirado en la app Vital Bracelet Arena
     * (decisiones del usuario): 2 vs 2 contra la máquina, con ataque de
     * números, defensa con barra, cambio y W-ATTACK (reglas en ArenaEngine /
     * ArenaConfig). Tu equipo = este Digimon + el otro del escritorio si es
     * Child o superior; si no, un COMPAÑERO prestado de una DIM card de la
     * carpeta de rivales. El rival: 2 Digimon de esas DIM cards. NO cuenta
     * para el récord ni para el reporte al retirar (como en la app real).
     */
    private void arenaAction() {
        ArenaConfig config = ArenaConfig.load();
        BattleEngine engine = new BattleEngine();
        int myStage = dimData.getStageForSlot(currentSlot);

        // El equipo sale de los PUESTOS del escritorio (1 = principal, 2 = secundario), no de
        // quién recibió el clic; los Baby no pelean y se saltan.
        List<DigimonInstance> fighters = registry.getActiveInstances().stream()
                .filter(d -> d.getDimData().getStageForSlot(d.getCurrentSlot()) >= 2).toList();
        DigimonInstance first = fighters.isEmpty() ? this : fighters.get(0);
        ArenaFighter me = ArenaFighter.fromDim(first.getDimData(), first.getCurrentSlot(), first.getDisplayName(), false, config);
        ArenaFighter partner = null;
        if (fighters.size() >= 2) {
            DigimonInstance second = fighters.get(1);
            partner = ArenaFighter.fromDim(second.getDimData(), second.getCurrentSlot(), second.getDisplayName(), false, config);
        }

        // Sin otro Digimon en el escritorio: primero una de tus cápsulas del Laboratorio (Child+).
        if (partner == null) {
            List<LabStorage.Capsule> capsules = LabStorage.list().stream()
                    .filter(c -> c.data().character().stage() >= 2 && !c.file().equals(first.getSourceBinPath())).toList();
            if (!capsules.isEmpty()) {
                LabStorage.Capsule c = capsules.get(new java.util.Random().nextInt(capsules.size()));
                try {
                    DimVPetData capsuleData = DimVPetData.load(c.file(), 0);
                    partner = ArenaFighter.fromDim(capsuleData, c.data().slot(), c.species(), true, config);
                    Digidex.seen(capsuleData.getSpritesForSlot(c.data().slot()).get(0), c.species());
                } catch (Exception e) {
                    System.out.println("[ARENA] No se pudo usar la cápsula " + c.id() + ": " + e.getMessage());
                }
            }
        }

        ArenaFighter[] cpu = new ArenaFighter[2];
        for (int i = 0; i < (partner == null ? 3 : 2); i++) {
            Optional<DimVPetData> card = rivalCard();
            if (card.isEmpty()) return;
            int slot = engine.pickOpponentSlot(card.get(), myStage, -1);
            Digidex.seen(card.get().getSpritesForSlot(slot).get(0), null);
            if (i < 2) {
                cpu[i] = ArenaFighter.fromDim(card.get(), slot, "Rival " + (i + 1), false, config);
            } else {
                partner = ArenaFighter.fromDim(card.get(), slot, "Compañero", true, config);
            }
        }
        ArenaFighter[] team = {me, partner};

        // Por el portal entra el PUESTO 1 y, si su compañero es el puesto 2 del escritorio, este
        // lo sigue por detrás y cruza el MISMO portal (pedido del usuario); al final salen los dos
        // por el mismo portal.
        DigimonInstance leader = first.away ? this : first;
        DigimonInstance follower = fighters.size() >= 2 && fighters.get(1) != leader
                && !fighters.get(1).away && !fighters.get(1).teleporting ? fighters.get(1) : null;
        TeleportAnimator teleport = new TeleportAnimator();
        if (follower != null) teleport.keepPortalOpen();

        double leaderX = leader.stage.getX(), leaderY = leader.stage.getY();
        leader.companion.setMenuOpen(true);
        leader.animationPlayer.stop();
        leader.away = true;
        if (follower != null) follower.startFollowing(leader);

        Runnable openArena = () -> new ArenaScreen(new ArenaEngine(config, new java.util.Random(), team, cpu)).open(won -> {
            if (follower != null) teleport.keepPortalOpen(); // salen los dos por el mismo portal
            leader.comeBack(() -> leader.exitPortal(teleport, leader.animationPlayer.getSpriteSet(), leaderX, leaderY, () -> {
                leader.animationPlayer.play(VPetAnimations.idle(leader.currentStage), 2.0);
                leader.companion.setMenuOpen(false);
                if (follower != null) follower.leaveArenaThrough(teleport);
                if (won == null) return; // cerró la ventana a media pelea
                BattleHistory.add(leader.displayName + " + " + team[1].name, "ARENA 2 vs 2",
                        "rivales " + LabWindow.stageName(cpu[0].stage) + " y " + LabWindow.stageName(cpu[1].stage),
                        won ? "Victoria" : "Derrota", "no cuenta");
                if (won) leader.companion.victoryAction();
                else leader.companion.loseAction();
                leader.reactOnly(won ? VitalRewards.BattleOutcome.WIN : VitalRewards.BattleOutcome.LOSS, "arena");
            }));
        });
        leader.enterPortal(teleport, leader.animationPlayer.getSpriteSet(), () -> {
            if (follower == null) openArena.run();
            else follower.followIntoPortal(teleport, openArena);
        });
    }

    /**
     * ARENA local: el compañero (puesto 2) deja de pasear y camina detrás del
     * principal, hacia el centro de la pantalla donde se abrirá el portal.
     */
    private void startFollowing(DigimonInstance leader) {
        companion.setMenuOpen(true);
        clickToMove.cancel();
        teleporting = true; // camino al portal: el clic no lo desvía
        away = true;
        inArena = true;
        arenaReturnX = stage.getX();
        arenaReturnY = stage.getY();
        double center = TeleportAnimator.centerX();
        double fromSide = Math.signum(leader.stage.getX() - center);
        if (fromSide == 0) fromSide = -1;
        // Se queda del lado por donde viene el principal, un poco más atrás.
        movementController.walkTo(center + fromSide * 110, TeleportAnimator.bottomY());
    }

    /** Cruza el portal que dejó abierto el principal (deja de caminar primero: nunca dos animaciones a la vez). */
    private void followIntoPortal(TeleportAnimator teleport, Runnable onEntered) {
        movementController.stopMovement();
        clickToMove.cancel();
        animationPlayer.stop();
        teleporting = true;
        teleport.playFollowIntoOpenPortal(imageView, stage, animationPlayer.getSpriteSet(), () -> {
            teleporting = false;
            onEntered.run();
        });
    }

    /** ARENA local terminada: sale detrás del principal por el mismo portal (que se cierra tras él). */
    private void leaveArenaThrough(TeleportAnimator teleport) {
        inArena = false;
        comeBack(() -> {
            clickToMove.cancel();
            teleporting = true;
            teleport.playExitThroughOpenPortal(imageView, stage, animationPlayer.getSpriteSet(), () -> {
                teleporting = false;
                backOnDesktop();
            });
        });
    }

    /**
     * ARENA online (pedido del usuario): el puesto 1 ya está en la sala; el
     * puesto 2, que está en el escritorio, cruza su propio portal antes de que
     * empiece la pelea. {@code onEntered} = empezar la pelea.
     */
    public void goToArena(Runnable onEntered) {
        if (away || teleporting) {
            onEntered.run();
            return;
        }
        companion.setMenuOpen(true);
        arenaReturnX = stage.getX();
        arenaReturnY = stage.getY();
        away = true;
        inArena = true;
        enterPortal(new TeleportAnimator(), animationPlayer.getSpriteSet(), onEntered);
    }

    /** Terminó la ARENA online: solo el puesto 2 sale de su portal al escritorio, a donde estaba. */
    public void returnFromArena() {
        if (!inArena) return;
        inArena = false;
        comeBack(() -> exitPortal(new TeleportAnimator(), animationPlayer.getSpriteSet(), arenaReturnX, arenaReturnY,
                this::backOnDesktop));
    }

    public boolean isInArena() { return inArena; }

    // ---------- VS Online ----------

    /**
     * Batalla > VS Online: el Digimon entra al portal (misma animación que la
     * Batalla aleatoria), se abre la sala del VS Online donde se maneja el
     * avatar, y el Digimon aparece ahí siguiéndolo. Al cerrar la sala, el
     * Digimon vuelve al escritorio con la animación de salida.
     * Servidor, puerto y nombre de jugador: Ajustes (assistant.properties).
     */
    private void vsOnlineAction() {
        if (LobbyWindow.isAnyOpen()) return;
        // A la sala entra SIEMPRE el puesto 1 (es quien te sigue ahí), aunque el menú sea del puesto 2.
        DigimonInstance first = registry.primary().orElse(this);
        if (first != this) {
            if (first.away) {
                aiController.announce(first.getDisplayName() + " (puesto 1) todavía no vuelve; espera a que regrese para entrar a la sala.");
                return;
            }
            first.vsOnlineAction();
            return;
        }

        companion.setMenuOpen(true);
        animationPlayer.stop();
        DimSpriteSet currentSprites = animationPlayer.getSpriteSet();

        lobbyReturnX = stage.getX();
        lobbyReturnY = stage.getY();
        away = true;
        inLobby = true;
        enterPortal(new TeleportAnimator(), currentSprites, () -> {
            // En la sala va TU EQUIPO: el puesto 1 te sigue y pelea las batallas tipo VB; el
            // puesto 2 viaja como compañero para el 2 vs 2. Se reenvía si lo cambias en la PC.
            // Al cerrar vuelve QUIEN ESTÉ en la sala en ese momento (pudo cambiar desde la PC).
            LobbyWindow lobby = new LobbyWindow(new Stage(), AssistantSettings.onlineHost(), AssistantSettings.onlinePort(),
                    AssistantSettings.playerName(), 0, teamPayload(), "VS Online - " + AssistantSettings.playerName(),
                    () -> List.copyOf(registry.getActiveInstances()).stream()
                            .filter(d -> d.isInLobby() && !d.teleporting).forEach(DigimonInstance::returnFromLobby));
            lobby.setTeamSupplier(this::teamPayload);
            // Cada Batalla Oficial cuenta para quien la peleó: el puesto 1 del momento.
            lobby.setOnBattleResult(r -> registry.primary().orElse(this).recordOnlineBattle(r));
            // ARENA 2 vs 2 online: tu puesto 2 (en el escritorio) cruza su portal antes de la pelea
            // y, al terminar, solo él vuelve a salir por su portal (pedido del usuario).
            lobby.setArenaHooks(
                    start -> registry.secondary().filter(d -> !d.isInLobby()).ifPresentOrElse(
                            second -> second.goToArena(start), start),
                    () -> List.copyOf(registry.getActiveInstances()).stream()
                            .filter(DigimonInstance::isInArena).forEach(DigimonInstance::returnFromArena));
            lobby.open();
        });
    }

    /** Sale de la sala y vuelve al escritorio por el portal, al lugar desde donde entró. */
    public void returnFromLobby() {
        leaveLobbyTo(lobbyReturnX, lobbyReturnY);
    }

    /** Sale de la sala y aparece en el escritorio por un portal nuevo, caminando hacia (x, y). */
    private void leaveLobbyTo(double x, double y) {
        if (!inLobby) return;
        inLobby = false;
        comeBack(() -> exitPortal(new TeleportAnimator(), animationPlayer.getSpriteSet(), x, y, this::backOnDesktop));
    }

    /**
     * Intercambio de puestos 1 ⇄ 2 con la sala abierta: sale de la sala por
     * el portal del escritorio que dejó ABIERTO el otro al entrar (el portal
     * no se cierra ni aparece en otro lado). Si ya no estaba en la sala, solo
     * se cierra ese portal.
     */
    public void leaveLobbyThrough(TeleportAnimator openPortal) {
        if (!inLobby) {
            openPortal.closeOpenPortal(null);
            return;
        }
        inLobby = false;
        comeBack(() -> {
            clickToMove.cancel();
            teleporting = true;
            openPortal.playExitThroughOpenPortal(imageView, stage, animationPlayer.getSpriteSet(), () -> {
                teleporting = false;
                backOnDesktop();
            });
        });
    }

    private void backOnDesktop() {
        animationPlayer.play(VPetAnimations.idle(currentStage), 2.0);
        companion.start(); // si llegó directo a la sala, todavía no paseaba
        companion.setMenuOpen(false);
    }

    /**
     * Del escritorio a la sala (intercambio de puestos con la sala abierta):
     * entra a un portal que QUEDA ABIERTO y, cuando desaparece,
     * {@code onEntered} recibe ese portal para que el otro salga por él.
     */
    public void goToLobby(Consumer<TeleportAnimator> onEntered) {
        companion.setMenuOpen(true);
        lobbyReturnX = stage.getX();
        lobbyReturnY = stage.getY();
        away = true;
        inLobby = true;
        TeleportAnimator portal = new TeleportAnimator().keepPortalOpen();
        enterPortal(portal, animationPlayer.getSpriteSet(), () -> onEntered.accept(portal));
    }

    /** Llega DIRECTO a la sala (reemplazo del puesto 1 desde la PC): no aparece en el escritorio. */
    public void setSpawnIntoLobby(boolean spawnIntoLobby) {
        this.spawnIntoLobby = spawnIntoLobby;
    }

    public boolean isInLobby() { return inLobby; }

    /** Mensaje "digimon" con el equipo actual: puesto 1 + puesto 2 (si hay) como compañero. */
    private JSONObject teamPayload() {
        DigimonInstance first = registry.primary().orElse(this);
        DigimonInstance second = registry.secondary().orElse(null);
        return LobbyDigimon.teamPayload(first.getDimData().getVsData(), first.speciesForOnline(),
                second == null ? null : second.getDimData().getVsData(),
                second == null ? null : second.speciesForOnline());
    }

    /** Especie para el VS Online: la leída del sprite NAME (o escrita), nunca el nombre del archivo. */
    public String speciesForOnline() {
        return recognizedSpecies != null ? recognizedSpecies : "Digimon";
    }

    /**
     * Resultado de una pelea del VS Online. Las Batallas Oficiales (Libre u
     * Original) suman o restan Vital Values y viajan al VB al retirar; la
     * ARENA 2 vs 2 online, como la local, NO cuenta (solo historial y comentario).
     */
    public void recordOnlineBattle(OfficialBattleResult r) {
        VitalRewards.BattleOutcome outcome = switch (r.outcome()) {
            case "WIN" -> VitalRewards.BattleOutcome.WIN;
            case "LOSS" -> VitalRewards.BattleOutcome.LOSS;
            default -> VitalRewards.BattleOutcome.DRAW;
        };
        if (Protocol.MODE_ARENA.equals(r.mode())) {
            BattleHistory.add(displayName + " + compañero", "ARENA 2 vs 2 online", "rival " + LabWindow.stageName(r.rivalStage()),
                    resultName(outcome), "no cuenta");
            if (outcome != VitalRewards.BattleOutcome.DRAW) reactOnly(outcome, "arena online");
            return;
        }
        recordAndReact(outcome, r.rivalStage(), "batalla oficial online", true,
                "Oficial (" + (Protocol.MODE_ORIGINAL.equals(r.mode()) ? "Original" : "Libre") + ")");
    }

    private Image nativeNameImage(DimVPetData card, int slot) {
        return DimSpriteImageFactory.toNativeImage(card.getSpritesForSlot(slot).get(0));
    }

    private void applyBattleResult(BattleEngine.BattleResult result, int rivalStage) {
        VitalRewards.BattleOutcome outcome = result.draw ? VitalRewards.BattleOutcome.DRAW
                : result.won ? VitalRewards.BattleOutcome.WIN : VitalRewards.BattleOutcome.LOSS;
        recordAndReact(outcome, rivalStage, "combate", false, "Aleatoria");
    }

    /**
     * Suma la batalla al récord (Vital Values por etapa del rival) y el
     * Digimon la comenta: con la IA en 0.0.3, con un texto fijo en 0.0.3.1.
     */
    private static String resultName(VitalRewards.BattleOutcome outcome) {
        return switch (outcome) {
            case WIN -> "Victoria";
            case LOSS -> "Derrota";
            case DRAW -> "Empate";
        };
    }

    /** Solo el comentario del Digimon, sin tocar el récord (la ARENA no cuenta). */
    private void reactOnly(VitalRewards.BattleOutcome outcome, String detail) {
        if (Edition.ASSISTANT) {
            aiController.onGameEvent(new VPetEvent(outcome == VitalRewards.BattleOutcome.WIN
                    ? VPetEvent.Type.VICTORIA : VPetEvent.Type.DERROTA, detail, false));
            return;
        }
        aiController.announce(outcome == VitalRewards.BattleOutcome.WIN
                ? "¡Ganamos en la ARENA!" : "Perdimos en la ARENA... ¡la próxima!");
    }

    private void recordAndReact(VitalRewards.BattleOutcome outcome, int rivalStage, String detail, boolean important,
                                String historyMode) {
        int delta = progress.recordBattle(outcome, rivalStage, VitalRewards.load());
        if (capsuleId != null) LabStorage.saveProgress(capsuleId, progress.toProperties());
        BattleHistory.add(displayName, historyMode, "rival " + LabWindow.stageName(rivalStage), resultName(outcome),
                String.format("%+d (estimado)", delta));
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
     * batallas (aleatorias y oficiales), resumido en UN reporte de batalla
     * dentro de una VS DIM nueva hecha desde la original (VsDimWriter), igual
     * al que escribe un VB rival: saldo positivo = victoria contra el rival
     * más fuerte que venció, negativo = derrota contra el más fuerte que lo
     * venció, sin saldo = copia sin reporte. El VB calcula los Vital Values
     * reales al leerlo. Después se va por un portal, se abre la carpeta con
     * el archivo para pasarlo al VB y vuelve la pantalla de inicio.
     */
    private void retireAction() {
        if (retiring) return;
        VitalRewards rewards = VitalRewards.load();
        int before = progress.getImportedVitalValues();
        int after = progress.getVitalValues(rewards);
        int change = after - before;
        Optional<DigimonProgress.BattleReport> report = progress.battleReport();

        String verdict = report.map(r -> (r.won()
                        ? "Saldo POSITIVO: vuelve como VICTORIA contra un rival " : "Saldo NEGATIVO: vuelve como DERROTA contra un rival ")
                        + stageName(r.rivalStage()) + ".")
                .orElse(progress.getBattles() == 0 ? "No peleó: vuelve igual que llegó."
                        : "Saldo en cero: vuelve igual que llegó.");

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.initOwner(stage);
        confirm.setTitle("Retirar a " + displayName);
        confirm.setHeaderText("¿Retirar a " + displayName + " y devolverlo al Vital Bracelet?");
        confirm.setContentText("Batallas: " + progress.getBattles() + "  (" + progress.getWins() + " ganadas, "
                + progress.getLosses() + " perdidas, " + progress.getDraws() + " empates)\n"
                + "Saldo estimado: " + String.format("%+d", change) + " Vital Values\n\n"
                + verdict + "\n\nEl Vital Bracelet calcula los Vital Values reales al recibirlo. "
                + "Se creará una VS DIM nueva; la original no se toca. Usa una VS DIM recién "
                + "extraída del VB. El Digimon se irá del escritorio.");
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;

        Path target = AppPaths.returns().resolve(returnFileName(report));
        try {
            if (report.isPresent()) {
                VsDimWriter.writeBattleReport(sourceBinPath, target, report.get().rivalStage(), report.get().won());
            } else {
                VsDimWriter.copyUnchanged(sourceBinPath, target);
            }
        } catch (IOException e) {
            Alert error = new Alert(Alert.AlertType.ERROR, "No se pudo crear la VS DIM de vuelta:\n" + e.getMessage());
            error.initOwner(stage);
            error.setHeaderText(null);
            error.showAndWait();
            return;
        }
        System.out.println("[RETIRO] " + displayName + ": " + report.map(r -> (r.won() ? "victoria" : "derrota")
                + " contra etapa " + r.rivalStage()).orElse("sin reporte") + " en " + target);

        // El saldo ya viaja al VB en el reporte: la cápsula empieza de cero.
        if (capsuleId != null) LabStorage.resetProgress(capsuleId);
        retiring = true;
        companion.setMenuOpen(true);
        stopAll();
        animationPlayer.stop();
        enterPortal(new TeleportAnimator(), animationPlayer.getSpriteSet(), () -> {
            showInExplorer(target);
            // Primero la pantalla de inicio y DESPUÉS se cierra esta ventana: si no quedara
            // ninguna ventana abierta, JavaFX cerraría el programa.
            if (onRetired != null) onRetired.accept(this);
            dispose();
        });
    }

    /** "VS DIM MagnaKidmon 2026-09-28 18-40-00 (victoria vs Perfect).bin" -- solo caracteres válidos en Windows. */
    private String returnFileName(Optional<DigimonProgress.BattleReport> report) {
        String species = recognizedSpecies != null ? recognizedSpecies : "Digimon";
        species = species.replaceAll("[^A-Za-z0-9 _-]", "").trim();
        if (species.isEmpty()) species = "Digimon";
        String when = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH-mm-ss"));
        String result = report.map(r -> (r.won() ? "victoria" : "derrota") + " vs " + stageName(r.rivalStage()))
                .orElse("sin batalla");
        return "VS DIM " + species + " " + when + " (" + result + ").bin";
    }

    private static String stageName(int dimStage) {
        return switch (dimStage) {
            case 2 -> "Child";
            case 3 -> "Adult";
            case 4 -> "Perfect";
            case 5 -> "Ultimate";
            default -> "Baby";
        };
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
