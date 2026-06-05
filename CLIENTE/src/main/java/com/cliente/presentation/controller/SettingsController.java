package com.cliente.presentation.controller;

import com.cliente.application.service.MlApiConfig;
import com.cliente.infrastructure.http.MusicGenreService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;

public class SettingsController {

    @FXML private TextField        mlHostField;
    @FXML private TextField        mlPortField;
    @FXML private Label            statusLabel;
    @FXML private Button           testButton;
    @FXML private Button           saveButton;
    @FXML private ProgressIndicator progressIndicator;

    @FXML
    public void initialize() {
        MlApiConfig cfg = MlApiConfig.getInstance();
        mlHostField.setText(cfg.getHost());
        mlPortField.setText(String.valueOf(cfg.getPort()));
        statusLabel.setVisible(false);
    }

    @FXML
    private void handleSave() {
        String host    = mlHostField.getText().trim();
        String portStr = mlPortField.getText().trim();
        if (!validate(host, portStr)) return;

        try {
            MlApiConfig.getInstance().save(host, Integer.parseInt(portStr));
            showStatus("✅ Configuración guardada: " + host + ":" + portStr, true);
        } catch (Exception e) {
            showStatus("❌ Error al guardar: " + e.getMessage(), false);
        }
    }

    @FXML
    private void handleTestConnection() {
        String host    = mlHostField.getText().trim();
        String portStr = mlPortField.getText().trim();
        if (!validate(host, portStr)) return;

        // Persiste los valores actuales antes del test
        try {
            MlApiConfig.getInstance().save(host, Integer.parseInt(portStr));
        } catch (Exception e) {
            showStatus("❌ Error al guardar: " + e.getMessage(), false);
            return;
        }

        setBusy(true);

        new Thread(() -> {
            try {
                boolean available = MusicGenreService.getInstance().testConnection();
                Platform.runLater(() -> {
                    setBusy(false);
                    if (available) {
                        showStatus("✅ Servicio disponible en " + host + ":" + portStr, true);
                    } else {
                        showStatus("⚠️ El servicio respondió con un código inesperado.", false);
                    }
                });
            } catch (Throwable e) {
                Platform.runLater(() -> {
                    setBusy(false);
                    showStatus("❌ No se pudo conectar: " + friendlyError(e), false);
                });
            }
        }, "ml-test-thread").start();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private boolean validate(String host, String portStr) {
        if (host.isEmpty()) {
            showStatus("❌ El host no puede estar vacío.", false);
            return false;
        }
        try {
            int p = Integer.parseInt(portStr);
            if (p < 1 || p > 65535) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            showStatus("❌ Puerto inválido. Debe ser un número entre 1 y 65535.", false);
            return false;
        }
        return true;
    }

    private void setBusy(boolean busy) {
        progressIndicator.setVisible(busy);
        progressIndicator.setManaged(busy);
        testButton.setDisable(busy);
        saveButton.setDisable(busy);
    }

    private void showStatus(String msg, boolean success) {
        statusLabel.getStyleClass().removeAll("success-label", "error-label");
        statusLabel.getStyleClass().add(success ? "success-label" : "error-label");
        statusLabel.setText(msg);
        statusLabel.setVisible(true);
    }

    private String friendlyError(Throwable e) {
        String msg = e.getMessage();
        if (msg == null) return "Error desconocido.";
        if (msg.contains("refused"))       return "Conexión rechazada. ¿Está el servicio activo?";
        if (msg.contains("timed out") || msg.contains("timeout"))
                                           return "Tiempo de espera agotado.";
        if (msg.contains("unreachable") || msg.contains("No route"))
                                           return "Host inalcanzable.";
        return msg;
    }
}
