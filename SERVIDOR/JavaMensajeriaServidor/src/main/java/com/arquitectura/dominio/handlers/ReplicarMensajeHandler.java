package com.arquitectura.dominio.handlers;

import com.arquitectura.aplicacion.router.Handler;
import com.arquitectura.aplicacion.sesion.GestorServidoresPeer;
import com.arquitectura.dominio.repositorios.JpaMensajeRepository;
import com.arquitectura.dominio.repositorios.MensajeRepository;
import com.arquitectura.mensajeria.Mensaje;
import com.arquitectura.mensajeria.Metadata;
import com.arquitectura.mensajeria.Respuesta;
import com.arquitectura.mensajeria.enums.Accion;
import com.arquitectura.mensajeria.enums.Estado;
import com.arquitectura.mensajeria.enums.TipoMensaje;
import com.arquitectura.mensajeria.payload.PayloadReplicarMensaje;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.logging.Logger;

public class ReplicarMensajeHandler implements Handler<PayloadReplicarMensaje> {

    private static final Logger LOGGER = Logger.getLogger(ReplicarMensajeHandler.class.getName());
    private final MensajeRepository mensajeRepository = new JpaMensajeRepository();

    @Override
    public Respuesta<?> handle(Mensaje<PayloadReplicarMensaje> mensaje) {
        PayloadReplicarMensaje payload = mensaje.getPayload();
        if (payload == null || payload.getId() == null || payload.getId().isBlank()) {
            Respuesta<?> error = new Respuesta<>();
            error.setEstado(Estado.ERROR);
            return error;
        }

        // Idempotencia: si ya existe, ignorar silenciosamente
        if (mensajeRepository.existePorId(payload.getId())) {
            LOGGER.fine(() -> "Mensaje replicado ya existente, ignorando: " + payload.getId());
            return crearRespuestaExito("Mensaje ya existe (idempotente): " + payload.getId());
        }

        LocalDateTime timestamp = payload.getTimestamp() != null ? payload.getTimestamp() : LocalDateTime.now();
        String servidorOrigen = payload.getServidorOrigen();

        // Persistir sin hash/cifrado ya que viene de un peer (datos de texto plano)
        mensajeRepository.guardar(
                payload.getId(),
                payload.getAutor(),
                servidorOrigen != null ? servidorOrigen : "peer-desconocido",
                payload.getContenido(),
                "",   // hash no recalculado en replicacion
                "",   // contenido cifrado no reenviado en replicacion de texto
                timestamp,
                servidorOrigen,
                payload.getDestinatario()  // null = broadcast, username = unicast
        );

        LOGGER.info(() -> "Mensaje replicado persistido: id=" + payload.getId()
                + " | autor=" + payload.getAutor()
                + " | origen=" + servidorOrigen);

        // Propagacion a peers adicionales (fan-out, idempotente por existePorId)
        GestorServidoresPeer gestorPeers = GestorServidoresPeer.getInstance();
        if (!gestorPeers.obtenerPeersConectados().isEmpty()) {
            Mensaje<PayloadReplicarMensaje> msgReplica = construirMensajeReplica(payload);
            gestorPeers.enviarATodos(msgReplica);
            LOGGER.info(() -> "Mensaje replicado propagado a peers adicionales: id=" + payload.getId());
        }

        return crearRespuestaExito("Mensaje replicado: " + payload.getId());
    }

    @Override
    public Class<PayloadReplicarMensaje> getPayloadClass() {
        return PayloadReplicarMensaje.class;
    }

    private Mensaje<PayloadReplicarMensaje> construirMensajeReplica(PayloadReplicarMensaje payload) {
        Mensaje<PayloadReplicarMensaje> msg = new Mensaje<>();
        msg.setTipo(TipoMensaje.REQUEST);
        msg.setAccion(Accion.REPLICAR_MENSAJE);
        msg.setMetadata(crearMetadata());
        msg.setPayload(payload);
        return msg;
    }

    private Respuesta<String> crearRespuestaExito(String texto) {
        Mensaje<String> mensajeRespuesta = new Mensaje<>();
        mensajeRespuesta.setTipo(TipoMensaje.RESPONSE);
        mensajeRespuesta.setAccion(Accion.REPLICAR_MENSAJE);
        mensajeRespuesta.setMetadata(crearMetadata());
        mensajeRespuesta.setPayload(texto);

        Respuesta<String> respuesta = new Respuesta<>();
        respuesta.setEstado(Estado.EXITO);
        respuesta.setMensaje(mensajeRespuesta);
        return respuesta;
    }

    private Metadata crearMetadata() {
        Metadata metadata = new Metadata();
        metadata.setIdMensaje(UUID.randomUUID().toString());
        metadata.setTimestamp(LocalDateTime.now());
        return metadata;
    }
}
