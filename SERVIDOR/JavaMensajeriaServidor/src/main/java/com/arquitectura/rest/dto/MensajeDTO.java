package com.arquitectura.rest.dto;

import java.time.LocalDateTime;

/**
 * Contrato JSON para un mensaje disponible.
 * Omite contenidoCifrado para no exponer datos sensibles por REST.
 */
public record MensajeDTO(
        String id,
        String autor,
        String ipRemitente,
        String contenido,
        String hashSha256,
        LocalDateTime fechaEnvio,
        String servidorOrigen,
        String destinatario
) {}
