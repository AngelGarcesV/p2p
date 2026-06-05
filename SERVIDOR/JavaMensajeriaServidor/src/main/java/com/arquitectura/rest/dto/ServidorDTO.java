package com.arquitectura.rest.dto;

import java.time.LocalDateTime;

/**
 * Contrato JSON para un servidor peer conocido/disponible.
 */
public record ServidorDTO(
        String servidorId,
        String host,
        int puerto,
        String estado,
        int intentosReconexion,
        LocalDateTime ultimaConexion
) {}
