package com.cliente.infrastructure.http;

import com.cliente.application.service.MlApiConfig;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;

/**
 * Cliente HTTP que se comunica con el servicio FastAPI de clasificación de géneros musicales.
 * Usa HttpURLConnection (java.base) para evitar problemas de módulos con java.net.http.
 *
 * Endpoints:
 *   GET  /         — health check
 *   POST /predict  — recibe WAV, retorna JSON con género predicho
 */
public class MusicGenreService {

    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int PREDICT_TIMEOUT_MS = 90_000;

    private static MusicGenreService instance;

    private MusicGenreService() {}

    public static synchronized MusicGenreService getInstance() {
        if (instance == null) instance = new MusicGenreService();
        return instance;
    }

    /**
     * Verifica que el servicio esté disponible haciendo GET /.
     *
     * @return true si responde con HTTP 200
     */
    public boolean testConnection() throws Exception {
        MlApiConfig cfg = MlApiConfig.getInstance();
        String urlStr = "http://" + cfg.getHost() + ":" + cfg.getPort() + "/";
        HttpURLConnection conn = openConnection(urlStr, CONNECT_TIMEOUT_MS, CONNECT_TIMEOUT_MS);
        try {
            conn.setRequestMethod("GET");
            return conn.getResponseCode() == 200;
        } finally {
            conn.disconnect();
        }
    }

    /**
     * Envía un archivo WAV a /predict y retorna la respuesta JSON.
     *
     * @param wavFile archivo WAV a clasificar (debe terminar en .wav)
     * @return JSON: { "predicted_genre": "rock", "confidence": 0.91, "probabilities": {...} }
     */
    public String predictGenre(File wavFile) throws Exception {
        if (!wavFile.getName().toLowerCase().endsWith(".wav")) {
            throw new IllegalArgumentException("El archivo debe tener extensión .wav");
        }

        MlApiConfig cfg = MlApiConfig.getInstance();
        String urlStr   = "http://" + cfg.getHost() + ":" + cfg.getPort() + "/predict";
        String boundary = UUID.randomUUID().toString().replace("-", "");
        byte[] body     = buildMultipart(boundary, wavFile.getName(),
                                         Files.readAllBytes(wavFile.toPath()));

        HttpURLConnection conn = openConnection(urlStr, CONNECT_TIMEOUT_MS, PREDICT_TIMEOUT_MS);
        try {
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type",
                    "multipart/form-data; boundary=" + boundary);
            conn.setRequestProperty("Content-Length", String.valueOf(body.length));

            try (OutputStream os = conn.getOutputStream()) {
                os.write(body);
            }

            int status = conn.getResponseCode();
            if (status != 200) {
                String errorBody = readStream(conn.getErrorStream());
                throw new IOException(
                        "El servicio respondió con código " + status + ": " + errorBody);
            }
            return readStream(conn.getInputStream());
        } finally {
            conn.disconnect();
        }
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private HttpURLConnection openConnection(String urlStr, int connectMs, int readMs)
            throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(connectMs);
        conn.setReadTimeout(readMs);
        return conn;
    }

    private byte[] buildMultipart(String boundary, String filename, byte[] fileBytes)
            throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        String partHeader = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n"
                + "Content-Type: audio/wav\r\n"
                + "\r\n";
        out.write(partHeader.getBytes(StandardCharsets.UTF_8));
        out.write(fileBytes);

        String footer = "\r\n--" + boundary + "--\r\n";
        out.write(footer.getBytes(StandardCharsets.UTF_8));

        return out.toByteArray();
    }

    private String readStream(InputStream is) throws IOException {
        if (is == null) return "";
        try (is) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

