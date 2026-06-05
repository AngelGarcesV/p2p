package com.arquitectura.rest.dto;

import java.time.LocalDateTime;

/**
 * Contrato JSON para un archivo recibido.
 * Omite contenidoCifrado para no exponer datos sensibles.
 */
public record ArchivoResumenDTO(
        String id,
        String remitente,
        String ipRemitente,
        String nombreArchivo,
        String extension,
        String rutaArchivo,
        String hashSha256,
        long tamano,
        LocalDateTime fechaRecepcion,
        String servidorOrigen,
        String destinatario
) {}
