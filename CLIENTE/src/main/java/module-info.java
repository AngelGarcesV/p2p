module com.cliente {
    requires javafx.controls;
    requires javafx.fxml;
    requires com.google.gson;
    requires java.base;
    requires java.sql;
    requires java.desktop;
    requires javafx.media;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.datatype.jsr310;
    requires com.arquitectura.mensajeria;

    opens com.cliente to javafx.fxml;
    opens com.cliente.presentation.controller to javafx.fxml;
    opens com.cliente.domain.model to com.google.gson, com.fasterxml.jackson.databind;

    exports com.cliente;
    exports com.cliente.domain.model;
    exports com.cliente.domain.enums;
    exports com.cliente.infrastructure.socket;
    exports com.cliente.infrastructure.protocol;
    exports com.cliente.infrastructure.persistence;
    exports com.cliente.application.service;
    exports com.cliente.presentation.controller;
}
