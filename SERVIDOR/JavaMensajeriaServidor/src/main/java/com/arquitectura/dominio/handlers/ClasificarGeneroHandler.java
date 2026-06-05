package com.arquitectura.dominio.handlers;

import com.arquitectura.aplicacion.ContextoSolicitud;
import com.arquitectura.aplicacion.ml.MlProxyConfig;
import com.arquitectura.aplicacion.router.Handler;
import com.arquitectura.aplicacion.sesion.GestorSesiones;
import com.arquitectura.aplicacion.sesion.ResultadoValidacionSesion;
import com.arquitectura.mensajeria.ErrorDetalle;
import com.arquitectura.mensajeria.Mensaje;
import com.arquitectura.mensajeria.Metadata;
import com.arquitectura.mensajeria.Respuesta;
import com.arquitectura.mensajeria.enums.Accion;
import com.arquitectura.mensajeria.enums.Estado;
import com.arquitectura.mensajeria.enums.TipoMensaje;
import com.arquitectura.mensajeria.payload.PayloadClasificarGenero;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Handler para CLASIFICAR_GENERO.
 * Recibe el WAV en Base64, lo reenvía como multipart/form-data al servicio
 * Python (FastAPI) usando java.net.http.HttpClient y retorna la predicción JSON.
 */
public class ClasificarGeneroHandler implements Handler<PayloadClasificarGenero> {

    private static final Logger LOGGER = Logger.getLogger(ClasificarGeneroHandler.class.getName());

    private final GestorSesiones gestorSesiones = GestorSesiones.getInstance();

