package com.cliente.application.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Singleton que guarda y carga la configuración del servicio de IA (FastAPI).
 * Los valores se persisten en ~/.p2p-client/ml-api.properties.
 */
public class MlApiConfig {

    private static final String FILE_NAME   = "ml-api.properties";
    private static final String DEFAULT_HOST = "localhost";
    private static final int    DEFAULT_PORT = 8000;

    private static MlApiConfig instance;

    private String host;
    private int    port;

    private MlApiConfig() {
        load();
    }

    public static synchronized MlApiConfig getInstance() {
        if (instance == null) instance = new MlApiConfig();
        return instance;
    }

    public String getHost() { return host; }
    public int    getPort() { return port; }

    /** Valida, persiste y actualiza la configuración en memoria. */
    public void save(String newHost, int newPort) throws IOException {
        Properties props = new Properties();
        props.setProperty("ml.api.host", newHost);
        props.setProperty("ml.api.port", String.valueOf(newPort));

        Path dir = configDir();
        Files.createDirectories(dir);
        try (OutputStream out = Files.newOutputStream(dir.resolve(FILE_NAME))) {
            props.store(out, "ML API configuration - p2p client");
        }

        this.host = newHost;
        this.port = newPort;
    }

    private void load() {
        Path file = configDir().resolve(FILE_NAME);
        Properties props = new Properties();
        if (Files.exists(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                props.load(in);
            } catch (IOException ignored) {}
        }
        host = props.getProperty("ml.api.host", DEFAULT_HOST);
        try {
            port = Integer.parseInt(props.getProperty("ml.api.port", String.valueOf(DEFAULT_PORT)));
            if (port < 1 || port > 65535) port = DEFAULT_PORT;
        } catch (NumberFormatException e) {
            port = DEFAULT_PORT;
        }
    }

    private static Path configDir() {
        return Path.of(System.getProperty("user.home"), ".p2p-client");
    }
}
