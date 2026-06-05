package com.cliente.presentation.controller;

import com.cliente.MainApp;
import com.cliente.application.service.ConnectionService;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.layout.StackPane;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.Optional;

public class MainController {

    @FXML private StackPane contentArea;
    @FXML private Button btnPanel;
    @FXML private Button btnClients;
    @FXML private Button btnFiles;
    @FXML private Button btnMessages;
    @FXML private Button btnLogs;
    @FXML private Button btnServidores;
    @FXML private Button btnSettings;
    @FXML private Button btnGenreClassifier;

    private Button activeNavButton;

    @FXML
    public void initialize() {
        activateNav(btnPanel);
        loadView("fxml/panel.fxml");
    }

    @FXML public void showPanel() {
        activateNav(btnPanel);
        loadView("fxml/panel.fxml");
    }

    @FXML public void showClients() {
        activateNav(btnClients);
        loadView("fxml/clients.fxml");
    }

    @FXML public void showFiles() {
        activateNav(btnFiles);
        loadView("fxml/files.fxml");
    }

    @FXML public void showMessages() {
        activateNav(btnMessages);
        loadView("fxml/messages.fxml");
    }

    @FXML public void showLogs() {
        activateNav(btnLogs);
        loadView("fxml/logs.fxml");
    }

    @FXML public void showServidores() {
        activateNav(btnServidores);
        loadView("fxml/servidores.fxml");
    }

    @FXML public void showSettings() {
        activateNav(btnSettings);
        loadView("fxml/settings.fxml");
    }

    @FXML public void showGenreClassifier() {
        activateNav(btnGenreClassifier);
        loadView("fxml/genre-classifier.fxml");
    }

    @FXML public void showUpload() {
        activateNav(null);
        loadView("fxml/upload.fxml");
    }

    @FXML
    public void showSendMessage() {
        try {
            FXMLLoader loader = new FXMLLoader(
                MainApp.class.getResource("fxml/send-message-dialog.fxml"));
            Parent root = loader.load();
            Stage dialog = new Stage();
            dialog.initOwner(MainApp.getPrimaryStage());
            dialog.initModality(Modality.WINDOW_MODAL);
            dialog.setTitle("Enviar Mensaje");
            dialog.setResizable(false);
            Scene scene = new Scene(root);
            scene.getStylesheets().add(
                MainApp.class.getResource("css/main.css").toExternalForm());
            dialog.setScene(scene);
            dialog.showAndWait();
        } catch (IOException e) {
            showError("No se pudo abrir el diálogo: " + e.getMessage());
        }
    }

    @FXML
    public void handleDisconnect() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Terminar Conexión");
        alert.setHeaderText("¿Desea desconectarse del servidor?");
        alert.setContentText("Se cerrará la sesión actual.");
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            ConnectionService.getInstance().disconnect();
            try {
                MainApp.showConnectionScreen();
            } catch (IOException e) {
                showError("Error al regresar a la pantalla de conexión.");
            }
        }
    }

    private void loadView(String fxmlPath) {
        try {
            FXMLLoader loader = new FXMLLoader(MainApp.class.getResource(fxmlPath));
            Parent view = loader.load();
            contentArea.getChildren().setAll(view);
        } catch (IOException e) {
            showError("Error al cargar la vista: " + e.getMessage());
        }
    }

    private void activateNav(Button button) {
        if (activeNavButton != null)
            activeNavButton.getStyleClass().remove("nav-item-active");
        activeNavButton = button;
        if (button != null)
            button.getStyleClass().add("nav-item-active");
    }

    private void showError(String msg) {
        new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK).showAndWait();
    }
}