    @Override
    public Respuesta<?> handle(Mensaje<PayloadClasificarGenero> mensaje) {

        // ── 1. Validar sesion ────────────────────────────────────────────────
        String remitente = resolverRemitente(mensaje);
        ResultadoValidacionSesion validacion = gestorSesiones.validarSesion(
                remitente,
                ContextoSolicitud.obtenerIpRemitente(),
                puertoRemitente(),
                ContextoSolicitud.obtenerProtocolo()
        );
        if (!validacion.exito()) {
            return crearError(validacion.codigoError(), validacion.mensaje());
        }

        // ── 2. Validar payload ───────────────────────────────────────────────
        PayloadClasificarGenero payload = mensaje.getPayload();
        if (payload == null
                || payload.getContenidoBase64() == null
                || payload.getContenidoBase64().isBlank()) {
            return crearError("PAYLOAD_INVALIDO", "El payload no contiene contenidoBase64.");
        }

        // ── 3. Decodificar Base64 ────────────────────────────────────────────
        byte[] wavBytes;
        try {
            wavBytes = Base64.getDecoder().decode(payload.getContenidoBase64());
        } catch (IllegalArgumentException e) {
            return crearError("PAYLOAD_INVALIDO", "contenidoBase64 no es Base64 válido: " + e.getMessage());
        }

        String nombreArchivo = (payload.getNombreArchivo() != null && !payload.getNombreArchivo().isBlank())
                ? payload.getNombreArchivo() : "audio.wav";

        // ── 4. Construir multipart/form-data ─────────────────────────────────
        // Boundary sin guiones iniciales: python-multipart (starlette) puede
        // interpretar "----X" como que ya lleva el prefijo "--" y stripear 2 chars,
        // quedando desalineado con el cuerpo y retornando 422.
        String boundary = "WavBoundary" + UUID.randomUUID().toString().replace("-", "");
        byte[] cuerpo;
        try {
            cuerpo = construirMultipart(boundary, nombreArchivo, wavBytes);
        } catch (IOException e) {
            return crearError("PAYLOAD_INVALIDO", "No se pudo construir el multipart: " + e.getMessage());
        }

        // ── 5. POST al servicio Python ───────────────────────────────────────
        String url = MlProxyConfig.getInstance().getBaseUrl() + "/predict";
        LOGGER.info(() -> "CLASIFICAR_GENERO | remitente=%s | archivo=%s | bytes=%d | url=%s"
                .formatted(remitente, nombreArchivo, wavBytes.length, url));

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .version(HttpClient.Version.HTTP_1_1)
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(120))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(cuerpo))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (HttpTimeoutException e) {
            LOGGER.warning("Timeout al contactar servicio ML: " + e.getMessage());
            return crearError("ML_TIMEOUT", "Servicio ML no respondió en 120s");
        } catch (ConnectException e) {
            LOGGER.warning("Conexión rechazada al servicio ML: " + e.getMessage());
            return crearError("ML_NO_DISPONIBLE", "No se pudo conectar al servicio ML");
        } catch (IOException e) {
            String msg = e.getMessage();
            LOGGER.warning("Error IO al contactar servicio ML: " + msg);
            if (msg != null && msg.toLowerCase().contains("refused")) {
                return crearError("ML_NO_DISPONIBLE", "No se pudo conectar al servicio ML");
            }
            return crearError("ML_NO_DISPONIBLE", "Error de red al contactar servicio ML: " + msg);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return crearError("ML_NO_DISPONIBLE", "Solicitud al servicio ML interrumpida");
        }

        // ── 6. Procesar respuesta HTTP ───────────────────────────────────────
        int status = response.statusCode();
        if (status != 200) {
            LOGGER.warning("Servicio ML respondió con HTTP " + status + ": " + response.body());
            return crearError("ML_ERROR", "Servicio ML retornó: " + status + " - " + response.body());
        }

        String jsonPredicion = response.body();
        LOGGER.info(() -> "Predicción recibida | remitente=%s | respuesta=%s"
                .formatted(remitente, jsonPredicion));

        // ── 7. Retornar éxito ────────────────────────────────────────────────
        Mensaje<String> mensajeResp = new Mensaje<>();
        mensajeResp.setTipo(TipoMensaje.RESPONSE);
        mensajeResp.setAccion(Accion.CLASIFICAR_GENERO);
        mensajeResp.setMetadata(crearMetadata());
        mensajeResp.setPayload(jsonPredicion);

        Respuesta<String> respuesta = new Respuesta<>();
        respuesta.setEstado(Estado.EXITO);
        respuesta.setMensaje(mensajeResp);
        return respuesta;
    }

    @Override
    public Class<PayloadClasificarGenero> getPayloadClass() {
        return PayloadClasificarGenero.class;
    }

    // ─── helpers privados ─────────────────────────────────────────────────────

    private byte[] construirMultipart(String boundary, String nombreArchivo, byte[] wavBytes)
            throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String preambulo = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"" + nombreArchivo + "\"\r\n"
                + "Content-Type: audio/wav\r\n\r\n";
        String epilogo = "\r\n--" + boundary + "--\r\n";
        out.write(preambulo.getBytes(StandardCharsets.UTF_8));
        out.write(wavBytes);
        out.write(epilogo.getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    private String resolverRemitente(Mensaje<PayloadClasificarGenero> mensaje) {
        if (mensaje.getMetadata() != null && mensaje.getMetadata().getClientId() != null) {
            return mensaje.getMetadata().getClientId();
        }
        return "desconocido";
    }

    private int puertoRemitente() {
        Integer puerto = ContextoSolicitud.obtenerPuertoRemitente();
        return puerto == null ? -1 : puerto;
    }

    private Respuesta<?> crearError(String codigo, String detalle) {
        Respuesta<?> respuesta = new Respuesta<>();
        respuesta.setEstado(Estado.ERROR);
        respuesta.setError(new ErrorDetalle(codigo, detalle));
        return respuesta;
    }

    private Metadata crearMetadata() {
        Metadata meta = new Metadata();
        meta.setIdMensaje(UUID.randomUUID().toString());
        meta.setTimestamp(LocalDateTime.now());
        return meta;
    }
}
