package com.arquitectura.dominio.handlers;

import com.arquitectura.aplicacion.ContextoSolicitud;
import com.arquitectura.aplicacion.replicacion.ReplicadorArchivos;
import com.arquitectura.aplicacion.router.Handler;
import com.arquitectura.aplicacion.sesion.GestorSesiones;
import com.arquitectura.aplicacion.sesion.GestorServidoresPeer;
import com.arquitectura.aplicacion.sesion.ResultadoValidacionSesion;
import com.arquitectura.dominio.repositorios.ArchivoRecibidoRepository;
import com.arquitectura.dominio.repositorios.JpaArchivoRecibidoRepository;
import com.arquitectura.infraestructura.seguridad.CryptoUtil;
import com.arquitectura.mensajeria.ErrorDetalle;
import com.arquitectura.mensajeria.Mensaje;
import com.arquitectura.mensajeria.Metadata;
import com.arquitectura.mensajeria.Respuesta;
import com.arquitectura.mensajeria.enums.Accion;
import com.arquitectura.mensajeria.enums.Estado;
import com.arquitectura.mensajeria.enums.TipoMensaje;
import com.arquitectura.mensajeria.payload.PayloadClienteRemoto;
import com.arquitectura.mensajeria.payload.PayloadEnviarArchivo;
import com.arquitectura.mensajeria.payload.PayloadReplicarArchivo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Logger;

public class EnviarArchivoHandler implements Handler<PayloadEnviarArchivo> {

    private static final Logger LOGGER = Logger.getLogger(EnviarArchivoHandler.class.getName());
    private static final Path DIRECTORIO_DESTINO = Path.of("archivos-recibidos");
    private final ArchivoRecibidoRepository archivoRecibidoRepository = new JpaArchivoRecibidoRepository();
    private final GestorSesiones gestorSesiones = GestorSesiones.getInstance();

