package com.cliente.presentation.controller;

import javafx.fxml.FXML;

/**
 * Controlador de la pantalla de Configuración.
 * La configuración del servicio ML fue eliminada: ahora es gestionada
 * por el servidor (application.properties del servidor Java).
 */
public class SettingsController {

    @FXML
    public void initialize() {
        // Sin configuración cliente-side de ML — el servidor gestiona la conexión a Python.
    }
}
