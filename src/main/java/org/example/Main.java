package org.example;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.FileChooser;
import javafx.stage.Screen;
import javafx.stage.Stage;

import org.example.behavior.DigimonInstance;
import org.example.behavior.DigimonRegistry;
import org.example.chat.AppPaths;
import org.example.chat.AssistantSettings;
import org.example.chat.OllamaClient;
import org.example.dim.VsDimData;
import org.example.dim.VsDimReader;
import org.example.ui.StartScreen;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

/**
 * 0.0.3: asistente virtual con la cara de tu Digimon. Sin crianza (huevo,
 * selección, muerte ni renacimiento se quitaron). La única forma de tener un
 * Digimon es traerlo del Vital Bracelet con una VS DIM (decisión del usuario),
 * y la única forma de devolverlo es RETIRARLO (menú V-PET), que genera la VS
 * DIM de vuelta con el saldo de sus batallas.
 *
 * Con -Ddigimon.edicion=tester es 0.0.3.1 (probadores): sin asistente ni
 * chat IA, no usa Ollama (ver Edition).
 */
public class Main extends Application {

    private static final int MAX_DIGIMON = 2;

    private Stage navigationStage;
    private final DigimonRegistry registry = new DigimonRegistry();
    private OllamaClient ollamaClient;

    /** Lo que se pregunta al importar: la VS DIM no guarda la edad (ni, sin la IA, la especie legible). */
    private record ImportAnswers(int ageDays, String species) {}

    @Override
    public void start(Stage stage) {
        AppPaths.base(); // deja lista la carpeta de datos desde el arranque

        this.navigationStage = stage;
        if (Edition.ASSISTANT) {
            this.ollamaClient = new OllamaClient("http://localhost:11434", "ministral-3:3b"); // elegido tras comparar modelos (ver CLAUDE.md)
            ollamaClient.warmUp();
// TEMPORAL - PRUEBA AttackSpriteResolver: quitar esta línea una vez confirmado.
            new org.example.battle.AttackSpriteResolver().runDiagnostics();
        }
        stage.setTitle("V-PET " + Edition.VERSION);
        showStartScreen();
    }

    /** Nombre y servidor guardados de la vez anterior: se piden antes de cargar la VS DIM. */
    private void showStartScreen() {
        navigationStage.setScene(StartScreen.build(AssistantSettings.savedPlayerName(), AssistantSettings.onlineHost(),
                this::onLoadVsDim));
        navigationStage.show();
        navigationStage.toFront();
    }

    // ---------- VS DIM: Digimon traído del Vital Bracelet ----------

    private void onLoadVsDim(String playerName, String host) {
        AssistantSettings.saveStartScreen(playerName, host);
        if (registry.getActiveInstances().size() >= MAX_DIGIMON) {
            showError("Ya hay " + MAX_DIGIMON + " Digimon activos; no cabe otro.");
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Selecciona una VS DIM (.bin)");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("VS DIM", "*.bin"));
        File file = chooser.showOpenDialog(navigationStage);
        if (file == null) return;

        // Se valida ANTES de pedir la edad: si no es una VS DIM con Digimon, se avisa y ya.
        try {
            VsDimData vs = VsDimReader.read(file.toPath());
            System.out.println("VS DIM: slot=" + vs.slot() + " vitals=" + vs.vitalValues()
                    + " powerTrophies=" + vs.powerTrophies());
        } catch (IOException ex) {
            showError(ex.getMessage());
            return;
        }

        Optional<ImportAnswers> answers = askImportAnswers();
        if (answers.isEmpty()) return;

        navigationStage.hide();

        // Llega por el portal del centro de la pantalla; esta posición solo le da la dirección de salida.
        double offset = registry.getActiveInstances().size() * 140;
        double x = Screen.getPrimary().getVisualBounds().getMaxX() - 150 - offset;
        double y = Screen.getPrimary().getVisualBounds().getMaxY() - 56;
        DigimonInstance instance = DigimonInstance.fromVsDim(file.toPath(), answers.get().ageDays());
        if (!Edition.ASSISTANT) instance.setTypedSpecies(answers.get().species());
        instance.setOnRetired(retired -> {
            registry.remove(retired.getInstanceId());
            showStartScreen();
        });
        registry.add(instance);
        try {
            instance.spawn(x, y, ollamaClient, registry);
        } catch (Exception ex) {
            registry.remove(instance.getInstanceId());
            navigationStage.show();
            showError("No se pudo iniciar el Digimon de la VS DIM: " + ex.getMessage());
        }
    }

    /**
     * La VS DIM no guarda la edad: se le pregunta al usuario. En 0.0.3.1
     * también la especie (sin el modelo de visión no se puede leer el sprite
     * NAME); vacía = "Digimon". Vacío el Optional = canceló.
     */
    private Optional<ImportAnswers> askImportAnswers() {
        while (true) {
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.initOwner(navigationStage);
            dialog.setTitle("Tu Digimon");
            dialog.setHeaderText("La VS DIM no guarda la edad del Digimon.");
            TextField ageField = new TextField("0");
            TextField speciesField = new TextField();
            speciesField.setPromptText("Ej.: MagnaKidmon");
            GridPane grid = new GridPane();
            grid.setHgap(8);
            grid.setVgap(8);
            grid.setPadding(new Insets(10));
            grid.addRow(0, new Label("¿Cuántos días tenía en el Vital Bracelet?"), ageField);
            if (!Edition.ASSISTANT) grid.addRow(1, new Label("¿Qué Digimon es? (especie)"), speciesField);
            dialog.getDialogPane().setContent(grid);
            dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

            if (dialog.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return Optional.empty();
            try {
                int days = Integer.parseInt(ageField.getText().trim());
                String species = speciesField.getText().replaceAll("[^A-Za-z0-9 .\\-]", "").trim();
                if (species.length() > 24) species = species.substring(0, 24);
                if (days >= 0) return Optional.of(new ImportAnswers(days, species));
            } catch (NumberFormatException ignored) {
                // se vuelve a preguntar
            }
            showError("Escribe un número de días (0 o más).");
        }
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message);
        alert.setHeaderText(null);
        alert.initOwner(navigationStage);
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
