package com.arquitectura.rest.service;

import com.arquitectura.dominio.repositorios.MensajeRepository;
import com.arquitectura.rest.dto.MensajeDTO;
import com.arquitectura.rest.mapper.MensajeRestMapper;

import java.util.List;

public class MensajesRestService {

    private final MensajeRepository mensajeRepository;
    private final MensajeRestMapper mensajeMapper;

    public MensajesRestService(MensajeRepository mensajeRepository, MensajeRestMapper mensajeMapper) {
        this.mensajeRepository = mensajeRepository;
        this.mensajeMapper = mensajeMapper;
    }

    public List<MensajeDTO> listarDisponibles(String username) {
        boolean sinFiltro = username == null || username.isBlank();
        return (sinFiltro ? mensajeRepository.listarTodos() : mensajeRepository.listarParaUsuario(username.trim()))
                .stream()
                .map(mensajeMapper::toDto)
                .toList();
    }
}
