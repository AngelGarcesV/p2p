package com.arquitectura.rest.service;

import com.arquitectura.dominio.repositorios.ArchivoRecibidoRepository;
import com.arquitectura.rest.dto.ArchivoResumenDTO;
import com.arquitectura.rest.mapper.ArchivoRestMapper;

import java.util.List;

public class ArchivosRestService {

    private final ArchivoRecibidoRepository archivoRepository;
    private final ArchivoRestMapper archivoMapper;

    public ArchivosRestService(ArchivoRecibidoRepository archivoRepository, ArchivoRestMapper archivoMapper) {
        this.archivoRepository = archivoRepository;
        this.archivoMapper = archivoMapper;
    }

    public List<ArchivoResumenDTO> listarDisponibles(String username) {
        boolean sinFiltro = username == null || username.isBlank();
        return (sinFiltro ? archivoRepository.listarTodos() : archivoRepository.listarParaUsuario(username.trim()))
                .stream()
                .map(archivoMapper::toDto)
                .toList();
    }
}
