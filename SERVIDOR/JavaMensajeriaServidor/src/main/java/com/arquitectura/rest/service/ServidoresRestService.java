package com.arquitectura.rest.service;

import com.arquitectura.aplicacion.sesion.GestorServidoresPeer;
import com.arquitectura.dominio.repositorios.PeerConocidoRepository;
import com.arquitectura.rest.dto.ServidorDTO;
import com.arquitectura.rest.mapper.ServidorRestMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ServidoresRestService {

    private final PeerConocidoRepository peerRepository;
    private final GestorServidoresPeer gestorServidoresPeer;
    private final ServidorRestMapper servidorMapper;

    public ServidoresRestService(PeerConocidoRepository peerRepository,
                                 GestorServidoresPeer gestorServidoresPeer,
                                 ServidorRestMapper servidorMapper) {
        this.peerRepository = peerRepository;
        this.gestorServidoresPeer = gestorServidoresPeer;
        this.servidorMapper = servidorMapper;
    }

    public List<ServidorDTO> listarDisponibles() {
        Map<String, ServidorDTO> servidores = new LinkedHashMap<>();

        peerRepository.listarTodos().stream()
                .map(servidorMapper::toDto)
                .forEach(servidor -> servidores.put(servidor.servidorId(), servidor));

        gestorServidoresPeer.obtenerPeers().stream()
                .map(servidorMapper::toDto)
                .forEach(servidor -> servidores.put(servidor.servidorId(), servidor));

        return servidores.values().stream().toList();
    }
}