    @Override
    public Respuesta<?> handle(Mensaje<PayloadEnviarArchivo> mensaje) {
        PayloadEnviarArchivo payload = mensaje.getPayload();
        String remitente = resolverRemitente(mensaje);
        ResultadoValidacionSesion validacion = gestorSesiones.validarSesion(
                remitente,
                ContextoSolicitud.obtenerIpRemitente(),
                puertoRemitente(),
                ContextoSolicitud.obtenerProtocolo()
        );
        if (!validacion.exito()) {
            return crearErrorSesion(validacion.codigoError(), validacion.mensaje());
        }

        String ipRemitente = ContextoSolicitud.obtenerIpRemitente();
        String mensajeId = resolverMensajeId(mensaje);
        LocalDateTime fechaRecepcion = resolverFecha(mensaje);
        byte[] contenidoArchivo = obtenerContenido(payload);
        String hashSha256 = CryptoUtil.sha256Base64(contenidoArchivo);
        String contenidoCifrado = CryptoUtil.aesEncryptBase64(contenidoArchivo);

        String clientIdDestino = (payload.getClientIdDestino() != null
                && !payload.getClientIdDestino().isBlank())
                ? payload.getClientIdDestino().trim() : null;

        GestorServidoresPeer gestorPeers = GestorServidoresPeer.getInstance();

        // --- Determinar si el destinatario es local o está en otro peer ---
        // Esto se evalúa ANTES de guardar en disco para evitar almacenar archivos
        // que no corresponden a este servidor (unicast para cliente remoto).
        boolean esUnicast = clientIdDestino != null;
        boolean destinatarioLocal = !esUnicast || gestorSesiones.existeSesionActiva(clientIdDestino);
        String peerDestinoId = null;

        if (esUnicast && !destinatarioLocal) {
            peerDestinoId = encontrarPeerDeCliente(clientIdDestino, gestorPeers);
            if (peerDestinoId == null) {
                // Destinatario desconocido en toda la red — igual guardar localmente
                // como fallback para que el cliente pueda buscarlo cuando se conecte.
                LOGGER.warning(() -> "Destinatario [" + clientIdDestino
                        + "] no encontrado en peers conocidos — archivo guardado localmente como fallback");
                destinatarioLocal = true;
            }
        }

        try {
            Path rutaArchivo = guardarArchivo(payload, contenidoArchivo);
            String nombreFinalArchivo = rutaArchivo.getFileName().toString();

            if (destinatarioLocal) {
                // Broadcast o unicast local: guardar en este servidor
                archivoRecibidoRepository.guardar(
                        mensajeId,
                        remitente,
                        ipRemitente,
                        extraerNombreBase(nombreFinalArchivo),
                        extraerExtension(nombreFinalArchivo),
                        rutaArchivo.toAbsolutePath().toString(),
                        hashSha256,
                        contenidoCifrado,
                        payload.getTamano(),
                        fechaRecepcion,
                        null,
                        clientIdDestino
                );

                if (!esUnicast) {
                    // Broadcast: replicar a todos los peers
                    archivoRecibidoRepository.buscarPorId(mensajeId).ifPresent(modelo ->
                            new ReplicadorArchivos().replicar(modelo, gestorPeers.getServidorId()));
                    LOGGER.info(() -> "Archivo broadcast replicado a peers | " + nombreFinalArchivo);
                } else {
                    // Unicast local: replicar también a todos los peers para distribución
                    PayloadReplicarArchivo replicaLocalPayload = new PayloadReplicarArchivo();
                    replicaLocalPayload.setId(mensajeId);
                    replicaLocalPayload.setRemitente(remitente);
                    replicaLocalPayload.setNombreArchivo(extraerNombreBase(nombreFinalArchivo));
                    replicaLocalPayload.setExtension(extraerExtension(nombreFinalArchivo));
                    replicaLocalPayload.setTamano(payload.getTamano());
                    replicaLocalPayload.setHashSha256(hashSha256);
                    replicaLocalPayload.setContenidoCifrado(contenidoCifrado);
                    replicaLocalPayload.setServidorOrigen(gestorPeers.getServidorId());
                    replicaLocalPayload.setClientIdDestino(clientIdDestino);

                    Mensaje<PayloadReplicarArchivo> mensajeReplicaLocal = new Mensaje<>();
                    mensajeReplicaLocal.setTipo(TipoMensaje.REQUEST);
                    mensajeReplicaLocal.setAccion(Accion.REPLICAR_ARCHIVO);
                    mensajeReplicaLocal.setMetadata(crearMetadataRespuesta());
                    mensajeReplicaLocal.setPayload(replicaLocalPayload);

                    gestorPeers.enviarATodos(mensajeReplicaLocal);
                    LOGGER.info(() -> "Archivo unicast para [" + clientIdDestino
                            + "] replicado a todos los peers | " + nombreFinalArchivo);
                }
            } else {
                // Unicast para cliente en otro servidor: distribuir a TODOS los peers
                // (no solo al peer dueño) para garantizar entrega sin importar
                // a qué servidor esté conectado el destinatario.
                final String peerFinal = peerDestinoId;
                PayloadReplicarArchivo replicarPayload = new PayloadReplicarArchivo();
                replicarPayload.setId(mensajeId);
                replicarPayload.setRemitente(remitente);
                replicarPayload.setNombreArchivo(extraerNombreBase(nombreFinalArchivo));
                replicarPayload.setExtension(extraerExtension(nombreFinalArchivo));
                replicarPayload.setTamano(payload.getTamano());
                replicarPayload.setHashSha256(hashSha256);
                replicarPayload.setContenidoCifrado(contenidoCifrado);
                replicarPayload.setServidorOrigen(gestorPeers.getServidorId());
                replicarPayload.setClientIdDestino(clientIdDestino);

                Mensaje<PayloadReplicarArchivo> mensajePeer = new Mensaje<>();
                mensajePeer.setTipo(TipoMensaje.REQUEST);
                mensajePeer.setAccion(Accion.REPLICAR_ARCHIVO);
                mensajePeer.setMetadata(crearMetadataRespuesta());
                mensajePeer.setPayload(replicarPayload);

                gestorPeers.enviarATodos(mensajePeer);
                LOGGER.info(() -> "Archivo unicast para [" + clientIdDestino
                        + "] distribuido a todos los peers | " + nombreFinalArchivo
                        + " (peer dueño: " + peerFinal + ")");
                // Limpiar el archivo temporal del disco de este servidor tras distribución
                try {
                    Files.deleteIfExists(rutaArchivo);
                    LOGGER.fine(() -> "Archivo temporal eliminado tras distribución: " + rutaArchivo);
                } catch (IOException ex) {
                    LOGGER.warning(() -> "No se pudo eliminar archivo temporal: " + rutaArchivo);
                }
            }

            LOGGER.info(() -> "Archivo recibido: " + nombreFinalArchivo + " desde " + remitente + " ("
                    + ipRemitente + ") | Hash: " + hashSha256);
            System.out.println("[SERVIDOR] Archivo recibido de " + remitente + " (" + ipRemitente + "): "
                    + payload.getNombre());

            return crearRespuestaExitosa(payload.getNombre(),
                    destinatarioLocal ? Path.of("archivos-recibidos").resolve(payload.getNombre()) : Path.of(payload.getNombre()));
        } catch (IOException e) {
            LOGGER.severe(() -> "No fue posible guardar el archivo " + payload.getNombre() + ": " + e.getMessage());
            throw new IllegalStateException("No fue posible guardar el archivo recibido", e);
        }
    }

    @Override
    public Class<PayloadEnviarArchivo> getPayloadClass() {
        return PayloadEnviarArchivo.class;
    }

    private Path guardarArchivo(PayloadEnviarArchivo payload, byte[] contenidoArchivo) throws IOException {
        Files.createDirectories(DIRECTORIO_DESTINO);
        String nombreArchivoNormalizado = construirNombreArchivo(payload);
        Path rutaArchivo = resolverRutaSinColision(nombreArchivoNormalizado);
        Files.write(rutaArchivo, contenidoArchivo);
        return rutaArchivo;
    }

