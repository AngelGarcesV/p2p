package com.arquitectura.rest.dto;

import java.time.Instant;

/**
 * Contrato JSON para una sesion TCP activa.
 * Campos mapeados desde SesionCliente.
 */
public record ClienteConectadoDTO(
        String username,
        String ip,
        int puerto,
        String protocolo,
        Instant creadoEn,
        Instant ultimoAcceso
) {}
