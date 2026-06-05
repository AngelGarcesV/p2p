package com.arquitectura.rest.dto;

import java.time.LocalDateTime;

/**
 * Contrato JSON para un log del servidor.
 */
public record LogServidorDTO(
        Long id,
        String nivel,
        String mensaje,
        String origen,
        String ipRemitente,
        LocalDateTime fechaEvento
) {}
