package com.arquitectura.rest.service;

import com.arquitectura.aplicacion.sesion.GestorSesiones;
import com.arquitectura.rest.dto.ClienteConectadoDTO;
import com.arquitectura.rest.mapper.ClienteRestMapper;

import java.util.List;

public class ClientesRestService {

    private final GestorSesiones gestorSesiones;
    private final ClienteRestMapper clienteMapper;

    public ClientesRestService(GestorSesiones gestorSesiones, ClienteRestMapper clienteMapper) {
        this.gestorSesiones = gestorSesiones;
        this.clienteMapper = clienteMapper;
    }

    public List<ClienteConectadoDTO> listarConectados() {
        return gestorSesiones.listarSesiones().stream()
                .map(clienteMapper::toDto)
                .toList();
    }
}
