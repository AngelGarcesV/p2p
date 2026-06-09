package com.arquitectura.rest.service;

import com.arquitectura.dominio.repositorios.LogServidorRepository;
import com.arquitectura.rest.dto.LogServidorDTO;
import com.arquitectura.rest.dto.PaginaDTO;
import com.arquitectura.rest.mapper.LogServidorRestMapper;

public class LogsRestService {

    private final LogServidorRepository logRepository;
    private final LogServidorRestMapper logMapper;

    public LogsRestService(LogServidorRepository logRepository, LogServidorRestMapper logMapper) {
        this.logRepository = logRepository;
        this.logMapper = logMapper;
    }

    public PaginaDTO<LogServidorDTO> listarPaginado(int pagina, int tamanoPagina) {
        int paginaNormalizada = Math.max(pagina, 0);
        int tamanoNormalizado = Math.max(tamanoPagina, 1);
        var datos = logRepository.listarPaginado(paginaNormalizada, tamanoNormalizado).stream()
                .map(logMapper::toDto)
                .toList();

        return new PaginaDTO<>(
                datos,
                logRepository.contarTotal(),
                paginaNormalizada,
                tamanoNormalizado
        );
    }
}