    private String construirNombreArchivo(PayloadEnviarArchivo payload) {
        String extension = payload.getExtension();
        String nombre = payload.getNombre();
        if (extension == null || extension.isBlank() || nombre.endsWith("." + extension)) {
            return nombre;
        }
        return nombre + "." + extension;
    }

    private Path resolverRutaSinColision(String nombreArchivo) {
        Path rutaInicial = DIRECTORIO_DESTINO.resolve(nombreArchivo);
        if (!Files.exists(rutaInicial)) {
            return rutaInicial;
        }

        String extension = extraerExtension(nombreArchivo);
        String nombreBase = extraerNombreBase(nombreArchivo);
        int contador = 1;
        Path rutaCandidata = rutaInicial;
        while (Files.exists(rutaCandidata)) {
            String nombreConSufijo = extension.isBlank()
                    ? nombreBase + " (" + contador + ")"
                    : nombreBase + " (" + contador + ")." + extension;
            rutaCandidata = DIRECTORIO_DESTINO.resolve(nombreConSufijo);
            contador++;
        }
        return rutaCandidata;
    }

    private String extraerNombreBase(String nombreArchivoCompleto) {
        int ultimoPunto = nombreArchivoCompleto.lastIndexOf('.');
        if (ultimoPunto <= 0) {
            return nombreArchivoCompleto;
        }
        return nombreArchivoCompleto.substring(0, ultimoPunto);
    }

    private String extraerExtension(String nombreArchivoCompleto) {
        int ultimoPunto = nombreArchivoCompleto.lastIndexOf('.');
        if (ultimoPunto <= 0 || ultimoPunto == nombreArchivoCompleto.length() - 1) {
            return "";
        }
        return nombreArchivoCompleto.substring(ultimoPunto + 1).toLowerCase(Locale.ROOT);
    }

    private byte[] obtenerContenido(PayloadEnviarArchivo payload) {
        String contenido = payload.getContenido();
        try {
            return Base64.getDecoder().decode(contenido);
        } catch (IllegalArgumentException e) {
            return contenido.getBytes(StandardCharsets.UTF_8);
        }
    }

    private Respuesta<String> crearRespuestaExitosa(String nombreArchivo, Path rutaArchivo) {
        Mensaje<String> mensajeRespuesta = new Mensaje<>();
        mensajeRespuesta.setTipo(TipoMensaje.RESPONSE);
        mensajeRespuesta.setAccion(Accion.ENVIAR_DOCUMENTO);
        mensajeRespuesta.setMetadata(crearMetadataRespuesta());
        mensajeRespuesta.setPayload("Archivo recibido: " + nombreArchivo + " en " + rutaArchivo.toAbsolutePath());

        Respuesta<String> respuesta = new Respuesta<>();
        respuesta.setEstado(Estado.EXITO);
        respuesta.setMensaje(mensajeRespuesta);
        return respuesta;
    }

    private Metadata crearMetadataRespuesta() {
        Metadata metadata = new Metadata();
        metadata.setIdMensaje(UUID.randomUUID().toString());
        metadata.setTimestamp(LocalDateTime.now());
        return metadata;
    }

    private String resolverRemitente(Mensaje<PayloadEnviarArchivo> mensaje) {
        if (mensaje.getMetadata() != null && mensaje.getMetadata().getClientId() != null) {
            return mensaje.getMetadata().getClientId();
        }
        return "desconocido";
    }

    private String resolverMensajeId(Mensaje<PayloadEnviarArchivo> mensaje) {
        if (mensaje.getMetadata() != null && mensaje.getMetadata().getIdMensaje() != null) {
            String idMensaje = mensaje.getMetadata().getIdMensaje();
            try {
                return UUID.fromString(idMensaje).toString();
            } catch (IllegalArgumentException ignored) {
                LOGGER.warning(() -> "idMensaje recibido no es UUID valido, se generara uno nuevo");
            }
        }
        return UUID.randomUUID().toString();
    }

    private LocalDateTime resolverFecha(Mensaje<PayloadEnviarArchivo> mensaje) {
        if (mensaje.getMetadata() != null && mensaje.getMetadata().getTimestamp() != null) {
            return mensaje.getMetadata().getTimestamp();
        }
        return LocalDateTime.now();
    }

    private int puertoRemitente() {
        Integer puerto = ContextoSolicitud.obtenerPuertoRemitente();
        return puerto == null ? -1 : puerto;
    }

    private Respuesta<?> crearErrorSesion(String codigo, String detalle) {
        Respuesta<?> respuesta = new Respuesta<>();
        respuesta.setEstado(Estado.ERROR);
        respuesta.setError(new ErrorDetalle(codigo, detalle));
        return respuesta;
    }

    private String encontrarPeerDeCliente(String username, GestorServidoresPeer gestorPeers) {
        List<PayloadClienteRemoto> remotos = gestorPeers.obtenerTodosClientesRemotos();
        for (PayloadClienteRemoto cliente : remotos) {
            if (username.equalsIgnoreCase(cliente.getUsername())) {
                return cliente.getServidorOrigen();
            }
        }
        return null;
    }
}
