package com.arquitectura.rest.dto;

import java.util.List;

/**
 * Respuesta paginada generica para endpoints REST de consulta.
 */
public record PaginaDTO<T>(
        List<T> datos,
        long total,
        int pagina,
        int tamanoPagina
) {}
