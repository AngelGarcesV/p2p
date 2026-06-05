package com.cliente.presentation.controller;

import com.cliente.infrastructure.http.MusicGenreService;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.stage.FileChooser;
import javafx.util.Duration;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GenreClassifierController {

    private static final int MAX_DURATION_SECONDS = 30;

    @FXML private VBox   dropZone;
    @FXML private HBox   fileInfoBox;
    @FXML private Label  fileNameLabel;
    @FXML private Label  fileDurationLabel;
    @FXML private Label  errorLabel;
    @FXML private VBox   playerBox;
    @FXML private Slider positionSlider;
    @FXML private Label  currentTimeLabel;
    @FXML private Label  totalTimeLabel;
    @FXML private Button playPauseButton;
    @FXML private Button predictButton;
    @FXML private ProgressIndicator predictProgress;
    @FXML private Label  predictStatusLabel;
    @FXML private VBox   resultsBox;
    @FXML private Label  predictedGenreLabel;
    @FXML private Label  confidenceLabel;
    @FXML private VBox   probabilitiesContainer;

    private File        selectedFile;
    private MediaPlayer mediaPlayer;
    private boolean     userDragging      = false;
    private Task<?>     currentPredictTask;

    @FXML
    public void initialize() {
        positionSlider.setOnMousePressed(e -> userDragging = true);
        positionSlider.setOnMouseReleased(e -> {
            if (mediaPlayer != null) {
                double totalSecs = mediaPlayer.getTotalDuration().toSeconds();
                mediaPlayer.seek(Duration.seconds(positionSlider.getValue() * totalSecs));
            }
            userDragging = false;
        });

        // Cleanup when this view is removed from the scene graph
        predictButton.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) cleanup();
        });
    }

    // ── File selection ────────────────────────────────────────────────────────

    @FXML
    private void handleSelectFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Seleccionar archivo WAV");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Archivos de audio WAV (*.wav)", "*.wav"));
        File file = chooser.showOpenDialog(predictButton.getScene().getWindow());
        if (file == null) return;

        // Dispose previous player and reset UI
        disposePlayer();
        clearResults();
        hideError();
        hide(fileInfoBox);
        hide(playerBox);
        predictButton.setDisable(true);

        // Validate duration
        double durationSecs;
        try {
            durationSecs = readWavDurationSeconds(file);
        } catch (Exception e) {
            showError("No se pudo leer la duración del archivo WAV: " + e.getMessage());
            return;
        }

        if (durationSecs > MAX_DURATION_SECONDS) {
            showError(String.format(
                    "❌ Duración no permitida: %.1f s — el máximo es %d s. " +
                    "Seleccione un audio más corto.",
                    durationSecs, MAX_DURATION_SECONDS));
            return;
        }

        // File accepted — show info
        selectedFile = file;
        fileNameLabel.setText(file.getName());
        fileDurationLabel.setText(String.format("%.1f segundos", durationSecs));
        show(fileInfoBox);

        // Build media player
        setupMediaPlayer(file);
        show(playerBox);
        playPauseButton.setText("▶  Reproducir");
        predictButton.setDisable(false);
    }

    // ── Playback ──────────────────────────────────────────────────────────────

    @FXML
    private void handlePlayPause() {
        if (mediaPlayer == null) return;
        if (mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING) {
            mediaPlayer.pause();
            playPauseButton.setText("▶  Reproducir");
        } else {
            mediaPlayer.play();
            playPauseButton.setText("⏸  Pausar");
        }
    }

    @FXML
    private void handleStop() {
        if (mediaPlayer == null) return;
        mediaPlayer.stop();
        mediaPlayer.seek(Duration.ZERO);
        positionSlider.setValue(0);
        currentTimeLabel.setText("0:00");
        playPauseButton.setText("▶  Reproducir");
    }

    // ── Prediction ────────────────────────────────────────────────────────────

    @FXML
    private void handlePredict() {
        if (selectedFile == null) return;

        predictButton.setDisable(true);
        show(predictProgress);
        show(predictStatusLabel);
        predictStatusLabel.setText("Analizando audio...");
        clearResults();

        final File target = selectedFile;

        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
                return MusicGenreService.getInstance().predictGenre(target);
            }
        };

        task.setOnSucceeded(e -> {
            hide(predictProgress);
            hide(predictStatusLabel);
            predictButton.setDisable(false);
            // Only show results if the user hasn't changed the file
            if (target.equals(selectedFile)) {
                showResults(task.getValue());
            }
        });

        task.setOnFailed(e -> {
            hide(predictProgress);
            hide(predictStatusLabel);
            predictButton.setDisable(false);
            Throwable ex = task.getException();
            String msg = ex != null ? ex.getMessage() : "Error desconocido";
            showError("❌ Error al predecir: " + msg);
        });

        task.setOnCancelled(e -> {
            hide(predictProgress);
            hide(predictStatusLabel);
            predictButton.setDisable(false);
        });

        currentPredictTask = task;
        Thread thread = new Thread(task, "predict-thread");
        thread.setDaemon(true);
        thread.start();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void setupMediaPlayer(File file) {
        Media media = new Media(file.toURI().toString());
        mediaPlayer = new MediaPlayer(media);

        mediaPlayer.setOnReady(() -> {
            positionSlider.setValue(0);
            currentTimeLabel.setText("0:00");
            totalTimeLabel.setText(formatDuration(mediaPlayer.getTotalDuration()));
        });

        mediaPlayer.currentTimeProperty().addListener((obs, old, now) -> {
            if (userDragging) return;
            Duration total = mediaPlayer.getTotalDuration();
            if (total != null && total.toSeconds() > 0) {
                positionSlider.setValue(now.toSeconds() / total.toSeconds());
            }
            currentTimeLabel.setText(formatDuration(now));
        });

        mediaPlayer.setOnEndOfMedia(() -> {
            mediaPlayer.stop();
            mediaPlayer.seek(Duration.ZERO);
            positionSlider.setValue(0);
            currentTimeLabel.setText("0:00");
            playPauseButton.setText("▶  Reproducir");
        });
    }

    private void showResults(String json) {
        try {
            JsonObject obj     = JsonParser.parseString(json).getAsJsonObject();
            String genre       = obj.get("predicted_genre").getAsString();
            double confidence  = obj.get("confidence").getAsDouble();
            JsonObject probs   = obj.getAsJsonObject("probabilities");

            predictedGenreLabel.setText(capitalize(genre));
            confidenceLabel.setText(String.format("Confianza: %.1f%%", confidence * 100));

            // Sort genres by descending probability
            probabilitiesContainer.getChildren().clear();
            List<Map.Entry<String, JsonElement>> sorted = new ArrayList<>(probs.entrySet());
            sorted.sort((a, b) ->
                    Double.compare(b.getValue().getAsDouble(), a.getValue().getAsDouble()));

            for (Map.Entry<String, JsonElement> entry : sorted) {
                double prob = entry.getValue().getAsDouble();
                probabilitiesContainer.getChildren().add(
                        buildProbabilityRow(capitalize(entry.getKey()), prob));
            }

            show(resultsBox);
        } catch (Exception e) {
            showError("Error al interpretar la respuesta del servicio: " + e.getMessage());
        }
    }

    private HBox buildProbabilityRow(String genre, double probability) {
        Label nameLabel = new Label(genre);
        nameLabel.setMinWidth(120);
        nameLabel.getStyleClass().add("info-key");

        ProgressBar bar = new ProgressBar(probability);
        bar.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(bar, Priority.ALWAYS);
        bar.getStyleClass().add("genre-prob-bar");
        if (probability >= 0.5) {
            bar.getStyleClass().add("genre-prob-bar-high");
        }

        Label pctLabel = new Label(String.format("%.1f%%", probability * 100));
        pctLabel.setMinWidth(55);
        pctLabel.getStyleClass().add("info-value");

        HBox row = new HBox(10, nameLabel, bar, pctLabel);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    /** Reads WAV duration using javax.sound.sampled (standard PCM WAV). */
    private double readWavDurationSeconds(File file) throws Exception {
        try (AudioInputStream ais = AudioSystem.getAudioInputStream(file)) {
            long frames  = ais.getFrameLength();
            float rate   = ais.getFormat().getFrameRate();
            if (frames <= 0 || rate <= 0 || Float.isInfinite(rate) || Float.isNaN(rate)) {
                throw new Exception("Metadatos de audio no válidos o formato no soportado.");
            }
            return frames / (double) rate;
        }
    }

    private void disposePlayer() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.dispose();
            mediaPlayer = null;
        }
    }

    private void cleanup() {
        if (currentPredictTask != null) {
            currentPredictTask.cancel();
            currentPredictTask = null;
        }
        disposePlayer();
    }

    private void clearResults() {
        hide(resultsBox);
        probabilitiesContainer.getChildren().clear();
    }

    private void showError(String msg) {
        errorLabel.setText(msg);
        show(errorLabel);
    }

    private void hideError() {
        hide(errorLabel);
    }

    private void show(Node node) {
        node.setVisible(true);
        node.setManaged(true);
    }

    private void hide(Node node) {
        node.setVisible(false);
        node.setManaged(false);
    }

    private String formatDuration(Duration d) {
        if (d == null || d.isUnknown() || d.isIndefinite()) return "0:00";
        int totalSecs = (int) d.toSeconds();
        return String.format("%d:%02d", totalSecs / 60, totalSecs % 60);
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }
}
